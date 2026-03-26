package com.zombieland.backend.service;

import com.zombieland.backend.dto.WorldMapDTO;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class MapGenerator {

    /**
     * Generates a 64x64 random map with tiles 0-7 and two distant bunker doors (10).
     */
    public WorldMapDTO generateMap() {
        int size = 64;
        int[][] matrix = new int[size][size];
        Random rand = new Random();

        // Fill with random ground tiles (0-7)
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrix[i][j] = rand.nextInt(8);
            }
        }

        // Pick start and end points ensuring they are far apart
        int startX, startY, endX, endY;
        do {
            startX = rand.nextInt(size);
            startY = rand.nextInt(size);
            endX = rand.nextInt(size);
            endY = rand.nextInt(size);
        } while (Math.abs(startX - endX) + Math.abs(startY - endY) < 64); // Manhattan distance >= 64

        // Place bunker doors
        matrix[startY][startX] = 10;
        matrix[endY][endX] = 10;

        return new WorldMapDTO(matrix, startX, startY, endX, endY);
    }
}
