package com.dragonwarrior.airplayserver.player

import com.dragonwarrior.airplayserver.model.NALPacket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 共享视频数据管理器，避免重复拷贝相同的视频数据
 */
class SharedVideoDataManager {
    private val sharedDataMap = ConcurrentHashMap<Int, SharedVideoData>()
    private val idGenerator = AtomicInteger(0)
    
    /**
     * 创建共享的视频数据
     */
    fun createSharedData(videoData: ByteArray): SharedVideoData {
        val id = idGenerator.incrementAndGet()
        val sharedData = SharedVideoData(id, videoData)
        sharedDataMap[id] = sharedData
        return sharedData
    }
    
    /**
     * 获取共享的视频数据
     */
    fun getSharedData(id: Int): SharedVideoData? {
        return sharedDataMap[id]
    }
    
    /**
     * 释放共享的视频数据
     */
    fun releaseSharedData(id: Int) {
        val sharedData = sharedDataMap.remove(id)
        sharedData?.release()
    }
    
    /**
     * 共享视频数据类
     */
    class SharedVideoData(val id: Int, val videoData: ByteArray) {
        private val refCount = AtomicInteger(2) // 两个SurfaceView共享
        
        /**
         * 创建NAL数据包（不复制数据）
         */
        fun createNALPacket(): NALPacket {
            val packet = NALPacket()
            packet.nalData = videoData // 共享同一份数据
            return packet
        }
        
        /**
         * 减少引用计数
         */
        fun release() {
            if (refCount.decrementAndGet() <= 0) {
                // 可以在这里做清理工作
            }
        }
        
        /**
         * 获取当前引用计数
         */
        fun getRefCount(): Int = refCount.get()
    }
    
    companion object {
        @Volatile
        private var INSTANCE: SharedVideoDataManager? = null
        
        fun getInstance(): SharedVideoDataManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharedVideoDataManager().also { INSTANCE = it }
            }
        }
    }
} 