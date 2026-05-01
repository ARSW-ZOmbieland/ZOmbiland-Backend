const fs = require('fs');
const p = 'd:/ProyectoARSW/ZOmbiland-Backend/src/main/java/com/zombieland/backend/service/MapGenerator.java';
let c = fs.readFileSync(p, 'utf8');
c = c.replace('public WorldMapDTO generateMap() {', 'public WorldMapDTO generateMap(String mode) {');
c = c.replace('int[][] doors = new int[4][2];', 'int numDoors = "TORNEO".equals(mode) ? 4 : 2;\n        int[][] doors = new int[numDoors][2];');
c = c.replaceAll('for (int i = 0; i < 4; i++) {', 'for (int i = 0; i < numDoors; i++) {');
fs.writeFileSync(p, c);
