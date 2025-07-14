package com.github.serezhka.airplay.server;


public class AirPlayConfig {
    private String serverName;
    private int width;
    private int height;
    private int fps;
    
    public AirPlayConfig() {
    }
    
    public AirPlayConfig(String serverName, int width, int height, int fps) {
        this.serverName = serverName;
        this.width = width;
        this.height = height;
        this.fps = fps;
    }
    
    public String getServerName() {
        return serverName;
    }
    
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
    
    public int getWidth() {
        return width;
    }
    
    public void setWidth(int width) {
        this.width = width;
    }
    
    public int getHeight() {
        return height;
    }
    
    public void setHeight(int height) {
        this.height = height;
    }
    
    public int getFps() {
        return fps;
    }
    
    public void setFps(int fps) {
        this.fps = fps;
    }
}
