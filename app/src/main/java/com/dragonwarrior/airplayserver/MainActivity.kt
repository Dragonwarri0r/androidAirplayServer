package com.dragonwarrior.airplayserver

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.dragonwarrior.airplayserver.databinding.ActivityMainBinding
import com.dragonwarrior.airplayserver.model.NALPacket
import com.dragonwarrior.airplayserver.model.PCMPacket
import com.dragonwarrior.airplayserver.player.AacAudioPlayer
import com.dragonwarrior.airplayserver.player.VideoPlayer
import com.dragonwarrior.airplayserver.player.SharedVideoDataManager
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.github.serezhka.airplay.lib.AudioStreamInfo
import com.github.serezhka.airplay.lib.VideoStreamInfo
import com.github.serezhka.airplay.server.AirPlayServer
import com.github.serezhka.airplay.server.AirPlayConsumer
import com.github.serezhka.airplay.server.AirPlayConfig
import com.github.serezhka.airplay.lib.internal.OmgHaxConst
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.LinkedList
import java.nio.ByteBuffer

class MainActivity : BaseMirrorActivity<ActivityMainBinding>() {
    private var mSurfaceViewL: SurfaceView? = null
    private var mSurfaceViewR: SurfaceView? = null
    private var airPlayServer: AirPlayServer? = null
    private var mVideoPlayerL: VideoPlayer? = null
    private var mVideoPlayerR: VideoPlayer? = null
    private var mAacAudioPlayer: AacAudioPlayer? = null
    private val mVideoCacheListL = LinkedList<NALPacket>()
    private val mVideoCacheListR = LinkedList<NALPacket>()
    private val sharedVideoDataManager = SharedVideoDataManager.getInstance()
    


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
        
        // 初始化OmgHaxConst表格数据
        initializeOmgHaxConst()
        
        mSurfaceViewL = mBindingPair.left.surfaceView
        mSurfaceViewL!!.holder!!.addCallback(SurfaceHolder(mSurfaceViewL!!, this, true))
        mSurfaceViewR = mBindingPair.right.surfaceView
        mSurfaceViewR!!.holder!!.addCallback(SurfaceHolder(mSurfaceViewR!!, this, false))
        mSurfaceViewL!!.keepScreenOn = true
        
        // 初始化 AAC 音频播放器（用于 AAC ELD 格式）
        mAacAudioPlayer = AacAudioPlayer()
        mAacAudioPlayer!!.initialize()
        mAacAudioPlayer!!.start()
        Log.i(TAG, "AacAudioPlayer initialized successfully")


        airplayDataConsumer = object : AirPlayConsumer {
            override fun onVideo(video: ByteArray) {
//            Logger.i(TAG, "rev video length :%d", video.length);
                // 使用共享视频数据管理器避免重复拷贝
                val sharedData = sharedVideoDataManager.createSharedData(video)
                val nalPacketL = sharedData.createNALPacket()
                val nalPacketR = sharedData.createNALPacket()
                
                if (mVideoPlayerL != null) {
                    while (!mVideoCacheListL.isEmpty()) {
                        mVideoPlayerL!!.addPacker(mVideoCacheListL.removeFirst())
                    }
                    mVideoPlayerL!!.addPacker(nalPacketL)
                } else {
                    mVideoCacheListL.add(nalPacketL)
                }
                if (mVideoPlayerR != null) {
                    while (!mVideoCacheListR.isEmpty()) {
                        mVideoPlayerR!!.addPacker(mVideoCacheListR.removeFirst())
                    }
                    mVideoPlayerR!!.addPacker(nalPacketR)
                } else {
                    mVideoCacheListR.add(nalPacketR)
                }

                mSurfaceViewL?.post {
                    (mSurfaceViewL?.parent as ViewGroup).findViewById<View>(R.id.tv_connecting).visibility =
                        View.GONE
                }
                mSurfaceViewR?.post {
                    (mSurfaceViewR?.parent as ViewGroup).findViewById<View>(R.id.tv_connecting).visibility =
                        View.GONE
                }
            }

            override fun onVideoFormat(videoStreamInfo: VideoStreamInfo) {
                Log.d(TAG, "onVideoFormat: ${videoStreamInfo.streamConnectionId}")
                
                // 视频格式变化时，记录信息
                // VideoStreamInfo只包含streamConnectionId，没有分辨率信息
                // 分辨率变化会通过MediaCodec的onOutputFormatChanged回调处理
            }
            override fun onAudio(audio: ByteArray) {
                Log.d(TAG, "onAudio called with ${audio.size} bytes")
                
                if (audio.size < 10) {
                    Log.d(TAG, "Skipping small audio packet: ${audio.size} bytes")
                    return
                }
                
                mAacAudioPlayer?.pushAudioData(audio)
            }
            
            /**
             * 分析音频数据的模式和特征
             */
            private fun analyzeAudioData(data: ByteArray) {
                if (data.size < 4) return
                
                // 检查是否有ALAC magic number
                if (data.size >= 4) {
                    val first4 = ByteBuffer.wrap(data, 0, 4).int
                    Log.d(TAG, "First 4 bytes as int: $first4 (0x${String.format("%08X", first4)})")
                }
                
                // 检查数据的统计特征
                var zeroCount = 0
                var maxValue = 0
                var minValue = 255
                
                for (i in 0 until minOf(data.size, 100)) {
                    val unsigned = data[i].toInt() and 0xFF
                    if (unsigned == 0) zeroCount++
                    if (unsigned > maxValue) maxValue = unsigned
                    if (unsigned < minValue) minValue = unsigned
                }
                
                Log.d(TAG, "Data analysis: zeros=$zeroCount/100, range=$minValue-$maxValue")
                
                // 检查是否有重复模式
                if (data.size >= 8) {
                    val pattern1 = data.sliceArray(0..3)
                    val pattern2 = data.sliceArray(4..7)
                    val isRepeating = pattern1.contentEquals(pattern2)
                    Log.d(TAG, "Has repeating 4-byte pattern: $isRepeating")
                }
                
                // 检查数据长度是否符合音频帧大小
                val possibleFrameSizes = listOf(128, 256, 512, 1024, 2048)
                val isStandardFrameSize = possibleFrameSizes.contains(data.size)
                Log.d(TAG, "Is standard frame size: $isStandardFrameSize")
                
                // 检查是否是16位PCM的倍数
                val is16BitAligned = data.size % 2 == 0
                val is16BitStereo = data.size % 4 == 0
                Log.d(TAG, "16-bit aligned: $is16BitAligned, stereo aligned: $is16BitStereo")
            }
            
            /**
             * 检查音频数据是否是编码格式
             */
            private fun isEncodedAudioFormat(data: ByteArray): Boolean {
                if (data.size < 4) return false
                
                // 检查数据大小特征 - PCM通常是固定大小的帧
                // 而编码数据（ALAC/AAC）大小会变化
                val isVariableSize = data.size !in listOf(128, 256, 512, 1024, 2048, 4096)
                
                // 检查前几个字节的模式
                val first4Bytes = data.sliceArray(0..3)
                val isLikelyEncoded = when {
                    // AAC ADTS header
                    data[0] == 0xFF.toByte() && (data[1].toInt() and 0xF0) == 0xF0 -> {
                        Log.d(TAG, "Detected AAC format (ADTS header)")
                        true
                    }
                    // ALAC通常不以0x00开始，而是有特定的结构
                    data[0] == 0x00.toByte() && data[1] == 0x00.toByte() -> {
                        Log.d(TAG, "Possibly ALAC format (starts with 0x00)")
                        true
                    }
                    // 检查是否有明显的编码特征
                    isVariableSize -> {
                        Log.d(TAG, "Variable size suggests encoded format: ${data.size} bytes")
                        true
                    }
                    else -> {
                        Log.d(TAG, "Assuming PCM format")
                        false
                    }
                }
                
                // 额外检查：PCM数据通常有更均匀的分布
                if (!isLikelyEncoded) {
                    var zeroCount = 0
                    for (i in 0 until minOf(data.size, 32)) {
                        if (data[i] == 0.toByte()) zeroCount++
                    }
                    val zeroRatio = zeroCount.toFloat() / minOf(data.size, 32)
                    
                    // 如果零字节太多，可能是编码数据
                    if (zeroRatio > 0.3) {
                        Log.d(TAG, "High zero ratio ($zeroRatio) suggests encoded format")
                        return true
                    }
                }
                
                return isLikelyEncoded
            }

            override fun onAudioFormat(audioInfo: AudioStreamInfo) {
                val (sampleRate, channelCount) = parseAudioFormat(audioInfo.audioFormat)
                
                Log.i(TAG, "onAudioFormat called - Format: ${audioInfo.audioFormat}, ${sampleRate}Hz, ${channelCount} channels")
                
                mAacAudioPlayer?.setAudioFormat(sampleRate, channelCount, 16)
            }
            
            override fun onVideoSrcDisconnect() {
                Log.d(TAG, "onVideoSrcDisconnect")
            }
            
            override fun onAudioSrcDisconnect() {
                Log.d(TAG, "onAudioSrcDisconnect")
            }
        }
        val config = AirPlayConfig(SERVER_NAME, 1920, 1080, 30)
        airPlayServer = AirPlayServer(config, airplayDataConsumer)

        Thread(object : Runnable {
            override fun run() {
                try {
                    airPlayServer!!.start()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }, "start-server-thread").start()
    }

    override fun onStop() {
        super.onStop()

        mVideoPlayerL?.stopVideoPlay()
        mVideoPlayerL = null
        mVideoPlayerR?.stopVideoPlay()
        mVideoPlayerR = null
        airplayDataConsumer = null
        airPlayServer?.stop()
    }

    private var airplayDataConsumer: AirPlayConsumer? = null

    /**
     * 解析AudioFormat枚举，提取采样率和声道数
     */
    private fun parseAudioFormat(audioFormat: AudioStreamInfo.AudioFormat): Pair<Int, Int> {
        return when (audioFormat) {
            AudioStreamInfo.AudioFormat.PCM_8000_16_1 -> Pair(8000, 1)
            AudioStreamInfo.AudioFormat.PCM_8000_16_2 -> Pair(8000, 2)
            AudioStreamInfo.AudioFormat.PCM_16000_16_1 -> Pair(16000, 1)
            AudioStreamInfo.AudioFormat.PCM_16000_16_2 -> Pair(16000, 2)
            AudioStreamInfo.AudioFormat.PCM_24000_16_1 -> Pair(24000, 1)
            AudioStreamInfo.AudioFormat.PCM_24000_16_2 -> Pair(24000, 2)
            AudioStreamInfo.AudioFormat.PCM_32000_16_1 -> Pair(32000, 1)
            AudioStreamInfo.AudioFormat.PCM_32000_16_2 -> Pair(32000, 2)
            AudioStreamInfo.AudioFormat.PCM_44100_16_1 -> Pair(44100, 1)
            AudioStreamInfo.AudioFormat.PCM_44100_16_2 -> Pair(44100, 2)
            AudioStreamInfo.AudioFormat.PCM_44100_24_1 -> Pair(44100, 1)
            AudioStreamInfo.AudioFormat.PCM_44100_24_2 -> Pair(44100, 2)
            AudioStreamInfo.AudioFormat.PCM_48000_16_1 -> Pair(48000, 1)
            AudioStreamInfo.AudioFormat.PCM_48000_16_2 -> Pair(48000, 2)
            AudioStreamInfo.AudioFormat.PCM_48000_24_1 -> Pair(48000, 1)
            AudioStreamInfo.AudioFormat.PCM_48000_24_2 -> Pair(48000, 2)
            AudioStreamInfo.AudioFormat.ALAC_44100_16_2 -> Pair(44100, 2)
            AudioStreamInfo.AudioFormat.ALAC_44100_24_2 -> Pair(44100, 2)
            AudioStreamInfo.AudioFormat.ALAC_48000_16_2 -> Pair(48000, 2)
            AudioStreamInfo.AudioFormat.ALAC_48000_24_2 -> Pair(48000, 2)
            AudioStreamInfo.AudioFormat.AAC_LC_44100_2 -> Pair(44100, 2)
            AudioStreamInfo.AudioFormat.AAC_LC_48000_2 -> Pair(48000, 2)
            AudioStreamInfo.AudioFormat.AAC_ELD_44100_2 -> Pair(44100, 2)
            AudioStreamInfo.AudioFormat.AAC_ELD_48000_2 -> Pair(48000, 2)
            AudioStreamInfo.AudioFormat.AAC_ELD_16000_1 -> Pair(16000, 1)
            AudioStreamInfo.AudioFormat.AAC_ELD_24000_1 -> Pair(24000, 1)
            AudioStreamInfo.AudioFormat.AAC_ELD_44100_1 -> Pair(44100, 1)
            AudioStreamInfo.AudioFormat.AAC_ELD_48000_1 -> Pair(48000, 1)
            AudioStreamInfo.AudioFormat.OPUS_16000_1 -> Pair(16000, 1)
            AudioStreamInfo.AudioFormat.OPUS_24000_1 -> Pair(24000, 1)
            AudioStreamInfo.AudioFormat.OPUS_48000_1 -> Pair(48000, 1)
            else -> Pair(44100, 2) // 默认值
        }
    }

    private fun initializeOmgHaxConst() {
        try {
            Log.d(TAG, "Initializing OmgHaxConst from Android assets")
            
            val assetLoader = object : OmgHaxConst.AssetLoader {
                override fun readBytes(fileName: String): ByteArray {
                    return assets.open(fileName).use { inputStream ->
                        val buffer = ByteArray(8192)
                        val output = java.io.ByteArrayOutputStream()
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                        output.toByteArray()
                    }
                }
                
                override fun readInts(fileName: String): IntArray {
                    return assets.open(fileName).use { inputStream ->
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        val lines = mutableListOf<String>()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            lines.add(line!!)
                        }
                        
                        val result = IntArray(lines.size)
                        for (i in lines.indices) {
                            result[i] = java.lang.Long.decode(lines[i]).toInt()
                        }
                        result
                    }
                }
            }
            
            OmgHaxConst.initializeFromAssets(assetLoader)
            Log.d(TAG, "OmgHaxConst initialization completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OmgHaxConst", e)
            throw RuntimeException("Failed to initialize OmgHaxConst", e)
        }
    }



    class SurfaceHolder(
        val surfaceView: SurfaceView,
        val activity: MainActivity,
        var isLeft: Boolean
    ) : android.view.SurfaceHolder.Callback {
        override fun surfaceCreated(p0: android.view.SurfaceHolder) {
            Log.d(TAG, "surfaceCreated: ")
        }

        override fun surfaceChanged(
            p0: android.view.SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            if ((isLeft && activity.mVideoPlayerL == null) || (!isLeft && activity.mVideoPlayerR == null)) {
                Log.i(
                    TAG,
                    "surfaceChanged: width:$width---height$height"
                )
                if (isLeft) activity.mVideoPlayerL = VideoPlayer(p0.surface, width, height)
                else activity.mVideoPlayerR = VideoPlayer(p0.surface, width, height)

                val videoPlayer = if (isLeft) activity.mVideoPlayerL else activity.mVideoPlayerR
                videoPlayer!!.start()
                videoPlayer.setOutputFormatChangedListener(
                    VideoPlayer.OutputFormatChangedListener { width1, height1 ->
                        surfaceView.post {
                            try {
                                val lp = surfaceView.layoutParams as ConstraintLayout.LayoutParams
                                val lpw = (surfaceView.parent as ViewGroup).width.toFloat()
                                val lph = (surfaceView.parent as ViewGroup).height.toFloat()
                                val ratio = width1.toFloat() / height1.toFloat()
                                if (lpw / lph > ratio) {
                                    lp.width = (lph * ratio).toInt()
                                    lp.height = lph.toInt()
                                } else {
                                    lp.width = lpw.toInt()
                                    lp.height = (lpw / ratio).toInt()
                                }
                                Log.d(
                                    TAG,
                                    "surfaceChanged: lp.width:" + lp.width + "---lp.height:" + lp.height
                                )
                                surfaceView.layoutParams = lp
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                )
            }
        }

        override fun surfaceDestroyed(p0: android.view.SurfaceHolder) {}

    }


    override fun onDestroy() {
        super.onDestroy()
        
        // 清理 AAC 音频播放器
        mAacAudioPlayer?.let { player ->
            player.stop()
            player.release()
            Log.i(TAG, "AacAudioPlayer released")
        }
        
        // 清理 ALAC 音频播放器

    }

    companion object {
        private const val TAG = "MainActivity"
        const val SERVER_NAME = "VisionPro"
    }
}