package com.zombieland.backend.service;

import com.zombieland.backend.dto.WorldMapDTO;
import org.springframework.stereotype.Service;

import java.util.*;

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
                int tile = rand.nextInt(8); // background ground tile equivalent to 0
                double chance = rand.nextDouble();
                // 15% chance to feature a bush instead of empty ground
                if (chance < 0.15) {
                    tile = 20 + rand.nextInt(3); // 20, 21, or 22
                } else if (chance < 0.20) {
                    // 5% chance to feature a tree
                    tile = 30 + rand.nextInt(5); // 30, 31, 32, 33, or 34
                }
                matrix[i][j] = tile;
            }
        }

        // Place 3 completely random grouped campsite configurations away from edges
        for (int k = 0; k < 3; k++) {
            int campX = 10 + rand.nextInt(40);
            int campY = 10 + rand.nextInt(40);

            matrix[campY][campX] = 52; // campfire_dead
            matrix[campY - 1][campX] = 56; // tent_destroyed
            matrix[campY + 1][campX - 1] = 55; // log_large
            matrix[campY + 1][campX + 1] = 54; // log_fallen
            matrix[campY][campX - 1] = 50; // backpack_loot
            matrix[campY][campX + 1] = 53; // forest_supply_box
            matrix[campY][campX + 2] = 57; // wood_stump
            matrix[campY + 2][campX] = 51; // branch_pile
        }

        // Place 3 procedural cities
        for (int c = 0; c < 3; c++) {
            int cX = 15 + rand.nextInt(30);
            int cY = 15 + rand.nextInt(30);

            // Carreterita (Road)
            for (int dy = -4; dy <= 4; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    matrix[cY + dy][cX + dx] = 1; // Basic stone road texture underneath
                }
            }

            // VFX and urban props on the road
            matrix[cY][cX] = 80;           // barrilfuego.gif
            matrix[cY - 2][cX] = 72;       // street_light
            matrix[cY + 2][cX - 1] = 74;   // traffic_cone
            matrix[cY - 1][cX + 1] = 70;   // crate_01
            matrix[cY + 3][cX] = 73;       // supply_box

            // Spaced out buildings with unique types per city
            List<Integer> bTypes = new ArrayList<>(Arrays.asList(60, 61, 62, 63, 64));
            Collections.shuffle(bTypes);

            int[][] buildings = {
                {cY - 6, cX - 4}, // Left
                {cY, cX + 4},     // Right
                {cY + 6, cX - 4}  // Left
            };
            
            for(int i = 0; i < buildings.length; i++) {
                int bY = buildings[i][0], bX = buildings[i][1];
                matrix[bY][bX] = bTypes.get(i); // Each building is unique
                
                // Add Invisible Solid Blocks (99) to simulate the 3x3 footprint 
                for (int by = 0; by <= 2; by++) {
                    for (int bx = -1; bx <= 1; bx++) {
                        if (by == 0 && bx == 0) continue; 
                        matrix[bY - by][bX + bx] = 99; 
                    }
                }
            }
        }

        // Place 15 random Medkits (ID 100) on walkable ground
        for (int m = 0; m < 15; m++) {
            boolean found = false;
            for (int attempts = 0; attempts < 100; attempts++) {
                int mx = rand.nextInt(size);
                int my = rand.nextInt(size);
                if (matrix[my][mx] >= 0 && matrix[my][mx] <= 7) {
                    matrix[my][mx] = 100;
                    found = true;
                    break;
                }
            }
        }

        // Place 20 random Ammo Pickups (ID 101) on walkable ground
        for (int a = 0; a < 20; a++) {
            boolean found = false;
            for (int attempts = 0; attempts < 100; attempts++) {
                int mx = rand.nextInt(size);
                int my = rand.nextInt(size);
                if (matrix[my][mx] >= 0 && matrix[my][mx] <= 7) {
                    matrix[my][mx] = 101;
                    found = true;
                    break;
                }
            }
        }

        // Frame the edges of the map with Police Barricades (ID 90)
        for (int i = 0; i < size; i++) {
            matrix[0][i] = 90;           // Top edge
            matrix[size - 1][i] = 90;    // Bottom edge
            matrix[i][0] = 90;           // Left edge
            matrix[i][size - 1] = 90;    // Right edge
        }

        // Pick 4 door points ensuring they are far apart
        int[][] doors = new int[4][2];
        for (int i = 0; i < 4; i++) {
            boolean valid;
            do {
                valid = true;
                doors[i][0] = rand.nextInt(size - 2) + 1; // X (avoid edges)
                doors[i][1] = rand.nextInt(size - 2) + 1; // Y (avoid edges)
                // Check distance with previously placed doors
                for (int j = 0; j < i; j++) {
                    if (Math.abs(doors[i][0] - doors[j][0]) + Math.abs(doors[i][1] - doors[j][1]) < 30) {
                        valid = false;
                        break;
                    }
                }
            } while (!valid);
            
            // Place bunker door
            matrix[doors[i][1]][doors[i][0]] = 10;
        }

        WorldMapDTO dto = new WorldMapDTO();
        dto.setMatrix(matrix);
        // Start is doors[0], End is doors[1] for backward compatibility
        dto.setStartX(doors[0][0]);
        dto.setStartY(doors[0][1]);
        
        // Add all 4 doors to the DTO
        List<int[]> doorList = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            doorList.add(new int[]{doors[i][0], doors[i][1]});
        }
        dto.setDoors(doorList);
        
        return dto;
    }
}
