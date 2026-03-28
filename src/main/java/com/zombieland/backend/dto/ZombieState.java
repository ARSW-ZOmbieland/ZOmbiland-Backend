package com.zombieland.backend.dto;

public class ZombieState {
    private String id;
    private double x;
    private double y;
    private String direction;
    private boolean attacking;

    public ZombieState() {}

    public ZombieState(String id, double x, double y, String direction) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.direction = direction;
        this.attacking = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public boolean isAttacking() { return attacking; }
    public void setAttacking(boolean attacking) { this.attacking = attacking; }
}
