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
            // org.freedesktop.gstreamer.GStreamer.init(context);
    Log.w(TAG, "GStreamer initialization disabled - using ALAC decoder instead");
            
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
     * 推送音频数据 (MainActivity 调用的方法)
     */
    public void pushAudioData(byte[] audioData) {
        if (audioData != null && audioData.length > 0) {
            PCMPacket packet = new PCMPacket();
            packet.data = audioData;
            addPCMPacket(packet);
        }
    }
    
    /**
     * 启动播放器
     */
    public void startPlayer() {
        if (!isAlive()) {
            super.start();
        }
    }
    
    /**
     * 停止播放器 (MainActivity 调用的方法)
     */
    public void stopAudio() {
        stopPlayer();
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