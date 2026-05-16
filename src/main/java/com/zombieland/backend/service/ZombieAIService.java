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

/**
 * Servicio encargado de la inteligencia artificial (IA) de los zombis.
 * Controla el movimiento, el ataque y el comportamiento de los diferentes tipos de zombis en el juego.
 */
@Service
public class ZombieAIService {

    private final RoomManager roomManager;
    private final SimpMessagingTemplate messagingTemplate;
    
    /** Mapa de zombieId + playerId al último momento en que se realizó un ataque. */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> lastAttackTime = new java.util.concurrent.ConcurrentHashMap<>();
    
    /** Mapa de zombieId al último momento en que se realizó un ataque visual. */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> zombieVisualAttackTime = new java.util.concurrent.ConcurrentHashMap<>();
    
    /** Mapa de zombieId + playerId al momento en que comenzó la persecución o preparación del ataque. */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> attackStartedAt = new java.util.concurrent.ConcurrentHashMap<>();

    /** Mapa de zombieId al último momento en que se realizó un teletransporte. */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> lastTeleportTime = new java.util.concurrent.ConcurrentHashMap<>();

    /** Mapa de zombieId para rastrear si actualmente está persiguiendo a un jugador. */
    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> isPursuing = new java.util.concurrent.ConcurrentHashMap<>();

    private int moveTickCounter = 0;
    private int cleanupTickCounter = 0;

    public ZombieAIService(RoomManager roomManager, SimpMessagingTemplate messagingTemplate) {
        this.roomManager = roomManager;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Tarea programada que actualiza el estado de todos los zombis en todas las salas activas.
     * Gestiona el movimiento, los ataques y el envío de estados a los clientes.
     */
    @Scheduled(fixedRate = 350)
    public void updateZombies() {
        moveTickCounter++;
        if (moveTickCounter > 100) moveTickCounter = 1;

        Set<String> activeRooms = roomManager.getAllActiveRooms();
        
        for (String roomCode : activeRooms) {
            if (roomManager.isRoomPaused(roomCode)) continue;

            List<ZombieState> zombies = roomManager.getZombiesInRoom(roomCode);
            Collection<GameActionMessage> players = roomManager.getRoomState(roomCode);
            WorldMapDTO map = roomManager.getRoomMap(roomCode);

            if (zombies.isEmpty() || players.isEmpty() || map == null) continue;

            for (ZombieState zombie : zombies) {
                long lastVisual = zombieVisualAttackTime.getOrDefault(zombie.getId(), 0L);
                zombie.setAttacking(System.currentTimeMillis() - lastVisual < 600);

                String type = zombie.getType();
                int moveRate;
                
                if ("hunter".equals(type) || "chasqueador".equals(type)) {
                    moveRate = 2; 
                } else if ("tanke".equals(type)) {
                    moveRate = 5; 
                } else {
                    moveRate = 4; 
                }

                if (moveTickCounter % moveRate == 0) {
                    moveZombieTowardsClosestPlayer(zombie, players, map.getMatrix());
                }
                checkAndDamagePlayers(zombie, players, roomCode, map.getMatrix());
            }

            String topic = "/topic/game.zombies." + roomCode;
            messagingTemplate.convertAndSend(topic, zombies);
        }

        cleanupTickCounter++;
        if (cleanupTickCounter >= 20) {
            cleanupTickCounter = 0;
            performGlobalCleanup();
        }
    }

    /**
     * Realiza una limpieza global de los datos de seguimiento obsoletos para evitar fugas de memoria.
     * Elimina entradas de zombis que ya no están activos en ninguna sala.
     */
    private void performGlobalCleanup() {
        Set<String> activeZombieIds = new java.util.HashSet<>();
        Set<String> activeRooms = roomManager.getAllActiveRooms();
        
        for (String roomCode : activeRooms) {
            List<ZombieState> zombies = roomManager.getZombiesInRoom(roomCode);
            for (ZombieState z : zombies) {
                activeZombieIds.add(z.getId());
            }
        }

        lastAttackTime.keySet().removeIf(key -> !activeZombieIds.contains(key.split(":")[0]));
        zombieVisualAttackTime.keySet().removeIf(id -> !activeZombieIds.contains(id));
        attackStartedAt.keySet().removeIf(key -> !activeZombieIds.contains(key.split(":")[0]));
        lastTeleportTime.keySet().removeIf(id -> !activeZombieIds.contains(id));
        isPursuing.keySet().removeIf(id -> !activeZombieIds.contains(id));
    }

    /**
     * Mueve un zombi hacia el jugador vivo más cercano.
     * Implementa lógica diferenciada según el tipo de zombi (visión, teletransporte, persecución).
     * 
     * @param zombie El zombi que se va a mover.
     * @param players La colección de jugadores en la sala.
     * @param matrix La matriz del mapa para verificar colisiones.
     */
    private void moveZombieTowardsClosestPlayer(ZombieState zombie, Collection<GameActionMessage> players, int[][] matrix) {
        GameActionMessage target = null;
        double minDistance = Double.MAX_VALUE;

        for (GameActionMessage player : players) {
            if (player.getHealth() <= 0) continue; 

            double dist = Math.abs(player.getX() - zombie.getX()) + Math.abs(player.getY() - zombie.getY());
            if (dist < minDistance) {
                minDistance = dist;
                target = player;
            }
        }

        String type = zombie.getType();
        boolean isHunter = "hunter".equals(type);

        if (target == null) {
            performRandomWander(zombie, matrix);
            return;
        }

        if (isHunter && minDistance <= 6.0) {
            long now = System.currentTimeMillis();
            long lastTeleport = lastTeleportTime.getOrDefault(zombie.getId(), 0L);
            
            if (now - lastTeleport >= 3000) {
                zombie.setX(target.getX());
                zombie.setY(target.getY());
                zombie.setDirection("abajo"); 
                lastTeleportTime.put(zombie.getId(), now);
                return; 
            }
        }

        boolean isGlobalFollower = "comun".equals(type) || "tanke".equals(type);
        boolean pursuing = isPursuing.getOrDefault(zombie.getId(), false);

        if (!isGlobalFollower && !pursuing) {
            if (minDistance <= 6.0) {
                isPursuing.put(zombie.getId(), true);
            } else {
                performRandomWander(zombie, matrix);
                return;
            }
        }

        if (!isGlobalFollower && minDistance > 8.0) {
            isPursuing.put(zombie.getId(), false);
            performRandomWander(zombie, matrix);
            return;
        }

        double dx = target.getX() - zombie.getX();
        double dy = target.getY() - zombie.getY();

        if (dx == 0 && dy == 0) return;

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

    /**
     * Realiza un movimiento aleatorio para el zombi.
     * 
     * @param zombie El zombi que se va a mover.
     * @param matrix La matriz del mapa para verificar colisiones.
     */
    private void performRandomWander(ZombieState zombie, int[][] matrix) {
        if (Math.random() > 0.25) return;

        int randomDir = (int)(Math.random() * 4);
        switch(randomDir) {
            case 0: tryMoveX(zombie, 1, matrix); break;
            case 1: tryMoveX(zombie, -1, matrix); break;
            case 2: tryMoveY(zombie, 1, matrix); break;
            case 3: tryMoveY(zombie, -1, matrix); break;
        }
    }

    /**
     * Intenta mover al zombi en el eje X.
     * 
     * @param zombie El zombi que se va a mover.
     * @param dx El desplazamiento en el eje X.
     * @param matrix La matriz del mapa para verificar colisiones.
     * @return true si el movimiento fue exitoso, false en caso contrario.
     */
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

    /**
     * Intenta mover al zombi en el eje Y.
     * 
     * @param zombie El zombi que se va a mover.
     * @param dy El desplazamiento en el eje Y.
     * @param matrix La matriz del mapa para verificar colisiones.
     * @return true si el movimiento fue exitoso, false en caso contrario.
     */
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

    /**
     * Verifica la proximidad de los jugadores al zombi y aplica daño si es necesario.
     * Implementa lógica de preparación de ataque (wind-up) y daño variable según el tipo de zombi.
     * 
     * @param zombie El zombi que ataca.
     * @param players La colección de jugadores en la sala.
     * @param roomCode El código de la sala.
     * @param matrix La matriz del mapa para verificar transitabilidad.
     */
    private void checkAndDamagePlayers(ZombieState zombie, Collection<GameActionMessage> players, String roomCode, int[][] matrix) {
        long now = System.currentTimeMillis();
        for (GameActionMessage player : players) {
            if (player.getHealth() <= 0) continue; 

            if (player.getX() == null || player.getY() == null) continue;
            if (!isWalkable(matrix, player.getX().intValue(), player.getY().intValue())) continue;

            double dist = Math.abs(player.getX() - zombie.getX()) + Math.abs(player.getY() - zombie.getY());
            String attackKey = zombie.getId() + ":" + player.getPlayerId();

            if (dist <= 1.1) {
                attackStartedAt.putIfAbsent(attackKey, now);
                long startedAt = attackStartedAt.get(attackKey);
                long lastDamage = lastAttackTime.getOrDefault(attackKey, 0L);

                if (now - startedAt < 500) {
                    zombie.setAttacking(true);
                    continue; 
                }

                if (now - lastDamage >= 1000) {
                    int currentHealth = player.getHealth();
                    if (currentHealth > 0) {
                        String type = zombie.getType();
                        int damageAmount;

                        if ("tanke".equals(type)) {
                            damageAmount = 50; 
                        } else if ("hunter".equals(type)) {
                            damageAmount = (dist < 0.2) ? 15 : 5;

                        } else {
                            damageAmount = (dist < 0.2) ? 20 : 8; 
                        }

                        int newHealth = Math.max(0, currentHealth - damageAmount);
                        
                        player.setHealth(newHealth);
                        lastAttackTime.put(attackKey, now);
                        zombieVisualAttackTime.put(zombie.getId(), now);
                        zombie.setAttacking(true);
                        
                        String stateTopic = "/topic/game.state." + roomCode;
                        messagingTemplate.convertAndSend(stateTopic, player);
                    }
                }
            } else {
                attackStartedAt.remove(attackKey);
            }
        }
    }

    /**
     * Verifica si una posición en el mapa es transitable.
     * Sincronizado lógicamente con el motor de juego del frontend.
     * 
     * @param matrix La matriz del mapa.
     * @param x La coordenada X.
     * @param y La coordenada Y.
     * @return true si la posición es transitable, false en caso contrario.
     */
    private boolean isWalkable(int[][] matrix, int x, int y) {
        if (y < 0 || y >= matrix.length || x < 0 || x >= matrix[0].length) return false;
        int tile = matrix[y][x];
        
        if (tile >= 0 && tile <= 7) return true;  
        if (tile >= 20 && tile <= 22) return true; 
        if (tile == 72) return true;              
        if (tile == 100) return true;             
        if (tile == 101) return true;             
        
        return false; 
    }
}
