LOCAL_PATH := $(call my-dir)

ifndef GSTREAMER_ROOT
ifndef GSTREAMER_ROOT_ANDROID
$(error GSTREAMER_ROOT_ANDROID is not defined!)
endif
GSTREAMER_ROOT        := $(GSTREAMER_ROOT_ANDROID)
endif

# 根据目标架构选择正确的 GStreamer 路径
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    GSTREAMER_NDK_BUILD_PATH := $(GSTREAMER_ROOT)/armv7/share/gst-android/ndk-build/
else ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
    GSTREAMER_NDK_BUILD_PATH := $(GSTREAMER_ROOT)/arm64/share/gst-android/ndk-build/
else ifeq ($(TARGET_ARCH_ABI),x86)
    GSTREAMER_NDK_BUILD_PATH := $(GSTREAMER_ROOT)/x86/share/gst-android/ndk-build/
else ifeq ($(TARGET_ARCH_ABI),x86_64)
    GSTREAMER_NDK_BUILD_PATH := $(GSTREAMER_ROOT)/x86_64/share/gst-android/ndk-build/
else
    $(error Unsupported architecture: $(TARGET_ARCH_ABI))
endif

GSTREAMER_PLUGINS         := coreelements audioconvert audioresample autodetect opensles
G_IO_MODULES              := 
GSTREAMER_EXTRA_DEPS      := gstreamer-audio-1.0

include $(GSTREAMER_NDK_BUILD_PATH)/gstreamer-1.0.mk

# 现在定义我们的模块，它依赖于 GStreamer 构建系统生成的模块
include $(CLEAR_VARS)

LOCAL_MODULE    := gstreamer_android_audio
LOCAL_SRC_FILES := gstreamer_android_audio.c
LOCAL_SHARED_LIBRARIES := gstreamer_android
LOCAL_LDLIBS := -llog -landroid

include $(BUILD_SHARED_LIBRARY) 