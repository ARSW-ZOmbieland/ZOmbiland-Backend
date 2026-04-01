package com.zombieland.backend.dto;

public class GameActionMessage {
    private String playerId;
    private String roomCode;
    private double x;
    private double y;
    private String action;
    private int health = 100;
    private double targetX;
    private double targetY;
    private double aimAngle;

    public GameActionMessage() {}

    public GameActionMessage(String playerId, String roomCode, double x, double y, String action) {
        this.playerId = playerId;
        this.roomCode = roomCode;
        this.x = x;
        this.y = y;
        this.action = action;
        this.health = 100;
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
    public int getHealth() { return health; }
    public void setHealth(int health) { this.health = health; }
    public double getTargetX() { return targetX; }
    public void setTargetX(double targetX) { this.targetX = targetX; }
    public double getTargetY() { return targetY; }
    public void setTargetY(double targetY) { this.targetY = targetY; }
    public double getAimAngle() { return aimAngle; }
    public void setAimAngle(double aimAngle) { this.aimAngle = aimAngle; }
}
