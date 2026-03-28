package com.zombieland.backend.service;

import com.zombieland.backend.dto.GameActionMessage;
import com.zombieland.backend.dto.WorldMapDTO;
import com.zombieland.backend.dto.ZombieState;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Service
public class ZombieAIService {

    private final RoomManager roomManager;
    private final SimpMessagingTemplate messagingTemplate;
    
    // zombieId + playerId -> lastAttackTimestamp
    private final java.util.concurrent.ConcurrentHashMap<String, Long> lastAttackTime = new java.util.concurrent.ConcurrentHashMap<>();
    
    // zombieId -> lastVisualAttackTimestamp
    private final java.util.concurrent.ConcurrentHashMap<String, Long> zombieVisualAttackTime = new java.util.concurrent.ConcurrentHashMap<>();

    public ZombieAIService(RoomManager roomManager, SimpMessagingTemplate messagingTemplate) {
        this.roomManager = roomManager;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedRate = 800) // Movimiento cada 0.8 segundos (antes 0.5)
    public void updateZombies() {
        Set<String> activeRooms = roomManager.getAllActiveRooms();
        
        for (String roomCode : activeRooms) {
            List<ZombieState> zombies = roomManager.getZombiesInRoom(roomCode);
            Collection<GameActionMessage> players = roomManager.getRoomState(roomCode);
            WorldMapDTO map = roomManager.getRoomMap(roomCode);

            if (zombies.isEmpty() || players.isEmpty() || map == null) continue;

            for (ZombieState zombie : zombies) {
                // Actualizar estado visual de ataque (resetear tras 600ms)
                long lastVisual = zombieVisualAttackTime.getOrDefault(zombie.getId(), 0L);
                zombie.setAttacking(System.currentTimeMillis() - lastVisual < 600);

                moveZombieTowardsClosestPlayer(zombie, players, map.getMatrix());
                checkAndDamagePlayers(zombie, players, roomCode, map.getMatrix());
            }

            // Broadcast the new zombie positions to the room
            String topic = "/topic/game.zombies." + roomCode;
            messagingTemplate.convertAndSend(topic, zombies);
        }
    }

    private void moveZombieTowardsClosestPlayer(ZombieState zombie, Collection<GameActionMessage> players, int[][] matrix) {
        GameActionMessage target = null;
        double minDistance = Double.MAX_VALUE;

        // Find closest player that is alive
        for (GameActionMessage player : players) {
            if (player.getHealth() <= 0) continue; // Ignorar muertos

            double dist = Math.abs(player.getX() - zombie.getX()) + Math.abs(player.getY() - zombie.getY());
            if (dist < minDistance) {
                minDistance = dist;
                target = player;
            }
        }

        if (target == null) return;

        double dx = target.getX() - zombie.getX();
        double dy = target.getY() - zombie.getY();

        if (dx == 0 && dy == 0) return;

        double nextX = zombie.getX();
        double nextY = zombie.getY();
        String newDir = zombie.getDirection();

        // Try to move on the axis with the largest distance first (Greedy)
        if (Math.abs(dx) > Math.abs(dy)) {
            if (!tryMoveX(zombie, dx, matrix)) {
                tryMoveY(zombie, dy, matrix);
            }
        } else {
            if (!tryMoveY(zombie, dy, matrix)) {
                tryMoveX(zombie, dx, matrix);
            }
        }
    }

    private boolean tryMoveX(ZombieState zombie, double dx, int[][] matrix) {
        int stepX = dx > 0 ? 1 : -1;
        int nextX = (int) zombie.getX() + stepX;
        int currentY = (int) zombie.getY();

        if (isWalkable(matrix, nextX, currentY)) {
            zombie.setX(nextX);
            zombie.setDirection(dx > 0 ? "derecha" : "izquierda");
            return true;
        }
        return false;
    }

    private boolean tryMoveY(ZombieState zombie, double dy, int[][] matrix) {
        int stepY = dy > 0 ? 1 : -1;
        int currentX = (int) zombie.getX();
        int nextY = (int) zombie.getY() + stepY;

        if (isWalkable(matrix, currentX, nextY)) {
            zombie.setY(nextY);
            zombie.setDirection(dy > 0 ? "abajo" : "arriba");
            return true;
        }
        return false;
    }

    private void checkAndDamagePlayers(ZombieState zombie, Collection<GameActionMessage> players, String roomCode, int[][] matrix) {
        long now = System.currentTimeMillis();
        for (GameActionMessage player : players) {
            if (player.getHealth() <= 0) continue; // No dañar muertos

            // Bloqueo de daño en búnker: si el jugador está en una zona no caminable para el zombie (como muros o búnker)
            if (!isWalkable(matrix, (int)player.getX(), (int)player.getY())) continue;

            double dist = Math.abs(player.getX() - zombie.getX()) + Math.abs(player.getY() - zombie.getY());
            
            // Si el zombie está adyacente (distancia <= 1.1 para margen de error de floats)
            if (dist <= 1.1) {
                String attackKey = zombie.getId() + ":" + player.getPlayerId();
                long lastAttack = lastAttackTime.getOrDefault(attackKey, 0L);
                
                if (now - lastAttack >= 1000) { // 1 ataque por segundo solicitado por el usuario
                    int currentHealth = player.getHealth();
                    if (currentHealth > 0) {
                        int newHealth = Math.max(0, currentHealth - 10);
                        player.setHealth(newHealth);
                        lastAttackTime.put(attackKey, now);
                        zombieVisualAttackTime.put(zombie.getId(), now);
                        zombie.setAttacking(true);
                        String stateTopic = "/topic/game.state." + roomCode;
                        messagingTemplate.convertAndSend(stateTopic, player);
                        
                        System.out.println(">> ZOMBIE ATTACK! " + zombie.getId() + " bit " + player.getPlayerId() + " HP: " + newHealth);
                    }
                }
            }
        }
    }

    private boolean isWalkable(int[][] matrix, int x, int y) {
        if (y < 0 || y >= matrix.length || x < 0 || x >= matrix[0].length) return false;
        int tile = matrix[y][x];
        
        // Logical sync with GameEngine.js (Frontend)
        if (tile >= 0 && tile <= 7) return true;  // Ground
        if (tile >= 20 && tile <= 22) return true; // Bushes
        if (tile == 72) return true;              // Street lights
        
        return false; // Solid objects are >= 10 and not in the "walkable exceptions"
    }
}
