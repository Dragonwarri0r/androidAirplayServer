# GStreamer 集成指南：解决 AirPlay AAC ELD 音频问题

## 问题背景

当前 Android 项目中的 AirPlay 服务器无法正确播放音频，根本原因是：
- AirPlay 使用 AAC ELD (Enhanced Low Delay) 格式
- Android MediaCodec 不支持 AirPlay 特定的 AAC ELD 实现
- 需要专门的解码器来处理这种格式

## 解决方案：GStreamer

参考项目中的 `GstPlayer.java` 显示，GStreamer 可以完美解决这个问题：

### 1. 关键优势

1. **原生 AAC ELD 支持**：GStreamer 的 `avdec_aac` 解码器支持 AAC ELD
2. **正确的配置**：已验证的 `codec_data` 配置
3. **完整的音频链**：自动处理格式转换和音频输出
4. **跨平台**：在桌面版本中已经验证可行

### 2. 核心管道配置

```java
// AAC ELD 管道
aacEldPipeline = (Pipeline) Gst.parseLaunch(
    "appsrc name=aac-eld-src ! avdec_aac ! audioconvert ! audioresample ! autoaudiosink sync=false"
);

// 关键配置
aacEldSrc.setCaps(Caps.fromString(
    "audio/mpeg,mpegversion=(int)4,channels=(int)2,rate=(int)44100,stream-format=raw,codec_data=(buffer)f8e85000"
));
```

### 3. 管道组件说明

- `appsrc`：接收应用程序数据
- `avdec_aac`：FFmpeg 的 AAC 解码器，支持 ELD
- `audioconvert`：音频格式转换
- `audioresample`：采样率转换
- `autoaudiosink`：自动选择音频输出设备

## Android 集成步骤

### 第一步：下载和配置 GStreamer Android

1. **下载 GStreamer Android 预编译包**：
   ```bash
   # 从 https://gstreamer.freedesktop.org/download/ 下载
   # GStreamer Android universal binaries (例如 gstreamer-1.0-android-universal-1.18.0.tar.xz)
   ```

2. **解压并设置环境变量**：
   ```bash
   # 解压到任意目录，例如 /opt/gstreamer-android
   export GSTREAMER_ROOT_ANDROID=/opt/gstreamer-android
   
   # 添加到 ~/.bashrc 或 ~/.zshrc 中
   echo 'export GSTREAMER_ROOT_ANDROID=/opt/gstreamer-android' >> ~/.bashrc
   ```

3. **验证安装**：
   ```bash
   ls $GSTREAMER_ROOT_ANDROID/share/gst-android/ndk-build/
   # 应该看到 gstreamer.mk 和 plugins.mk 文件
   ```

### 第二步：修改 Android 项目配置

1. **更新 `app/build.gradle`**：
   ```gradle
   android {
       compileSdkVersion 30
       buildToolsVersion "30.0.3"
       
       defaultConfig {
           applicationId "com.dragonwarrior.airplayserver"
           minSdkVersion 21  // GStreamer 需要 API 21+
           targetSdkVersion 30
           
           externalNativeBuild {
               ndkBuild {
                   arguments "APP_PLATFORM=android-21"
                   abiFilters "armeabi-v7a", "arm64-v8a", "x86", "x86_64"
               }
           }
       }
       
       externalNativeBuild {
           ndkBuild {
               path "src/main/jni/Android.mk"
           }
       }
   }
   
   dependencies {
       // 其他依赖...
   }
   ```

2. **创建 `app/src/main/jni/Android.mk`**：
   ```makefile
   LOCAL_PATH := $(call my-dir)
   
   include $(CLEAR_VARS)
   
   LOCAL_MODULE    := gstreamer_android_audio
   LOCAL_SRC_FILES := gstreamer_android_audio.c
   LOCAL_SHARED_LIBRARIES := gstreamer_android
   LOCAL_LDLIBS := -llog -landroid
   
   include $(BUILD_SHARED_LIBRARY)
   
   ifndef GSTREAMER_ROOT
   ifndef GSTREAMER_ROOT_ANDROID
   $(error GSTREAMER_ROOT_ANDROID is not defined!)
   endif
   GSTREAMER_ROOT := $(GSTREAMER_ROOT_ANDROID)
   endif
   
   GSTREAMER_NDK_BUILD_PATH := $(GSTREAMER_ROOT)/share/gst-android/ndk-build/
   include $(GSTREAMER_NDK_BUILD_PATH)/plugins.mk
   
   # 音频相关插件
   GSTREAMER_PLUGINS := $(GSTREAMER_PLUGINS_CORE) $(GSTREAMER_PLUGINS_CODECS) $(GSTREAMER_PLUGINS_SYS)
   GSTREAMER_EXTRA_DEPS := gstreamer-audio-1.0
   
   include $(GSTREAMER_NDK_BUILD_PATH)/gstreamer.mk
   ```

3. **创建 `app/src/main/jni/Application.mk`**：
   ```makefile
   APP_ABI := armeabi-v7a arm64-v8a x86 x86_64
   APP_PLATFORM := android-21
   APP_STL := c++_shared
   ```

### 第三步：实现 JNI 接口

创建 `app/src/main/jni/gstreamer_android_audio.c`：

```c
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
```

### 第四步：更新 Java 音频播放器

更新 `GstAudioPlayer.java`：

```java
package com.dragonwarrior.airplayserver.player;

import android.content.Context;
import android.util.Log;
import com.dragonwarrior.airplayserver.model.PCMPacket;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 基于 GStreamer 的音频播放器
 * 专门用于处理 AirPlay AAC ELD 音频格式
 */
public class GstAudioPlayer extends Thread {
    private static final String TAG = "GstAudioPlayer";
    
    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("gstreamer_android_audio");
    }
    
    private final Context context;
    private BlockingQueue<PCMPacket> packets = new LinkedBlockingQueue<>(500);
    private volatile boolean isStopThread = false;
    private volatile boolean isInitialized = false;
    
    // JNI 数据指针
    private long nativeCustomData;
    
    // JNI 方法
    private native void nativeInit();
    private native void nativePushBuffer(byte[] buffer);
    private native void nativeStop();
    
    public GstAudioPlayer(Context context) {
        this.context = context;
        initializeGStreamer();
    }
    
    /**
     * 初始化 GStreamer
     */
    private void initializeGStreamer() {
        try {
            // 初始化 GStreamer Android
            org.freedesktop.gstreamer.GStreamer.init(context);
            
            // 初始化本地代码
            nativeInit();
            
            Log.d(TAG, "GStreamer initialized successfully");
            isInitialized = true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize GStreamer", e);
            isInitialized = false;
        }
    }
    
    /**
     * 设置音频格式
     */
    public void setAudioFormat(String mimeType, int sampleRate, int channelCount) {
        Log.d(TAG, "Audio format: " + mimeType + ", " + sampleRate + "Hz, " + channelCount + " channels");
        // GStreamer 管道已经预配置为 AAC ELD，无需动态更改
    }
    
    /**
     * 添加音频数据包
     */
    public void addPCMPacket(PCMPacket pcmPacket) {
        if (!isInitialized) {
            Log.w(TAG, "GStreamer not initialized, dropping packet");
            return;
        }
        
        try {
            packets.put(pcmPacket);
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to add PCM packet", e);
        }
    }
    
    /**
     * 播放音频数据
     */
    private void playAudioData(byte[] audioData) {
        if (!isInitialized || audioData == null || audioData.length == 0) {
            return;
        }
        
        try {
            // 推送数据到 GStreamer
            nativePushBuffer(audioData);
            Log.v(TAG, "Pushed audio data to GStreamer: " + audioData.length + " bytes");
        } catch (Exception e) {
            Log.e(TAG, "Error playing audio data", e);
        }
    }
    
    @Override
    public void run() {
        Log.d(TAG, "GstAudioPlayer thread started");
        
        while (!isStopThread) {
            try {
                PCMPacket pcmPacket = packets.take();
                if (pcmPacket != null && pcmPacket.data != null) {
                    playAudioData(pcmPacket.data);
                }
            } catch (InterruptedException e) {
                Log.d(TAG, "GstAudioPlayer thread interrupted");
                break;
            } catch (Exception e) {
                Log.e(TAG, "Error in GstAudioPlayer thread", e);
            }
        }
        
        Log.d(TAG, "GstAudioPlayer thread stopped");
    }
    
    /**
     * 停止播放器
     */
    public void stopPlayer() {
        isStopThread = true;
        
        try {
            // 停止 GStreamer
            nativeStop();
            Log.d(TAG, "GStreamer stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping GStreamer", e);
        }
        
        // 清空队列
        packets.clear();
        
        // 中断线程
        interrupt();
    }
    
    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return isInitialized;
    }
}
```

### 第五步：在 MainActivity 中集成

```java
public class MainActivity extends AppCompatActivity {
    private GstAudioPlayer gstAudioPlayer;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 初始化 GStreamer 播放器
        gstAudioPlayer = new GstAudioPlayer(this);
        gstAudioPlayer.start();
        
        // 在 AirPlay 服务器配置中使用 GStreamer 播放器
        // 替换原来的 AudioPlayer
        AirPlayServer airPlayServer = new AirPlayServer(new AirplayDataConsumer() {
            @Override
            public void onAudio(byte[] audioData) {
                // 创建 PCMPacket 并添加到 GStreamer 播放器
                PCMPacket packet = new PCMPacket();
                packet.data = audioData;
                gstAudioPlayer.addPCMPacket(packet);
            }
            
            // 其他方法...
        });
        
        // 启动 AirPlay 服务器
        airPlayServer.start();
    }
    
    @Override
    protected void onDestroy() {
        if (gstAudioPlayer != null) {
            gstAudioPlayer.stopPlayer();
        }
        super.onDestroy();
    }
}
```

## 编译和测试

### 1. 编译项目
```bash
cd /path/to/your/android/project
./gradlew assembleDebug
```

### 2. 安装到设备
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. 查看日志
```bash
adb logcat | grep -E "(GStreamerAudio|GstAudioPlayer)"
```

## 故障排除

### 1. 常见问题

**问题 1**: `GSTREAMER_ROOT_ANDROID` 未定义
```bash
# 解决方案：设置环境变量
export GSTREAMER_ROOT_ANDROID=/path/to/gstreamer-android
```

**问题 2**: 找不到 GStreamer 库
```bash
# 解决方案：检查库文件是否存在
ls $GSTREAMER_ROOT_ANDROID/lib/
```

**问题 3**: 管道创建失败
```bash
# 解决方案：检查插件是否包含
# 在 Android.mk 中确保包含了必要的插件
```

### 2. 调试技巧

1. **启用 GStreamer 调试**：
   ```c
   // 在 C 代码中添加
   gst_debug_set_threshold_for_name("*", GST_LEVEL_DEBUG);
   ```

2. **检查管道状态**：
   ```c
   GstBus *bus = gst_element_get_bus(pipeline);
   // 监听消息
   ```

3. **验证音频数据**：
   ```java
   Log.d(TAG, "Audio data size: " + audioData.length);
   ```

## 总结

使用 GStreamer 是解决 AirPlay AAC ELD 音频问题的最佳方案：

1. **技术优势**：原生支持 AAC ELD，参考项目验证可行
2. **实现复杂度**：需要集成 JNI 和 GStreamer Android，但有官方支持
3. **性能表现**：硬件加速解码，低延迟
4. **维护成本**：稳定的开源项目，社区支持良好

通过这个集成，你的 Android AirPlay 服务器将能够正确播放来自 iOS 设备的音频流。

## 下一步

1. 实现上述集成步骤
2. 测试 AAC ELD 音频播放
3. 根据需要调整 GStreamer 管道配置
4. 优化性能和错误处理 