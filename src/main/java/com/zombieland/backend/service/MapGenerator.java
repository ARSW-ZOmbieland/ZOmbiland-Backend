package com.zombieland.backend.service;

import com.zombieland.backend.dto.WorldMapDTO;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Servicio encargado de la generación procedimental del mapa del juego.
 * Crea terrenos, ciudades, campamentos y distribuye objetos (botiquines, munición) de forma aleatoria.
 */
@Service
public class MapGenerator {

    /**
     * Genera un mapa aleatorio de 64x64 celdas.
     * Incluye lógica para terrenos, vegetación, ciudades, campamentos y puertas de búnker.
     * En el modo torneo, las puertas del búnker se omiten para soportar el estilo Battle Royale.
     * 
     * @param mode El modo de juego ("TRADICIONAL" o "TORNEO").
     * @return Un objeto WorldMapDTO que contiene la matriz del mapa y la posición de inicio.
     */
    public WorldMapDTO generateMap(String mode) {
        int size = 64;
        int[][] matrix = new int[size][size];
        Random rand = new Random();

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int tile = rand.nextInt(8); 
                double chance = rand.nextDouble();
                if (chance < 0.15) {
                    tile = 20 + rand.nextInt(3); 
                } else if (chance < 0.20) {
                    tile = 30 + rand.nextInt(5); 
                }
                matrix[i][j] = tile;
            }
        }

        for (int k = 0; k < 3; k++) {
            int campX = 10 + rand.nextInt(40);
            int campY = 10 + rand.nextInt(40);

            matrix[campY][campX] = 52; 
            matrix[campY - 1][campX] = 56; 
            matrix[campY + 1][campX - 1] = 55; 
            matrix[campY + 1][campX + 1] = 54; 
            matrix[campY][campX - 1] = 50; 
            matrix[campY][campX + 1] = 53; 
            matrix[campY][campX + 2] = 57; 
            matrix[campY + 2][campX] = 51; 
        }

        for (int c = 0; c < 3; c++) {
            int cX = 15 + rand.nextInt(30);
            int cY = 15 + rand.nextInt(30);

            for (int dy = -4; dy <= 4; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    matrix[cY + dy][cX + dx] = 1; 
                }
            }

            matrix[cY][cX] = 80;           
            matrix[cY - 2][cX] = 72;       
            matrix[cY + 2][cX - 1] = 74;   
            matrix[cY - 1][cX + 1] = 70;   
            matrix[cY + 3][cX] = 73;       

            List<Integer> bTypes = new ArrayList<>(Arrays.asList(60, 61, 62, 63, 64));
            Collections.shuffle(bTypes);

            int[][] buildings = {
                {cY - 6, cX - 4}, 
                {cY, cX + 4},     
                {cY + 6, cX - 4}  
            };
            
            for(int i = 0; i < buildings.length; i++) {
                int bY = buildings[i][0], bX = buildings[i][1];
                matrix[bY][bX] = bTypes.get(i); 
                
                for (int by = 0; by <= 2; by++) {
                    for (int bx = -1; bx <= 1; bx++) {
                        if (by == 0 && bx == 0) continue; 
                        matrix[bY - by][bX + bx] = 99; 
                    }
                }
            }
        }

        for (int m = 0; m < 15; m++) {
            for (int attempts = 0; attempts < 100; attempts++) {
                int mx = rand.nextInt(size);
                int my = rand.nextInt(size);
                if (matrix[my][mx] >= 0 && matrix[my][mx] <= 7) {
                    matrix[my][mx] = 100;
                    break;
                }
            }
        }

        for (int a = 0; a < 20; a++) {
            for (int attempts = 0; attempts < 100; attempts++) {
                int mx = rand.nextInt(size);
                int my = rand.nextInt(size);
                if (matrix[my][mx] >= 0 && matrix[my][mx] <= 7) {
                    matrix[my][mx] = 101;
                    break;
                }
            }
        }

        for (int i = 0; i < size; i++) {
            matrix[0][i] = 90;           
            matrix[size - 1][i] = 90;    
            matrix[i][0] = 90;           
            matrix[i][size - 1] = 90;    
        }

        List<int[]> doorList = new ArrayList<>();
        int defaultStartX = 32;
        int defaultStartY = 32;

        if (!"TORNEO".equals(mode)) {
            int numDoors = 2;
            int[][] doors = new int[numDoors][2];
            for (int i = 0; i < numDoors; i++) {
                boolean valid;
                do {
                    valid = true;
                    doors[i][0] = rand.nextInt(size - 2) + 1;
                    doors[i][1] = rand.nextInt(size - 2) + 1;
                    for (int j = 0; j < i; j++) {
                        if (Math.abs(doors[i][0] - doors[j][0]) + Math.abs(doors[i][1] - doors[j][1]) < 30) {
                            valid = false;
                            break;
                        }
                    }
                } while (!valid);
                
                matrix[doors[i][1]][doors[i][0]] = 10; 
                doorList.add(new int[]{doors[i][0], doors[i][1]});
            }
            if (!doorList.isEmpty()) {
                defaultStartX = doorList.get(0)[0];
                defaultStartY = doorList.get(0)[1];
            }
        }

        WorldMapDTO dto = new WorldMapDTO();
        dto.setMatrix(matrix);
        dto.setStartX(defaultStartX); 
        dto.setStartY(defaultStartY);
        dto.setDoors(doorList);
        
        return dto;
    }
}
