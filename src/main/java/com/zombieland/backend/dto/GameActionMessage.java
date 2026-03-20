package com.zombieland.backend.dto;

public class GameActionMessage {
    private String playerId;
    private String roomCode;
    private double x;
    private double y;
    private String action;

    public GameActionMessage() {}

    public GameActionMessage(String playerId, String roomCode, double x, double y, String action) {
        this.playerId = playerId;
        this.roomCode = roomCode;
        this.x = x;
        this.y = y;
        this.action = action;
    }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}
