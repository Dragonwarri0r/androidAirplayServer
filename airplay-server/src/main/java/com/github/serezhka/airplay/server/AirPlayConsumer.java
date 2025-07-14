package com.github.serezhka.airplay.server;

import com.github.serezhka.airplay.lib.AudioStreamInfo;
import com.github.serezhka.airplay.lib.VideoStreamInfo;

public interface AirPlayConsumer {

    void onVideoFormat(VideoStreamInfo videoStreamInfo);

    void onVideo(byte[] bytes);

    void onVideoSrcDisconnect();

    void onAudioFormat(AudioStreamInfo audioStreamInfo);

    void onAudio(byte[] bytes);

    void onAudioSrcDisconnect();

    // HLS stuff, youtube
    default void onMediaPlaylist(String playlistUri) {
    }

    default void onMediaPlaylistRemove() {
    }

    default void onMediaPlaylistPause() {
    }

    default void onMediaPlaylistResume() {
    }

    default PlaybackInfo playbackInfo() {
        return new PlaybackInfo(0, 0);
    }

    class PlaybackInfo {
        private final double duration;
        private final double position;
        
        public PlaybackInfo(double duration, double position) {
            this.duration = duration;
            this.position = position;
        }
        
        public double duration() {
            return duration;
        }
        
        public double position() {
            return position;
        }
    }
}
