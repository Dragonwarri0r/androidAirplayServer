package com.github.serezhka.airplay.server.internal.handler.session;

import com.github.serezhka.airplay.lib.AirPlay;
import com.github.serezhka.airplay.server.internal.AudioControlServer;
import com.github.serezhka.airplay.server.internal.AudioServer;
import com.github.serezhka.airplay.server.internal.VideoServer;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Session {

    private final String id;

    private final AirPlay airPlay;
    private final VideoServer videoServer;
    private final AudioServer audioServer;
    private final AudioControlServer audioControlServer;
    private final Map<String, ChannelHandlerContext> reverseContexts;
    private final Map<String, ChannelHandlerContext> playlistRequestContexts;

    Session(String id) {
        this.id = id;
        airPlay = new AirPlay();
        videoServer = new VideoServer(airPlay);
        audioServer = new AudioServer(airPlay);
        audioControlServer = new AudioControlServer();
        reverseContexts = new ConcurrentHashMap<>();
        playlistRequestContexts = new ConcurrentHashMap<>();
    }
    
    public String getId() {
        return id;
    }
    
    public AirPlay getAirPlay() {
        return airPlay;
    }
    
    public VideoServer getVideoServer() {
        return videoServer;
    }
    
    public AudioServer getAudioServer() {
        return audioServer;
    }
    
    public AudioControlServer getAudioControlServer() {
        return audioControlServer;
    }
    
    public Map<String, ChannelHandlerContext> getReverseContexts() {
        return reverseContexts;
    }
    
    public Map<String, ChannelHandlerContext> getPlaylistRequestContexts() {
        return playlistRequestContexts;
    }
}
