package com.dragonwarrior.airplayserver.player;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaFormat;
import android.util.Log;

import com.dragonwarrior.airplayserver.model.PCMPacket;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AudioPlayer extends Thread {

    private static String TAG = "AudioPlayer";
    private AudioTrack mTrack;
    private int mChannel = AudioFormat.CHANNEL_OUT_STEREO;
    private int mSampleRate = 44100;
    private boolean isStopThread = false;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    BlockingQueue<PCMPacket> packets = new LinkedBlockingQueue<PCMPacket>(500);
    
    // 音频解码器
    private AudioDecoder audioDecoder;
    private boolean needsDecoding = false;
    private boolean useDirectPlayback = true; // 添加直接播放模式标志
    private boolean useByteSwap = true; // 添加字节序转换标志

    public AudioPlayer() {
        this.mTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate, mChannel, mAudioFormat,
                AudioTrack.getMinBufferSize(mSampleRate, mChannel, mAudioFormat), AudioTrack.MODE_STREAM);
        
        // 设置音量
        this.mTrack.setVolume(1.0f);
        this.mTrack.play();
        
        // 初始化音频解码器
        initAudioDecoder();
    }
    
    /**
     * 初始化音频解码器
     */
    private void initAudioDecoder() {
        audioDecoder = new AudioDecoder(new AudioDecoder.AudioFormatChangedCallback() {
            @Override
            public void onPCMData(byte[] pcmData) {
                // 解码后的PCM数据直接播放
                if (mTrack != null && mTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    int written = mTrack.write(pcmData, 0, pcmData.length);
                    if (written != pcmData.length) {
                        Log.w(TAG, "AudioTrack write incomplete in decoder: " + written + "/" + pcmData.length + " bytes");
                    } else {
                        Log.v(TAG, "Successfully wrote decoded PCM data: " + pcmData.length + " bytes");
                    }
                } else {
                    Log.w(TAG, "AudioTrack not playing, dropping decoded PCM data: " + pcmData.length + " bytes");
                }
            }
            
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Audio decoder error, falling back to direct PCM playback", e);
                needsDecoding = false; // 回退到直接播放
                
                // 重新创建AudioTrack以确保正常播放
                recreateAudioTrack();
            }
            
            @Override
            public void onFormatChanged(int sampleRate, int channelCount) {
                Log.i(TAG, "Decoder format changed: " + sampleRate + "Hz, " + channelCount + " channels");
                
                // 如果格式与当前AudioTrack不匹配，重新创建
                int expectedChannel = channelCount == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
                if (mSampleRate != sampleRate || mChannel != expectedChannel) {
                    Log.i(TAG, "Recreating AudioTrack for new format");
                    mSampleRate = sampleRate;
                    mChannel = expectedChannel;
                    recreateAudioTrack();
                }
            }
        });
    }
    
    /**
     * 设置音频格式
     */
    public void setAudioFormat(String mimeType, int sampleRate, int channelCount) {
        Log.d(TAG, "Setting audio format: " + mimeType + ", " + sampleRate + "Hz, " + channelCount + " channels");
        
        // 检查是否需要解码
        if ("audio/alac".equals(mimeType) || 
            "audio/mp4a-latm".equals(mimeType) ||
            MediaFormat.MIMETYPE_AUDIO_AAC.equals(mimeType)) {
            
            if (AudioDecoder.isFormatSupported(mimeType)) {
                audioDecoder.setAudioFormat(mimeType, sampleRate, channelCount);
                needsDecoding = audioDecoder.initialize();
                Log.d(TAG, "Audio decoder initialized: " + needsDecoding);
            } else {
                Log.w(TAG, "Audio format not supported: " + mimeType);
                needsDecoding = false;
            }
        } else {
            needsDecoding = false;
            Log.d(TAG, "Using direct PCM playback");
        }
        
        // 更新AudioTrack参数
        this.mSampleRate = sampleRate;
        this.mChannel = channelCount == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
        
        // 重新创建AudioTrack
        recreateAudioTrack();
    }
    
    /**
     * 重新创建AudioTrack
     */
    private void recreateAudioTrack() {
        if (mTrack != null) {
            mTrack.stop();
            mTrack.release();
        }
        
        int bufferSize = AudioTrack.getMinBufferSize(mSampleRate, mChannel, mAudioFormat);
        mTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate, mChannel, mAudioFormat,
                bufferSize, AudioTrack.MODE_STREAM);
        
        // 设置音量
        mTrack.setVolume(1.0f);
        mTrack.play();
        
        Log.d(TAG, "AudioTrack created: " + mSampleRate + "Hz, " + 
              (mChannel == AudioFormat.CHANNEL_OUT_MONO ? "mono" : "stereo") + 
              ", buffer size: " + bufferSize);
    }

    public void addPacker(PCMPacket pcmPacket) {
        try {
            packets.put(pcmPacket);
        } catch (InterruptedException e) {
            Log.e(TAG, "addPacker: ", e);
        }
    }

    @Override
    public void run() {
        super.run();
        while (!isStopThread) {
            try {
                doPlay(packets.take());
            } catch (InterruptedException e) {
                Log.e(TAG, "run: take error: ", e);
            }
        }
    }

    private void doPlay(PCMPacket pcmPacket) {
        if (mTrack != null) {
            try {
                // 检查AudioTrack状态
                if (mTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                    Log.w(TAG, "AudioTrack not initialized, recreating...");
                    recreateAudioTrack();
                    return;
                }
                
                if (mTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                    Log.w(TAG, "AudioTrack not playing, starting playback...");
                    mTrack.play();
                }
                
                // 首先尝试直接播放模式
                if (useDirectPlayback) {
                    // 检查数据对齐
                    byte[] audioData = pcmPacket.data;
                    int dataLength = audioData.length;
                    
                    // 对于16位PCM，数据长度必须是2的倍数
                    // 对于立体声，数据长度必须是4的倍数
                    int alignedLength = dataLength;
                    if (mAudioFormat == AudioFormat.ENCODING_PCM_16BIT) {
                        if (mChannel == AudioFormat.CHANNEL_OUT_STEREO) {
                            // 立体声：对齐到4字节边界
                            alignedLength = (dataLength / 4) * 4;
                        } else {
                            // 单声道：对齐到2字节边界
                            alignedLength = (dataLength / 2) * 2;
                        }
                    }
                    
                    if (alignedLength != dataLength) {
                        Log.w(TAG, "Data alignment issue: original=" + dataLength + ", aligned=" + alignedLength);
                    }
                    
                    if (alignedLength > 0) {
                        // 实验性：尝试字节序转换
                        byte[] processedData = audioData;
                        if (useByteSwap && alignedLength >= 4) {
                            // 尝试16位字节序转换
                            processedData = new byte[alignedLength];
                            for (int i = 0; i < alignedLength - 1; i += 2) {
                                // 交换字节序 (little endian <-> big endian)
                                processedData[i] = audioData[i + 1];
                                processedData[i + 1] = audioData[i];
                            }
                            Log.v(TAG, "Applied byte order conversion");
                        }
                        
                        // 直接播放解密后的数据作为PCM
                        int written = mTrack.write(processedData, 0, alignedLength);
                        if (written < 0) {
                            Log.e(TAG, "AudioTrack write error: " + written);
                            // 尝试重新创建AudioTrack
                            recreateAudioTrack();
                        } else if (written != alignedLength) {
                            Log.w(TAG, "AudioTrack write incomplete in direct mode: " + written + "/" + alignedLength + " bytes");
                            Log.w(TAG, "AudioTrack state: " + mTrack.getPlayState() + ", buffer size: " + mTrack.getBufferSizeInFrames());
                        } else {
                            Log.v(TAG, "Successfully wrote audio data in direct mode: " + alignedLength + " bytes");
                        }
                    } else {
                        Log.w(TAG, "Dropping audio data due to alignment issues: " + dataLength + " bytes");
                    }
                } else if (needsDecoding && audioDecoder != null) {
                    // 使用解码器解码音频数据
                    audioDecoder.decode(pcmPacket.data);
                } else {
                    // 直接播放PCM数据
                    int written = mTrack.write(pcmPacket.data, 0, pcmPacket.data.length);
                    // 只在出现问题时打印日志
                    if (written != pcmPacket.data.length) {
                        Log.w(TAG, "AudioTrack write incomplete: " + written + "/" + pcmPacket.data.length + " bytes");
                    }
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "AudioTrack IllegalStateException, recreating...", e);
                recreateAudioTrack();
            } catch (Exception e) {
                Log.e(TAG, "doPlay: error", e);
                // 尝试重新创建AudioTrack
                recreateAudioTrack();
            }
        }
    }

    /**
     * 设置是否使用字节序转换
     */
    public void setUseByteSwap(boolean useByteSwap) {
        this.useByteSwap = useByteSwap;
        Log.d(TAG, "Byte swap mode changed to: " + useByteSwap);
    }
    
    /**
     * 设置是否使用直接播放模式
     */
    public void setUseDirectPlayback(boolean useDirectPlayback) {
        this.useDirectPlayback = useDirectPlayback;
        Log.d(TAG, "Audio playback mode changed to: " + (useDirectPlayback ? "DIRECT" : "DECODE"));
    }
    
    /**
     * 获取当前播放模式
     */
    public boolean isUsingDirectPlayback() {
        return useDirectPlayback;
    }

    public void stopPlay() {
        isStopThread = true;
        
        // 停止音频解码器
        if (audioDecoder != null) {
            audioDecoder.stop();
            audioDecoder = null;
        }
        
        if (mTrack != null) {
            mTrack.flush();
            mTrack.stop();
            mTrack.release();
            packets.clear();
            mTrack = null;
        }
    }

}
