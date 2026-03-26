package com.zombieland.backend.dto;

public class WorldMapDTO {
    private int[][] matrix;
    private int startX;
    private int startY;
    private int endX;
    private int endY;

    public WorldMapDTO() {
    }

    public WorldMapDTO(int[][] matrix, int startX, int startY, int endX, int endY) {
        this.matrix = matrix;
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
    }

    public int[][] getMatrix() {
        return matrix;
    }

    public void setMatrix(int[][] matrix) {
        this.matrix = matrix;
    }

    public int getStartX() {
        return startX;
    }

    public void setStartX(int startX) {
        this.startX = startX;
    }

    public int getStartY() {
        return startY;
    }

    public void setStartY(int startY) {
        this.startY = startY;
    }

    public int getEndX() {
        return endX;
    }

    public void setEndX(int endX) {
        this.endX = endX;
    }

    public int getEndY() {
        return endY;
    }

    public void setEndY(int endY) {
        this.endY = endY;
    }
}
