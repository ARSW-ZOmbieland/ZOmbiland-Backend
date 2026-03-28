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
    
    // zombieId + playerId -> timestamp when pursuit/wind-up started
    private final java.util.concurrent.ConcurrentHashMap<String, Long> attackStartedAt = new java.util.concurrent.ConcurrentHashMap<>();

    private int moveTickCounter = 0;

    public ZombieAIService(RoomManager roomManager, SimpMessagingTemplate messagingTemplate) {
        this.roomManager = roomManager;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedRate = 100) // Ticks cada 100ms para mayor precisión en ataques y delay
    public void updateZombies() {
        moveTickCounter++;
        boolean shouldMove = (moveTickCounter >= 8); // Movimiento real cada 800ms
        if (shouldMove) moveTickCounter = 0;

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

                if (shouldMove) {
                    moveZombieTowardsClosestPlayer(zombie, players, map.getMatrix());
                }
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

            // Bloqueo de daño en búnker
            if (!isWalkable(matrix, (int)player.getX(), (int)player.getY())) continue;

            double dist = Math.abs(player.getX() - zombie.getX()) + Math.abs(player.getY() - zombie.getY());
            String attackKey = zombie.getId() + ":" + player.getPlayerId();

            // Si el zombie está cerca (adyacente o encima)
            if (dist <= 1.1) {
                // Iniciar contador de "wind-up" si no existe
                attackStartedAt.putIfAbsent(attackKey, now);
                long startedAt = attackStartedAt.get(attackKey);
                long lastDamage = lastAttackTime.getOrDefault(attackKey, 0L);

                // Requisito 1: Delay inicial de 0.5s antes del primer daño
                if (now - startedAt < 500) {
                    // Animación visual inmediata al empezar el wind-up
                    zombie.setAttacking(true);
                    continue; 
                }

                // Requisito 2: Intervalo de daño normal (cada 1.0s) tras el primer golpe
                if (now - lastDamage >= 1000) {
                    int currentHealth = player.getHealth();
                    if (currentHealth > 0) {
                        // Requisito 3: Daño variable (20 si está encima, 8 si está al lado)
                        int damageAmount = (dist < 0.2) ? 20 : 8;
                        int newHealth = Math.max(0, currentHealth - damageAmount);
                        
                        player.setHealth(newHealth);
                        lastAttackTime.put(attackKey, now);
                        zombieVisualAttackTime.put(zombie.getId(), now);
                        zombie.setAttacking(true);
                        
                        String stateTopic = "/topic/game.state." + roomCode;
                        messagingTemplate.convertAndSend(stateTopic, player);
                        
                        System.out.println(">> ZOMBIE ATTACK! Dist: " + dist + " Damage: " + damageAmount + " HP: " + newHealth);
                    }
                }
            } else {
                // Si el jugador se alejó, resetear el wind-up para que cuando vuelva a entrar tarde otros 0.5s
                attackStartedAt.remove(attackKey);
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
