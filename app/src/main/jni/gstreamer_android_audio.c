#include <string.h>
#include <jni.h>
#include <android/log.h>
#include <gst/gst.h>
#include <gst/app/gstappsrc.h>
#include <pthread.h>

#define LOG_TAG "GStreamerAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef struct _CustomData {
    GstElement *pipeline;
    GstElement *appsrc;
    GMainLoop *main_loop;
    GMainContext *context;
    gboolean initialized;
    pthread_t gst_thread;
    jobject java_object;
} CustomData;

static JavaVM *java_vm;
static jfieldID custom_data_field_id;

// 线程函数
static void* gst_thread_func(void *userdata) {
    CustomData *data = (CustomData *)userdata;
    
    // 创建 GLib 主循环
    data->context = g_main_context_new();
    g_main_context_push_thread_default(data->context);
    
    data->main_loop = g_main_loop_new(data->context, FALSE);
    
    LOGI("GStreamer thread started");
    g_main_loop_run(data->main_loop);
    LOGI("GStreamer thread stopped");
    
    g_main_context_pop_thread_default(data->context);
    g_main_context_unref(data->context);
    
    return NULL;
}

// 初始化 GStreamer
JNIEXPORT void JNICALL
Java_com_dragonwarrior_airplayserver_player_GstAudioPlayer_nativeInit(JNIEnv *env, jobject thiz) {
    CustomData *data = g_new0(CustomData, 1);
    
    // 初始化 GStreamer
    gst_init(NULL, NULL);
    
    // 创建管道
    GError *error = NULL;
    data->pipeline = gst_parse_launch(
        "appsrc name=audio-src caps=\"audio/mpeg,mpegversion=(int)4,channels=(int)2,rate=(int)44100,stream-format=raw,codec_data=(buffer)f8e85000\" ! "
        "avdec_aac ! audioconvert ! audioresample ! autoaudiosink sync=false",
        &error
    );
    
    if (error) {
        LOGE("Failed to create pipeline: %s", error->message);
        g_error_free(error);
        g_free(data);
        return;
    }
    
    // 获取 appsrc 元素
    data->appsrc = gst_bin_get_by_name(GST_BIN(data->pipeline), "audio-src");
    if (!data->appsrc) {
        LOGE("Failed to get appsrc element");
        gst_object_unref(data->pipeline);
        g_free(data);
        return;
    }
    
    // 配置 appsrc
    g_object_set(data->appsrc,
        "stream-type", 0, // GST_APP_STREAM_TYPE_STREAM
        "is-live", TRUE,
        "format", GST_FORMAT_TIME,
        NULL
    );
    
    // 保存 Java 对象引用
    data->java_object = (*env)->NewGlobalRef(env, thiz);
    
    // 保存数据指针到 Java 对象
    (*env)->SetLongField(env, thiz, custom_data_field_id, (jlong)data);
    
    // 创建 GStreamer 线程
    pthread_create(&data->gst_thread, NULL, gst_thread_func, data);
    
    // 启动管道
    gst_element_set_state(data->pipeline, GST_STATE_PLAYING);
    
    data->initialized = TRUE;
    LOGI("GStreamer initialized successfully");
}

// 推送音频数据
JNIEXPORT void JNICALL
Java_com_dragonwarrior_airplayserver_player_GstAudioPlayer_nativePushBuffer(JNIEnv *env, jobject thiz, jbyteArray buffer) {
    CustomData *data = (CustomData *)(*env)->GetLongField(env, thiz, custom_data_field_id);
    
    if (!data || !data->initialized || !data->appsrc) {
        LOGE("GStreamer not initialized or appsrc not available");
        return;
    }
    
    jsize buffer_size = (*env)->GetArrayLength(env, buffer);
    jbyte *buffer_data = (*env)->GetByteArrayElements(env, buffer, NULL);
    
    // 创建 GStreamer buffer
    GstBuffer *gst_buffer = gst_buffer_new_allocate(NULL, buffer_size, NULL);
    GstMapInfo map;
    gst_buffer_map(gst_buffer, &map, GST_MAP_WRITE);
    memcpy(map.data, buffer_data, buffer_size);
    gst_buffer_unmap(gst_buffer, &map);
    
    // 推送到 appsrc
    GstFlowReturn ret = gst_app_src_push_buffer(GST_APP_SRC(data->appsrc), gst_buffer);
    
    (*env)->ReleaseByteArrayElements(env, buffer, buffer_data, JNI_ABORT);
    
    if (ret != GST_FLOW_OK) {
        LOGE("Failed to push buffer: %d", ret);
    }
}

// 停止 GStreamer
JNIEXPORT void JNICALL
Java_com_dragonwarrior_airplayserver_player_GstAudioPlayer_nativeStop(JNIEnv *env, jobject thiz) {
    CustomData *data = (CustomData *)(*env)->GetLongField(env, thiz, custom_data_field_id);
    
    if (!data) return;
    
    // 停止管道
    if (data->pipeline) {
        gst_element_set_state(data->pipeline, GST_STATE_NULL);
        gst_object_unref(data->pipeline);
    }
    
    // 停止主循环
    if (data->main_loop) {
        g_main_loop_quit(data->main_loop);
        pthread_join(data->gst_thread, NULL);
        g_main_loop_unref(data->main_loop);
    }
    
    // 清理资源
    if (data->appsrc) {
        gst_object_unref(data->appsrc);
    }
    
    if (data->java_object) {
        (*env)->DeleteGlobalRef(env, data->java_object);
    }
    
    g_free(data);
    (*env)->SetLongField(env, thiz, custom_data_field_id, 0);
    
    LOGI("GStreamer stopped and cleaned up");
}

// JNI 方法表
static JNINativeMethod native_methods[] = {
    {"nativeInit", "()V", (void *) Java_com_dragonwarrior_airplayserver_player_GstAudioPlayer_nativeInit},
    {"nativePushBuffer", "([B)V", (void *) Java_com_dragonwarrior_airplayserver_player_GstAudioPlayer_nativePushBuffer},
    {"nativeStop", "()V", (void *) Java_com_dragonwarrior_airplayserver_player_GstAudioPlayer_nativeStop}
};

// 库初始化
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    java_vm = vm;
    
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("Could not retrieve JNIEnv");
        return JNI_ERR;
    }
    
    jclass klass = (*env)->FindClass(env, "com/dragonwarrior/airplayserver/player/GstAudioPlayer");
    if (!klass) {
        LOGE("Could not find GstAudioPlayer class");
        return JNI_ERR;
    }
    
    // 注册本地方法
    if ((*env)->RegisterNatives(env, klass, native_methods, 
                               sizeof(native_methods) / sizeof(native_methods[0])) < 0) {
        LOGE("Failed to register native methods");
        return JNI_ERR;
    }
    
    // 获取字段 ID
    custom_data_field_id = (*env)->GetFieldID(env, klass, "nativeCustomData", "J");
    if (!custom_data_field_id) {
        LOGE("Could not find nativeCustomData field");
        return JNI_ERR;
    }
    
    LOGI("JNI_OnLoad completed successfully");
    return JNI_VERSION_1_6;
} 