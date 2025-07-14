package com.dragonwarrior.airplayserver.player;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import com.dragonwarrior.airplayserver.alac.AlacContext;
import com.dragonwarrior.airplayserver.alac.AlacDecodeUtils;
import com.dragonwarrior.airplayserver.alac.AlacFile;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AlacAudioPlayer {
    private static final String TAG = "AlacAudioPlayer";
    
    private AudioTrack audioTrack;
    private AlacContext alacContext;
    private AlacFile alacFile;
    private boolean isPlaying = false;
    private boolean isInitialized = false;
    
    private final BlockingQueue<byte[]> audioDataQueue = new LinkedBlockingQueue<>();
    private Thread playbackThread;
    private Thread decodingThread;
    
    // 音频参数
    private int sampleRate = 44100;
    private int channels = 2;
    private int bitsPerSample = 16;
    
    public AlacAudioPlayer() {
        Log.d(TAG, "AlacAudioPlayer created");
    }
    
    public void initialize() {
        Log.d(TAG, "Initializing ALAC audio player");
        
        try {
            // 初始化 ALAC 解码器
            alacContext = new AlacContext();
            alacFile = new AlacFile();
            
            // 设置音频参数 - 这些参数通常从 AirPlay 音频流中获取
            alacFile.setinfo_8a_rate = sampleRate;
            alacFile.numchannels = channels;
            alacFile.setinfo_sample_size = bitsPerSample;
            alacFile.setinfo_max_samples_per_frame = 4096;
            
            // 计算缓冲区大小
            int channelConfig = (channels == 1) ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
            int audioFormat = (bitsPerSample == 16) ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_8BIT;
            
            int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            bufferSize = Math.max(bufferSize, 4096 * channels * (bitsPerSample / 8));
            
            // 创建 AudioTrack
            audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM
            );
            
            if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioTrack");
                return;
            }
            
            isInitialized = true;
            Log.d(TAG, "ALAC audio player initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing ALAC audio player", e);
        }
    }
    
    public void start() {
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
        
        // 启动播放线程
        playbackThread = new Thread(this::playbackLoop);
        playbackThread.start();
        
        Log.d(TAG, "ALAC audio player started");
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
            
            if (playbackThread != null) {
                playbackThread.interrupt();
                playbackThread.join(1000);
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Error stopping threads", e);
        }
        
        if (audioTrack != null) {
            audioTrack.stop();
        }
        
        audioDataQueue.clear();
        Log.d(TAG, "ALAC audio player stopped");
    }
    
    public void release() {
        stop();
        
        if (audioTrack != null) {
            audioTrack.release();
            audioTrack = null;
        }
        
        alacContext = null;
        alacFile = null;
        isInitialized = false;
        
        Log.d(TAG, "ALAC audio player released");
    }
    
    public void pushAudioData(byte[] data) {
        Log.d(TAG, "pushAudioData called with " + data.length + " bytes, isPlaying: " + isPlaying);
        
        if (!isPlaying) {
            Log.w(TAG, "Player not playing, ignoring audio data");
            return;
        }
        
        try {
            boolean offered = audioDataQueue.offer(data);
            Log.d(TAG, "Audio data queued: " + offered + ", queue size: " + audioDataQueue.size());
        } catch (Exception e) {
            Log.e(TAG, "Error pushing audio data", e);
        }
    }
    
    private void decodingLoop() {
        Log.d(TAG, "Decoding loop started");
        
        while (isPlaying && !Thread.currentThread().isInterrupted()) {
            try {
                Log.d(TAG, "Waiting for audio data, queue size: " + audioDataQueue.size());
                byte[] encryptedData = audioDataQueue.take();
                
                Log.d(TAG, "Processing audio data: " + encryptedData.length + " bytes");
                
                if (encryptedData == null || encryptedData.length == 0) {
                    Log.w(TAG, "Received null or empty audio data");
                    continue;
                }
                
                // 解密数据（如果需要）
                byte[] alacData = decryptAudioData(encryptedData);
                Log.d(TAG, "Decrypted data size: " + alacData.length);
                
                // 使用 ALAC 解码器解码
                byte[] pcmData = decodeAlacData(alacData);
                
                if (pcmData != null && pcmData.length > 0) {
                    Log.d(TAG, "Decoded PCM data: " + pcmData.length + " bytes");
                    // 播放 PCM 数据
                    playPcmData(pcmData);
                } else {
                    Log.w(TAG, "Failed to decode audio data or got empty result");
                }
                
            } catch (InterruptedException e) {
                Log.d(TAG, "Decoding thread interrupted");
                break;
            } catch (Exception e) {
                Log.e(TAG, "Error in decoding loop", e);
            }
        }
        
        Log.d(TAG, "Decoding loop ended");
    }
    
    private void playbackLoop() {
        Log.d(TAG, "Playback loop started");
        
        // 这个方法现在主要用于监控播放状态
        while (isPlaying && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(100);
                
                // 检查 AudioTrack 状态
                if (audioTrack != null && audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioTrack state error");
                    break;
                }
                
            } catch (InterruptedException e) {
                Log.d(TAG, "Playback thread interrupted");
                break;
            } catch (Exception e) {
                Log.e(TAG, "Error in playback loop", e);
            }
        }
        
        Log.d(TAG, "Playback loop ended");
    }
    
    private byte[] decryptAudioData(byte[] encryptedData) {
        // TODO: 实现音频数据解密
        // 目前直接返回原始数据，实际需要根据 AirPlay 协议进行解密
        return encryptedData;
    }
    
    private byte[] decodeAlacData(byte[] alacData) {
        Log.d(TAG, "decodeAlacData called with " + alacData.length + " bytes");
        
        try {
            // 创建输出缓冲区
            int maxSamples = alacFile.setinfo_max_samples_per_frame;
            Log.d(TAG, "Max samples per frame: " + maxSamples + ", channels: " + channels);
            
            if (maxSamples == 0) {
                Log.w(TAG, "Max samples per frame is 0, setting to default 4096");
                maxSamples = 4096;
            }
            
            int[] outputBuffer = new int[maxSamples * channels];
            
            // 解码 ALAC 数据
            Log.d(TAG, "Calling AlacDecodeUtils.decode_frame");
            int result = AlacDecodeUtils.decode_frame(alacFile, alacData, outputBuffer, outputBuffer.length);
            
            Log.d(TAG, "decode_frame returned: " + result);
            
            if (result < 0) {
                Log.e(TAG, "Failed to decode ALAC frame: " + result);
                return null;
            }
            
            if (result == 0) {
                Log.w(TAG, "decode_frame returned 0 samples");
                return null;
            }
            
            // 将 int 数组转换为 byte 数组
            byte[] pcmData = new byte[result * channels * (bitsPerSample / 8)];
            Log.d(TAG, "Converting " + result + " samples to PCM, output size: " + pcmData.length);
            
            for (int i = 0; i < result * channels; i++) {
                if (bitsPerSample == 16) {
                    // 16-bit PCM
                    short sample = (short) outputBuffer[i];
                    pcmData[i * 2] = (byte) (sample & 0xFF);
                    pcmData[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
                } else {
                    // 8-bit PCM
                    pcmData[i] = (byte) outputBuffer[i];
                }
            }
            
            Log.d(TAG, "Successfully decoded to " + pcmData.length + " bytes of PCM data");
            return pcmData;
            
        } catch (Exception e) {
            Log.e(TAG, "Error decoding ALAC data", e);
            return null;
        }
    }
    
    private void playPcmData(byte[] pcmData) {
        Log.d(TAG, "playPcmData called with " + pcmData.length + " bytes");
        
        if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            try {
                Log.d(TAG, "AudioTrack state: " + audioTrack.getState() + ", playback state: " + audioTrack.getPlayState());
                
                int bytesWritten = audioTrack.write(pcmData, 0, pcmData.length);
                
                Log.d(TAG, "AudioTrack.write returned: " + bytesWritten + " (expected: " + pcmData.length + ")");
                
                if (bytesWritten < 0) {
                    Log.e(TAG, "Error writing to AudioTrack: " + bytesWritten);
                } else if (bytesWritten != pcmData.length) {
                    Log.w(TAG, "Partial write to AudioTrack: " + bytesWritten + "/" + pcmData.length);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error playing PCM data", e);
            }
        } else {
            Log.e(TAG, "AudioTrack is null or not initialized");
            if (audioTrack != null) {
                Log.e(TAG, "AudioTrack state: " + audioTrack.getState());
            }
        }
    }
    
    public boolean isPlaying() {
        return isPlaying;
    }
    
    public void setAudioFormat(int sampleRate, int channels, int bitsPerSample) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bitsPerSample = bitsPerSample;
        
        Log.d(TAG, "Audio format set: " + sampleRate + "Hz, " + channels + " channels, " + bitsPerSample + " bits");
    }
} 