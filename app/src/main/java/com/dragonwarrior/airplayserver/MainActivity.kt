package com.dragonwarrior.airplayserver

import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.dragonwarrior.airplayserver.databinding.ActivityMainBinding
import com.dragonwarrior.airplayserver.model.NALPacket
import com.dragonwarrior.airplayserver.model.PCMPacket
import com.dragonwarrior.airplayserver.player.AudioPlayer
import com.dragonwarrior.airplayserver.player.VideoPlayer
import com.ffalcon.mercury.android.sdk.ui.activity.BaseMirrorActivity
import com.github.serezhka.jap2lib.rtsp.AudioStreamInfo
import com.github.serezhka.jap2lib.rtsp.VideoStreamInfo
import com.github.serezhka.jap2server.AirPlayServer
import com.github.serezhka.jap2server.AirplayDataConsumer
import java.util.LinkedList

class MainActivity : BaseMirrorActivity<ActivityMainBinding>() {
    private var mSurfaceViewL: SurfaceView? = null
    private var mSurfaceViewR: SurfaceView? = null
    private var airPlayServer: AirPlayServer? = null
    private var mVideoPlayerL: VideoPlayer? = null
    private var mVideoPlayerR: VideoPlayer? = null
    private var mAudioPlayer: AudioPlayer? = null
    private val mVideoCacheListL = LinkedList<NALPacket>()
    private val mVideoCacheListR = LinkedList<NALPacket>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
        mSurfaceViewL = mBindingPair.left.surfaceView
        mSurfaceViewL!!.holder!!.addCallback(SurfaceHolder(mSurfaceViewL!!, this, true))
        mSurfaceViewR = mBindingPair.right.surfaceView
        mSurfaceViewR!!.holder!!.addCallback(SurfaceHolder(mSurfaceViewR!!, this, false))
        mSurfaceViewL!!.keepScreenOn = true
        mAudioPlayer = AudioPlayer()
        mAudioPlayer!!.start()

        airplayDataConsumer = object : AirplayDataConsumer {
            override fun onVideo(video: ByteArray?) {
//            Logger.i(TAG, "rev video length :%d", video.length);
                val nalPacketL = NALPacket()
                val nalPacketR = NALPacket()
                nalPacketL.nalData = video
                nalPacketR.nalData = video
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

            override fun onVideoFormat(videoStreamInfo: VideoStreamInfo?) {}
            override fun onAudio(audio: ByteArray?) {
//            Logger.i(TAG, "rev audio length :%d", audio.length);
                val pcmPacket = PCMPacket()
                pcmPacket.data = audio
                if (mAudioPlayer != null) {
                    mAudioPlayer!!.addPacker(pcmPacket)
                }
            }

            override fun onAudioFormat(audioInfo: AudioStreamInfo?) {
                Log.d(TAG, "onAudioFormat: " + audioInfo?.audioFormat)
            }
        }
        airPlayServer = AirPlayServer(SERVER_NAME, 7000, 49152, airplayDataConsumer)

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
        mAudioPlayer?.stopPlay()
        mAudioPlayer = null
        mVideoPlayerL?.stopVideoPlay()
        mVideoPlayerL = null
        mVideoPlayerR?.stopVideoPlay()
        mVideoPlayerR = null
        airplayDataConsumer = null
        airPlayServer?.stop()
    }

    private var airplayDataConsumer: AirplayDataConsumer? = null

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


    companion object {
        private const val TAG = "MainActivity"
        const val SERVER_NAME = "VisionPro"
    }
}