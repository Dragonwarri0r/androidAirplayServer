package com.dragonwarrior.airplayserver.player;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import com.dragonwarrior.airplayserver.model.NALPacket;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class VideoPlayer {
    private static final String TAG = "VideoPlayer";
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private final int mVideoWidth = 540;
    private final int mVideoHeight = 960;
    private final MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private MediaCodec mDecoder = null;
    private final Surface mSurface;
    // 增加队列容量以减少阻塞
    private BlockingQueue<NALPacket> packets = new LinkedBlockingQueue<>(1000);
    private final HandlerThread mDecodeThread = new HandlerThread("VideoDecoder");
    private volatile boolean isRunning = false;

    private OutputFormatChangedListener mOutputFormatChangedListener;

    public void setOutputFormatChangedListener(OutputFormatChangedListener listener) {
        mOutputFormatChangedListener = listener;
    }

    private final MediaCodec.Callback mDecoderCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            if (!isRunning) return;
            
            try {
                // 使用非阻塞方式获取数据包，避免在回调中阻塞
                NALPacket packet = packets.poll();
                if (packet != null) {
                    ByteBuffer inputBuffer = codec.getInputBuffer(index);
                    if (inputBuffer != null) {
                        inputBuffer.clear();
                        inputBuffer.put(packet.nalData);
                        codec.queueInputBuffer(index, 0, packet.nalData.length, packet.pts, 0);
                    }
                } else {
                    // 没有数据包时，发送空帧以保持流畅性
                    codec.queueInputBuffer(index, 0, 0, 0, 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in onInputBufferAvailable", e);
            }
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
            if (!isRunning) return;
            
            try {
                // 立即释放输出缓冲区以保持流畅性
                codec.releaseOutputBuffer(index, true);
            } catch (Exception e) {
                Log.e(TAG, "Error in onOutputBufferAvailable", e);
            }
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
            Log.e(TAG, "MediaCodec error", e);
            // 尝试恢复
            try {
                restartDecoder();
            } catch (Exception restartError) {
                Log.e(TAG, "Failed to restart decoder", restartError);
            }
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            try {
                int width = format.getInteger(MediaFormat.KEY_WIDTH);
                int height = format.getInteger(MediaFormat.KEY_HEIGHT);
                Log.i(TAG, "Output format changed: " + width + "x" + height);
                
                if (mOutputFormatChangedListener != null) {
                    mOutputFormatChangedListener.onSizeChanged(width, height);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in onOutputFormatChanged", e);
            }
        }
    };

    public VideoPlayer(Surface surface, int width, int height) {
        mSurface = surface;
        // 可以在这里设置动态分辨率，但目前使用固定值
    }

    public void initDecoder() {
        if (isRunning) return;
        
        mDecodeThread.start();
        isRunning = true;
        
        try {
            mDecoder = MediaCodec.createDecoderByType(MIME_TYPE);
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mVideoWidth, mVideoHeight);
            
            // 优化解码器配置
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024); // 1MB 输入缓冲区
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1); // 低延迟模式
            
            mDecoder.configure(format, mSurface, null, 0);
            mDecoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            mDecoder.setCallback(mDecoderCallback, new Handler(mDecodeThread.getLooper()));
            mDecoder.start();
            
            Log.i(TAG, "Video decoder initialized: " + mVideoWidth + "x" + mVideoHeight);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize decoder", e);
            isRunning = false;
        }
    }

    public void addPacker(NALPacket nalPacket) {
        if (!isRunning || nalPacket == null || nalPacket.nalData == null) {
            return;
        }
        
        try {
            // 使用非阻塞方式添加数据包
            boolean offered = packets.offer(nalPacket);
            if (!offered) {
                // 队列满了，丢弃最旧的包
                packets.poll();
                packets.offer(nalPacket);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adding packet", e);
        }
    }

    public void start() {
        initDecoder();
    }

    public void stopVideoPlay() {
        isRunning = false;
        
        try {
            if (mDecoder != null) {
                mDecoder.stop();
                mDecoder.release();
                mDecoder = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping decoder", e);
        }
        
        try {
            if (mDecodeThread != null) {
                mDecodeThread.quitSafely();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping decode thread", e);
        }
        
        packets.clear();
    }
    
    /**
     * 重启解码器（用于错误恢复）
     */
    private void restartDecoder() {
        Log.i(TAG, "Restarting video decoder");
        
        try {
            if (mDecoder != null) {
                mDecoder.stop();
                mDecoder.release();
            }
            
            // 重新创建解码器
            mDecoder = MediaCodec.createDecoderByType(MIME_TYPE);
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mVideoWidth, mVideoHeight);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024);
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
            
            mDecoder.configure(format, mSurface, null, 0);
            mDecoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            mDecoder.setCallback(mDecoderCallback, new Handler(mDecodeThread.getLooper()));
            mDecoder.start();
            
            Log.i(TAG, "Video decoder restarted successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart decoder", e);
            isRunning = false;
        }
    }
    
    /**
     * 更新视频格式（分辨率变化时调用）
     */
    public void updateVideoFormat(int width, int height) {
        Log.i(TAG, "Video format update requested: " + width + "x" + height);
        
        // MediaCodec 通常能自动处理合理的分辨率变化
        // 如果需要重新创建解码器，可以在这里实现
        // 但这会导致短暂的视频中断
    }

    /**
     * 获取当前队列大小（用于监控性能）
     */
    public int getQueueSize() {
        return packets.size();
    }
    
    /**
     * 清空队列（用于快速恢复）
     */
    public void clearQueue() {
        packets.clear();
    }

    public interface OutputFormatChangedListener {
        void onSizeChanged(int width, int height);
    }
}
