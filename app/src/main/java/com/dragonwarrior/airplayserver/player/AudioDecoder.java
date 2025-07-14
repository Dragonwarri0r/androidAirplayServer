package com.dragonwarrior.airplayserver.player;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 音频解码器，用于解码ALAC/AAC格式的音频数据
 */
public class AudioDecoder {
    private static final String TAG = "AudioDecoder";
    
    private MediaCodec decoder;
    private MediaFormat format;
    private BlockingQueue<ByteBuffer> outputBuffers = new LinkedBlockingQueue<>();
    private volatile boolean isRunning = false;
    
    // 音频参数
    private int sampleRate = 44100;
    private int channelCount = 2;
    private String mimeType = "audio/alac"; // 默认ALAC
    
    public interface AudioDecoderCallback {
        void onPCMData(byte[] pcmData);
        void onError(Exception e);
    }
    
    public interface AudioFormatChangedCallback extends AudioDecoderCallback {
        void onFormatChanged(int sampleRate, int channelCount);
    }
    
    private AudioDecoderCallback callback;
    
    public AudioDecoder(AudioDecoderCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 设置音频格式
     */
    public void setAudioFormat(String mimeType, int sampleRate, int channelCount) {
        this.mimeType = mimeType;
        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
    }
    
    /**
     * 初始化解码器
     */
    public boolean initialize() {
        try {
            // 尝试创建解码器
            try {
                decoder = MediaCodec.createDecoderByType(mimeType);
                Log.d(TAG, "Created MediaCodec decoder for: " + mimeType);
            } catch (IOException e) {
                Log.w(TAG, "Failed to create decoder for " + mimeType + ", trying alternative", e);
                // 如果ALAC不支持，尝试AAC
                if ("audio/alac".equals(mimeType)) {
                    try {
                        decoder = MediaCodec.createDecoderByType("audio/mp4a-latm");
                        mimeType = "audio/mp4a-latm";
                        Log.d(TAG, "Fallback to AAC decoder");
                    } catch (IOException e2) {
                        Log.e(TAG, "Failed to create any audio decoder", e2);
                        if (callback != null) {
                            callback.onError(e2);
                        }
                        return false;
                    }
                } else {
                    Log.e(TAG, "Failed to create audio decoder", e);
                    if (callback != null) {
                        callback.onError(e);
                    }
                    return false;
                }
            }
            
            // 创建MediaFormat
            format = MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount);
            Log.d(TAG, "Created MediaFormat: " + format);
            
            // 根据MIME类型设置特定参数
            if ("audio/alac".equals(mimeType)) {
                // ALAC特定设置
                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192);
                Log.d(TAG, "Applied ALAC-specific settings");
            } else if ("audio/mp4a-latm".equals(mimeType)) {
                // 设置AAC profile (支持ELD)
                format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectELD);
                // 设置最大输入大小
                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192);
                // 设置是否为ADTS格式
                format.setInteger(MediaFormat.KEY_IS_ADTS, 0);
                Log.d(TAG, "Applied AAC-specific settings");
            }
            
            // 配置解码器
            decoder.configure(format, null, null, 0);
            decoder.start();
            
            isRunning = true;
            
            // 启动输出线程
            new Thread(this::processOutput, "AudioDecoder-Output").start();
            
            Log.d(TAG, "Audio decoder initialized: " + mimeType + ", " + sampleRate + "Hz, " + channelCount + " channels");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize audio decoder", e);
            if (callback != null) {
                callback.onError(e);
            }
            return false;
        }
    }
    
    /**
     * 解码音频数据
     */
    public void decode(byte[] audioData) {
        if (!isRunning || decoder == null || audioData == null || audioData.length == 0) {
            Log.w(TAG, "Cannot decode: running=" + isRunning + ", decoder=" + (decoder != null) + ", dataLength=" + (audioData != null ? audioData.length : 0));
            return;
        }
        
        Log.v(TAG, "=== DECODE ATTEMPT ===");
        Log.v(TAG, "Input data size: " + audioData.length + " bytes");
        
        // 输出前8字节用于调试
        if (audioData.length >= 8) {
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(8, audioData.length); i++) {
                hex.append(String.format("%02X ", audioData[i]));
            }
            Log.v(TAG, "Input data first 8 bytes: " + hex.toString());
        }
        
        try {
            int inputBufferIndex = decoder.dequeueInputBuffer(0); // 非阻塞，避免影响视频
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                if (inputBuffer != null) {
                    inputBuffer.clear();
                    inputBuffer.put(audioData);
                    decoder.queueInputBuffer(inputBufferIndex, 0, audioData.length, 0, 0);
                    Log.v(TAG, "Successfully queued input buffer: " + audioData.length + " bytes");
                } else {
                    Log.w(TAG, "Input buffer is null");
                }
            } else {
                // 输入缓冲区不可用，直接丢弃这帧数据，避免阻塞
                Log.v(TAG, "No input buffer available, dropping frame");
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "MediaCodec in illegal state during decode", e);
            stop();
        } catch (Exception e) {
            Log.e(TAG, "Error decoding audio", e);
            if (callback != null) {
                callback.onError(e);
            }
        }
        Log.v(TAG, "=== END DECODE ATTEMPT ===");
    }
    
    /**
     * 处理输出数据
     */
    private void processOutput() {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        
        Log.d(TAG, "Output processing thread started");
        
        while (isRunning && decoder != null) {
            try {
                int outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10); // 减少超时时间
                
                if (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferIndex);
                    
                    if (bufferInfo.size > 0 && outputBuffer != null) {
                        byte[] pcmData = new byte[bufferInfo.size];
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.get(pcmData, 0, bufferInfo.size);
                        
                        // 详细的输出数据日志
                        Log.d(TAG, "=== DECODED OUTPUT ===");
                        Log.d(TAG, "Decoded PCM data size: " + bufferInfo.size + " bytes");
                        Log.d(TAG, "Buffer info - offset: " + bufferInfo.offset + ", size: " + bufferInfo.size + ", flags: " + bufferInfo.flags);
                        
                        // 输出前8字节的PCM数据
                        if (pcmData.length >= 8) {
                            StringBuilder hex = new StringBuilder();
                            for (int i = 0; i < Math.min(8, pcmData.length); i++) {
                                hex.append(String.format("%02X ", pcmData[i]));
                            }
                            Log.d(TAG, "PCM data first 8 bytes: " + hex.toString());
                        }
                        
                        if (callback != null) {
                            callback.onPCMData(pcmData);
                            Log.d(TAG, "PCM data sent to callback");
                        } else {
                            Log.w(TAG, "Callback is null, dropping PCM data");
                        }
                        Log.d(TAG, "=== END DECODED OUTPUT ===");
                    } else {
                        Log.v(TAG, "Empty output buffer: size=" + bufferInfo.size + ", buffer=" + (outputBuffer != null));
                    }
                    
                    decoder.releaseOutputBuffer(outputBufferIndex, false);
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    Log.i(TAG, "Output format changed: " + newFormat);
                    
                    // 提取新的音频参数
                    int newSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    int newChannelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    
                    Log.i(TAG, "New audio format: " + newSampleRate + "Hz, " + newChannelCount + " channels");
                    
                    if (callback instanceof AudioFormatChangedCallback) {
                        ((AudioFormatChangedCallback) callback).onFormatChanged(newSampleRate, newChannelCount);
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // 正常情况，继续等待
                    Log.v(TAG, "No output buffer available, waiting...");
                } else {
                    Log.v(TAG, "Unexpected output buffer index: " + outputBufferIndex);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing output", e);
                if (callback != null) {
                    callback.onError(e);
                }
                break;
            }
        }
        
        Log.d(TAG, "Output processing thread stopped");
    }
    
    /**
     * 停止解码器
     */
    public void stop() {
        isRunning = false;
        
        if (decoder != null) {
            try {
                decoder.stop();
                decoder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping decoder", e);
            }
            decoder = null;
        }
    }
    
    /**
     * 检查是否支持指定的音频格式
     */
    public static boolean isFormatSupported(String mimeType) {
        try {
            MediaCodec decoder = MediaCodec.createDecoderByType(mimeType);
            decoder.release();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
} 