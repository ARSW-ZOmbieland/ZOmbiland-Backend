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

    @Scheduled(fixedRate = 200) // Ticks cada 200ms
    public void updateZombies() {
        moveTickCounter++;
        if (moveTickCounter > 100) moveTickCounter = 1; // Reset prevent overflow

        Set<String> activeRooms = roomManager.getAllActiveRooms();
        
        for (String roomCode : activeRooms) {
            if (roomManager.isRoomPaused(roomCode)) continue;

            List<ZombieState> zombies = roomManager.getZombiesInRoom(roomCode);
            Collection<GameActionMessage> players = roomManager.getRoomState(roomCode);
            WorldMapDTO map = roomManager.getRoomMap(roomCode);

            if (zombies.isEmpty() || players.isEmpty() || map == null) continue;

            for (ZombieState zombie : zombies) {
                // Actualizar estado visual de ataque
                long lastVisual = zombieVisualAttackTime.getOrDefault(zombie.getId(), 0L);
                zombie.setAttacking(System.currentTimeMillis() - lastVisual < 600);

                // Lógica de velocidad diferenciada
                boolean isChasqueador = "chasqueador".equals(zombie.getType());
                int moveRate = isChasqueador ? 2 : 4; // Chasqueador: 400ms, Comun: 800ms

                if (moveTickCounter % moveRate == 0) {
                    moveZombieTowardsClosestPlayer(zombie, players, map.getMatrix());
                }
                checkAndDamagePlayers(zombie, players, roomCode, map.getMatrix());
            }

            // Broadcast
            String topic = "/topic/game.zombies." + roomCode;
            messagingTemplate.convertAndSend(topic, zombies);
        }
    }

    private void moveZombieTowardsClosestPlayer(ZombieState zombie, Collection<GameActionMessage> players, int[][] matrix) {
        GameActionMessage target = null;
        double minDistance = Double.MAX_VALUE;

        // Buscar al jugador más cercano que esté vivo
        for (GameActionMessage player : players) {
            if (player.getHealth() <= 0) continue; 

            double dist = Math.abs(player.getX() - zombie.getX()) + Math.abs(player.getY() - zombie.getY());
            if (dist < minDistance) {
                minDistance = dist;
                target = player;
            }
        }

        // LÓGICA DE VISIÓN: Si no hay nadie a menos de 6 unidades, vagar aleatoriamente
        if (target == null || minDistance > 6.0) {
            performRandomWander(zombie, matrix);
            return;
        }

        double dx = target.getX() - zombie.getX();
        double dy = target.getY() - zombie.getY();

        if (dx == 0 && dy == 0) return;

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

    private void performRandomWander(ZombieState zombie, int[][] matrix) {
        // Probabilidad de moverse al azar (25% cada vez que le toca moverse)
        if (Math.random() > 0.25) return;

        int randomDir = (int)(Math.random() * 4);
        switch(randomDir) {
            case 0: tryMoveX(zombie, 1, matrix); break;  // Derecha
            case 1: tryMoveX(zombie, -1, matrix); break; // Izquierda
            case 2: tryMoveY(zombie, 1, matrix); break;  // Abajo
            case 3: tryMoveY(zombie, -1, matrix); break; // Arriba
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
            if (player.getX() == null || player.getY() == null) continue;
            if (!isWalkable(matrix, player.getX().intValue(), player.getY().intValue())) continue;

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
        if (tile == 100) return true;             // Medkit
        if (tile == 101) return true;             // Ammo Pickup
        
        return false; // Solid objects are >= 10 and not in the "walkable exceptions"
    }
}
