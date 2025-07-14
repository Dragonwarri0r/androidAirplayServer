package com.dragonwarrior.airplayserver.player;

import android.content.Context;
import android.util.Log;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 基于ExoPlayer的音频播放器，专门用于处理AirPlay AAC ELD音频
 */
public class ExoAudioPlayer extends Thread {
    private static final String TAG = "ExoAudioPlayer";
    
    private final Context context;
    private ExoPlayer exoPlayer;
    private BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>();
    private volatile boolean isRunning = false;
    private volatile boolean isInitialized = false;
    
    // 音频格式参数
    private int sampleRate = 44100;
    private int channelCount = 2;
    private String mimeType = MimeTypes.AUDIO_AAC;
    
    public ExoAudioPlayer(Context context) {
        this.context = context;
        initializePlayer();
    }
    
    private void initializePlayer() {
        try {
            exoPlayer = new ExoPlayer.Builder(context).build();
            Log.d(TAG, "ExoPlayer initialized successfully");
            isInitialized = true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize ExoPlayer", e);
            isInitialized = false;
        }
    }
    
    /**
     * 设置音频格式
     */
    public void setAudioFormat(String mimeType, int sampleRate, int channelCount) {
        this.mimeType = mimeType;
        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
        
        Log.d(TAG, "Audio format set: " + mimeType + ", " + sampleRate + "Hz, " + channelCount + " channels");
        
        // 对于AAC ELD，我们需要特殊处理
        if (MimeTypes.AUDIO_AAC.equals(mimeType)) {
            Log.d(TAG, "Detected AAC format, will attempt to play directly");
        }
    }
    
    public void addAudioData(byte[] audioData) {
        if (isRunning && audioData != null && audioData.length > 0) {
            try {
                audioQueue.put(audioData);
                Log.v(TAG, "Added audio data: " + audioData.length + " bytes, queue size: " + audioQueue.size());
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to add audio data", e);
            }
        } else {
            Log.w(TAG, "Cannot add audio data - running: " + isRunning + ", data: " + (audioData != null ? audioData.length : "null"));
        }
    }
    
    public void startPlayback() {
        if (!isInitialized) {
            Log.e(TAG, "Cannot start playback - ExoPlayer not initialized");
            return;
        }
        
        if (!isRunning) {
            isRunning = true;
            start();
            Log.d(TAG, "Audio playback started");
        }
    }
    
    public void stopPlayback() {
        isRunning = false;
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.release();
            exoPlayer = null;
        }
        audioQueue.clear();
        Log.d(TAG, "Audio playback stopped");
    }
    
    @Override
    public void run() {
        Log.d(TAG, "ExoAudioPlayer thread started");
        
        while (isRunning) {
            try {
                byte[] audioData = audioQueue.take();
                
                if (audioData != null && audioData.length > 0) {
                    // 记录接收到的音频数据
                    Log.v(TAG, "Processing audio data: " + audioData.length + " bytes");
                    
                    // 输出前8字节用于调试
                    if (audioData.length >= 8) {
                        StringBuilder hex = new StringBuilder();
                        for (int i = 0; i < 8; i++) {
                            hex.append(String.format("%02X ", audioData[i]));
                        }
                        Log.v(TAG, "Audio data header: " + hex.toString());
                    }
                    
                    // 这里我们暂时只记录数据，不进行实际播放
                    // 因为ExoPlayer需要完整的媒体文件或流，而不是原始的AAC帧
                    Log.d(TAG, "Received AAC ELD data, size: " + audioData.length);
                }
                
            } catch (InterruptedException e) {
                Log.d(TAG, "Audio thread interrupted");
                break;
            } catch (Exception e) {
                Log.e(TAG, "Error processing audio data", e);
            }
        }
        
        Log.d(TAG, "ExoAudioPlayer thread stopped");
    }
    
    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return audioQueue.size();
    }
} 