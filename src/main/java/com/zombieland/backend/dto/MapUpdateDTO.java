package com.zombieland.backend.dto;

public class MapUpdateDTO {
    private int x;
    private int y;
    private int tile;

    public MapUpdateDTO() {}

    public MapUpdateDTO(int x, int y, int tile) {
        this.x = x;
        this.y = y;
        this.tile = tile;
    }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
    public int getY() { return y; }
    public void setY(int y) { this.y = y; }
    public int getTile() { return tile; }
    public void setTile(int tile) { this.tile = tile; }
}
