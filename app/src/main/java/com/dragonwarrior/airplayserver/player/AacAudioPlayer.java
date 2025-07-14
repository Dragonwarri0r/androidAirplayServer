package com.dragonwarrior.airplayserver.player;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AacAudioPlayer {
    private static final String TAG = "AacAudioPlayer";
    
    private AudioTrack audioTrack;
    private MediaCodec mediaCodec;
    private boolean isPlaying = false;
    private boolean isInitialized = false;
    
    private final BlockingQueue<byte[]> audioDataQueue = new LinkedBlockingQueue<>();
    private Thread decodingThread;
    
    // 音频参数
    private int sampleRate = 44100;
    private int channels = 2;
    private int bitsPerSample = 16;
    
    public AacAudioPlayer() {
        // 构造函数
    }
    
    public void initialize() {
        try {
            // 创建 MediaCodec 解码器
            mediaCodec = MediaCodec.createDecoderByType("audio/mp4a-latm");
            
            // 配置 MediaFormat
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channels);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 64000);
            
            // AAC ELD 特定配置
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, 39); // AAC_ELD profile
            
            // 添加 AAC ELD 的 codec_data（从参考项目获取）
            // 这是 AAC ELD 44.1kHz 立体声的配置
            byte[] codecData = {(byte)0xf8, (byte)0xe8, (byte)0x50, (byte)0x00};
            format.setByteBuffer("csd-0", ByteBuffer.wrap(codecData));
            
            // 配置并启动解码器
            mediaCodec.configure(format, null, null, 0);
            mediaCodec.start();
            
            // 创建 AudioTrack
            int channelConfig = (channels == 1) ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            
            int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            bufferSize = Math.max(bufferSize, 4096 * channels * (bitsPerSample / 8));
            
            audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM
            );
            
            if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioTrack, state: " + audioTrack.getState());
                return;
            }
            
            isInitialized = true;
            Log.i(TAG, "AAC audio player initialized successfully");
            
        } catch (IOException e) {
            Log.e(TAG, "Error initializing MediaCodec", e);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing AAC audio player", e);
        }
    }
    
    public void start() {
        Log.d(TAG, "start() called - isInitialized=" + isInitialized + ", isPlaying=" + isPlaying);
        
        if (!isInitialized) {
            Log.e(TAG, "Player not initialized");
            return;
        }
        
        if (isPlaying) {
            Log.w(TAG, "Player already started");
            return;
        }
        
        isPlaying = true;
        audioTrack.play();
        
        // 启动解码线程
        decodingThread = new Thread(this::decodingLoop);
        decodingThread.start();
        
        Log.i(TAG, "AAC audio player started successfully");
    }
    
    public void stop() {
        if (!isPlaying) {
            return;
        }
        
        isPlaying = false;
        
        try {
            if (decodingThread != null) {
                decodingThread.interrupt();
                decodingThread.join(1000);
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Error stopping threads", e);
        }
        
        if (audioTrack != null) {
            audioTrack.stop();
        }
        
        audioDataQueue.clear();
    }
    
    public void release() {
        stop();
        
        if (mediaCodec != null) {
            try {
                mediaCodec.stop();
                mediaCodec.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaCodec", e);
            }
            mediaCodec = null;
        }
        
        if (audioTrack != null) {
            audioTrack.release();
            audioTrack = null;
        }
        
        isInitialized = false;
    }
    
    public void pushAudioData(byte[] data) {
        if (!isPlaying || data == null || data.length == 0) {
            Log.d(TAG, "pushAudioData: skipping - isPlaying=" + isPlaying + ", data=" + (data != null ? data.length : "null"));
            return;
        }
        
        try {
            boolean offered = audioDataQueue.offer(data);
            Log.d(TAG, "pushAudioData: " + data.length + " bytes, queued=" + offered + ", queue size=" + audioDataQueue.size());
        } catch (Exception e) {
            Log.e(TAG, "Error pushing audio data", e);
        }
    }
    
    private void decodingLoop() {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        Log.d(TAG, "Decoding loop started");
        
        while (isPlaying && !Thread.currentThread().isInterrupted()) {
            try {
                byte[] encryptedData = audioDataQueue.take();
                Log.d(TAG, "Processing audio data: " + encryptedData.length + " bytes");
                
                if (encryptedData == null || encryptedData.length == 0) {
                    continue;
                }
                
                // 解密数据（如果需要）
                byte[] aacData = decryptAudioData(encryptedData);
                
                // 使用 MediaCodec 解码
                decodeAacData(aacData, bufferInfo);
                
            } catch (InterruptedException e) {
                Log.d(TAG, "Decoding thread interrupted");
                break;
            } catch (Exception e) {
                Log.e(TAG, "Error in decoding loop", e);
            }
        }
        
        Log.d(TAG, "Decoding loop ended");
    }
    
    private byte[] decryptAudioData(byte[] encryptedData) {
        // TODO: 实现音频数据解密
        // 目前直接返回原始数据，实际需要根据 AirPlay 协议进行解密
        return encryptedData;
    }
    
    private void decodeAacData(byte[] aacData, MediaCodec.BufferInfo bufferInfo) {
        try {
            // 获取输入缓冲区
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(1000);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                if (inputBuffer != null) {
                    inputBuffer.clear();
                    inputBuffer.put(aacData);
                    
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, aacData.length, 0, 0);
                    Log.d(TAG, "Queued input buffer: " + aacData.length + " bytes");
                }
            } else {
                Log.w(TAG, "No input buffer available");
            }
            
            // 尝试获取多个输出缓冲区
            int maxOutputAttempts = 5;
            for (int i = 0; i < maxOutputAttempts; i++) {
                int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 100);
                
                if (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                    
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        byte[] pcmData = new byte[bufferInfo.size];
                        outputBuffer.get(pcmData);
                        
                        Log.d(TAG, "Decoded PCM data: " + pcmData.length + " bytes");
                        playPcmData(pcmData);
                    }
                    
                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                    
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = mediaCodec.getOutputFormat();
                    Log.i(TAG, "Output format changed: " + newFormat);
                    
                } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.d(TAG, "No output buffer available");
                    break; // 没有更多输出，跳出循环
                    
                } else {
                    break;
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error decoding AAC data", e);
        }
    }
    
    private void playPcmData(byte[] pcmData) {
        if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            try {
                int bytesWritten = audioTrack.write(pcmData, 0, pcmData.length);
                Log.d(TAG, "AudioTrack.write: " + bytesWritten + "/" + pcmData.length + " bytes");
                
                if (bytesWritten < 0) {
                    Log.e(TAG, "Error writing to AudioTrack: " + bytesWritten);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error playing PCM data", e);
            }
        } else {
            Log.e(TAG, "AudioTrack not ready - state: " + (audioTrack != null ? audioTrack.getState() : "null"));
        }
    }
    
    public boolean isPlaying() {
        return isPlaying;
    }
    
    public void setAudioFormat(int sampleRate, int channels, int bitsPerSample) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bitsPerSample = bitsPerSample;
    }
} 