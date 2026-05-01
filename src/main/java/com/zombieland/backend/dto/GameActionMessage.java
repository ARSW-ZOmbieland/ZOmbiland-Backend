package com.zombieland.backend.dto;

public class GameActionMessage {
    private String playerId;
    private String roomCode;
    private Double x;
    private Double y;
    private String action;
    private int health = 100;
    private Double targetX;
    private Double targetY;
    private Double aimAngle;
    private int ammo = 30;
    private String location = "world";
    private boolean paralyzed = false;

    public GameActionMessage() {}

    public GameActionMessage(String playerId, String roomCode, Double x, Double y, String action) {
        this.playerId = playerId;
        this.roomCode = roomCode;
        this.x = x;
        this.y = y;
        this.action = action;
        this.health = 100;
        this.ammo = 30;
        this.location = "world";
    }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
    public Double getX() { return x; }
    public void setX(Double x) { this.x = x; }
    public Double getY() { return y; }
    public void setY(Double y) { this.y = y; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public int getHealth() { return health; }
    public void setHealth(int health) { this.health = health; }
    public Double getTargetX() { return targetX; }
    public void setTargetX(Double targetX) { this.targetX = targetX; }
    public Double getTargetY() { return targetY; }
    public void setTargetY(Double targetY) { this.targetY = targetY; }
    public Double getAimAngle() { return aimAngle; }
    public void setAimAngle(Double aimAngle) { this.aimAngle = aimAngle; }
    public int getAmmo() { return ammo; }
    public void setAmmo(int ammo) { this.ammo = ammo; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public boolean isParalyzed() { return paralyzed; }
    public void setParalyzed(boolean paralyzed) { this.paralyzed = paralyzed; }
}
