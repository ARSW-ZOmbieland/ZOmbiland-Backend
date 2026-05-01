package com.zombieland.backend.service;

import com.zombieland.backend.dto.GameActionMessage;
import com.zombieland.backend.dto.WorldMapDTO;
import com.zombieland.backend.dto.ZombieState;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomManager {
    // roomCode -> WorldMapDTO
    private final ConcurrentHashMap<String, WorldMapDTO> roomMaps = new ConcurrentHashMap<>();
    
    private final MapGenerator mapGenerator;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    public RoomManager(MapGenerator mapGenerator, org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate) {
        this.mapGenerator = mapGenerator;
        this.messagingTemplate = messagingTemplate;
    }

    // roomCode -> (playerId -> GameActionMessage)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, GameActionMessage>> roomPlayers = new ConcurrentHashMap<>();
    
    // roomCode -> List of Zombies
    private final ConcurrentHashMap<String, List<ZombieState>> roomZombies = new ConcurrentHashMap<>();
    
    // sessionId -> PlayerSessionInfo
    private final ConcurrentHashMap<String, PlayerSessionInfo> sessionTrackers = new ConcurrentHashMap<>();
    
    // roomCode -> Boolean (isPaused)
    private final ConcurrentHashMap<String, Boolean> roomPausedStates = new ConcurrentHashMap<>();
    
    // Explicitly generated room codes
    private final Set<String> validRooms = ConcurrentHashMap.newKeySet();

    // roomCode:playerId -> Timestamp of death
    private final ConcurrentHashMap<String, Long> deathTimers = new ConcurrentHashMap<>();

    public void createRoom(String roomCode) {
        String code = roomCode.toUpperCase();
        validRooms.add(code);
        roomPlayers.putIfAbsent(code, new ConcurrentHashMap<>());
        WorldMapDTO map = mapGenerator.generateMap();
        roomMaps.putIfAbsent(code, map);
        
        // Spawn 20 Zombies distributed across the 64x64 map
        List<ZombieState> zombies = new ArrayList<>();
        Random rand = new Random();
        int[][] matrix = map.getMatrix();
        
        for (int i = 1; i <= 40; i++) {
            int rx = 0, ry = 0;
            boolean found = false;
            // Seek a walkable position (Ground tiles 0-7)
            for (int attempts = 0; attempts < 100; attempts++) {
                int tx = rand.nextInt(matrix[0].length);
                int ty = rand.nextInt(matrix.length);
                int tile = matrix[ty][tx];
                if (tile >= 0 && tile <= 7) {
                    rx = tx;
                    ry = ty;
                    found = true;
                    break;
                }
            }
            // Fallback to start position if no random walkable found (unlikely)
            if (!found) {
                rx = (int)map.getStartX() + (i % 3);
                ry = (int)map.getStartY() + (i / 3);
            }
            
            // Distribución equilibrada de tipos
            String type;
            int chance = rand.nextInt(100);
            if (chance < 30) {
                type = "comun";
            } else if (chance < 50) {
                type = "chasqueador";
            } else if (chance < 70) {
                type = "tanke";
            } else if (chance < 85) {
                type = "hunter";
            } else {
                type = "llorona";
            }
            ZombieState newZombie = new ZombieState("zombie-" + i, rx, ry, "abajo", type);
            if ("tanke".equals(type)) {
                newZombie.setHealth(680); // 20 disparos de 34 cada uno
            }
            zombies.add(newZombie);
        }
        roomZombies.put(code, zombies);
    }

    public WorldMapDTO getRoomMap(String roomCode) {
        return roomMaps.get(roomCode.toUpperCase());
    }

    public boolean roomExists(String roomCode) {
        return validRooms.contains(roomCode.toUpperCase());
    }

    public static class PlayerSessionInfo {
        public String roomCode;
        public String playerId;
        public PlayerSessionInfo(String roomCode, String playerId) {
            this.roomCode = roomCode;
            this.playerId = playerId;
        }
    }

    public synchronized boolean joinRoom(GameActionMessage message, String sessionId) {
        String roomCode = message.getRoomCode().toUpperCase();
        String playerId = message.getPlayerId();
        
        if (!validRooms.contains(roomCode)) {
            return false;
        }
        
        roomPlayers.putIfAbsent(roomCode, new ConcurrentHashMap<>());
        Map<String, GameActionMessage> players = roomPlayers.get(roomCode);
        
        // Check if room is full (max 4)
        if (players.size() >= 4 && !players.containsKey(playerId)) {
            return false; 
        }
        
        players.put(playerId, message);
        sessionTrackers.put(sessionId, new PlayerSessionInfo(roomCode, playerId));
        return true;
    }

    public void updatePlayerState(GameActionMessage message) {
        String roomCode = message.getRoomCode().toUpperCase();
        
        // Manejar Pausa Global desde el servidor
        if ("PAUSE".equals(message.getAction())) {
            roomPausedStates.put(roomCode, true);
        } else if ("RESUME".equals(message.getAction())) {
            roomPausedStates.put(roomCode, false);
        }

        if (roomPlayers.containsKey(roomCode)) {
            Map<String, GameActionMessage> players = roomPlayers.get(roomCode);
            GameActionMessage existing = players.get(message.getPlayerId());
            if (existing != null) {
                // Si el mensaje es parcial (como PAUSE), preservar la posición anterior
                if (message.getX() == null) message.setX(existing.getX());
                if (message.getY() == null) message.setY(existing.getY());
                if (message.getAimAngle() == null) message.setAimAngle(existing.getAimAngle());
                
                // Preserve server state (health, ammo, location)
                message.setHealth(existing.getHealth());
                message.setAmmo(existing.getAmmo());
                if (message.getLocation() == null) message.setLocation(existing.getLocation());
                
                // --- ITEM COLLECTION CHECK ---
                WorldMapDTO map = roomMaps.get(roomCode);
                if (map != null && message.getX() != null && message.getY() != null) {
                    int px = message.getX().intValue();
                    int py = message.getY().intValue();
                    int[][] matrix = map.getMatrix();
                    
                    if (py >= 0 && py < matrix.length && px >= 0 && px < matrix[0].length) {
                        int tile = matrix[py][px];
                        if (tile == 100) { // Medkit
                            System.out.println(">> PLAYER COLLECTED MEDKIT: " + message.getPlayerId());
                            matrix[py][px] = 0; // Clear tile (back to ground)
                            message.setHealth(100); // HEAL FULL
                            
                            // Broadcast health update
                            String stateTopic = "/topic/game.state." + roomCode;
                            messagingTemplate.convertAndSend(stateTopic, message);
                            
                            // Broadcast map update (clear old)
                            String mapUpdateTopic = "/topic/game.map." + roomCode;
                            messagingTemplate.convertAndSend(mapUpdateTopic, new com.zombieland.backend.dto.MapUpdateDTO(px, py, 0));
                            
                            // Respawn Medkit in new location
                            respawnItem(roomCode, 100);
                        } else if (tile == 101) { // Ammo Pickup
                            System.out.println(">> PLAYER COLLECTED AMMO: " + message.getPlayerId());
                            matrix[py][px] = 0; // Clear tile
                            message.setAmmo(message.getAmmo() + 30); // Add 30 bullets
                            
                            // Broadcast ammo update
                            String stateTopic = "/topic/game.state." + roomCode;
                            messagingTemplate.convertAndSend(stateTopic, message);
                            
                            // Broadcast map update (clear old)
                            String mapUpdateTopic = "/topic/game.map." + roomCode;
                            messagingTemplate.convertAndSend(mapUpdateTopic, new com.zombieland.backend.dto.MapUpdateDTO(px, py, 0));
                            
                            // Respawn Ammo in new location
                            respawnItem(roomCode, 101);
                        }
                    }
                }
                
                players.put(message.getPlayerId(), message);
            }
        }
    }

    public boolean isRoomPaused(String roomCode) {
        return roomPausedStates.getOrDefault(roomCode.toUpperCase(), false);
    }

    public synchronized String leaveRoomBySession(String sessionId) {
        PlayerSessionInfo info = sessionTrackers.remove(sessionId);
        if (info != null) {
            Map<String, GameActionMessage> players = roomPlayers.get(info.roomCode);
            if (players != null) {
                players.remove(info.playerId);
                if (players.isEmpty()) {
                    roomPlayers.remove(info.roomCode);
                }
            }
            return info.roomCode;
        }
        return null;
    }

    public Set<String> getPlayersInRoom(String roomCode) {
        if (!roomPlayers.containsKey(roomCode)) return Collections.emptySet();
        return roomPlayers.get(roomCode).keySet();
    }

    public Collection<GameActionMessage> getRoomState(String roomCode) {
        if (!roomPlayers.containsKey(roomCode)) return Collections.emptyList();
        return roomPlayers.get(roomCode).values();
    }

    public List<ZombieState> getZombiesInRoom(String roomCode) {
        return roomZombies.getOrDefault(roomCode.toUpperCase(), Collections.emptyList());
    }

    public synchronized void handleAttack(GameActionMessage message) {
        String roomCode = message.getRoomCode().toUpperCase();
        
        // 0. Check Ammo from server state
        Map<String, GameActionMessage> players = roomPlayers.get(roomCode);
        if (players == null) return;
        GameActionMessage serverState = players.get(message.getPlayerId());
        if (serverState == null || serverState.getAmmo() <= 0) {
            System.out.println(">> ATTACK BLOCKED: No ammo for " + message.getPlayerId());
            message.setAmmo(0); // Sync client to 0
            message.setAction("NO_AMMO"); // Change action to prevent animation
            return;
        }

        // 1. Consume 1 Bullet
        serverState.setAmmo(serverState.getAmmo() - 1);
        message.setAmmo(serverState.getAmmo()); // Send back updated ammo
        message.setHealth(serverState.getHealth()); // Maintain server health

        List<ZombieState> zombies = roomZombies.get(roomCode);
        if (zombies == null) return;

        double tx = message.getTargetX();
        double ty = message.getTargetY();
        double px = message.getX();
        double py = message.getY();

        // 1. Calculate direction vector from player to target
        double dx = tx - px;
        double dy = ty - py;
        double magnitude = Math.sqrt(dx * dx + dy * dy);
        if (magnitude < 0.01) return; // Prevent target being exactly on player

        // 2. Snap to 8-directions (45 deg increments)
        double angle = Math.atan2(dy, dx);
        double snappedAngle = Math.round(angle / (Math.PI / 4.0)) * (Math.PI / 4.0);
        double vx = Math.cos(snappedAngle);
        double vy = Math.sin(snappedAngle);

        // 3. Find the CLOSEST zombie along the ray within range 6.0 (Single Target)
        ZombieState targetZombie = null;
        double closestDist = 7.0; // Max range is 6.0

        for (ZombieState zombie : zombies) {
            double zx = zombie.getX() - px;
            double zy = zombie.getY() - py;
            
            // Projection length L onto the normalized ray
            double dot = zx * vx + zy * vy;
            
            // Perpendicular squared distance D^2 = |vz|^2 - L^2
            double distSq = (zx * zx + zy * zy) - (dot * dot);
            
            // Hit if: in front of player, within distance 6.0, and close to line (distSq < 0.2)
            if (dot > 0 && dot < 6.0 && distSq < 0.2) { 
                if (dot < closestDist) {
                    closestDist = dot;
                    targetZombie = zombie;
                }
            }
        }

        // 4. Apply damage only to the closest target
        if (targetZombie != null) {
            targetZombie.setHealth(targetZombie.getHealth() - 34); 
            if (targetZombie.getHealth() <= 0) {
                zombies.remove(targetZombie);
            }
        }
    }

    public Set<String> getAllActiveRooms() {
        return roomPlayers.keySet();
    }

    private void respawnItem(String roomCode, int itemType) {
        WorldMapDTO map = roomMaps.get(roomCode);
        if (map == null) return;
        int[][] matrix = map.getMatrix();
        int size = matrix.length;
        java.util.Random rand = new java.util.Random();
        
        for (int attempts = 0; attempts < 100; attempts++) {
            int nx = rand.nextInt(size);
            int ny = rand.nextInt(size);
            // Check if it's walkable ground (0-7) and not already an item
            if (ny >= 0 && ny < size && nx >= 0 && nx < size && matrix[ny][nx] >= 0 && matrix[ny][nx] <= 7) {
                matrix[ny][nx] = itemType;
                
                // Broadcast new map update
                String mapUpdateTopic = "/topic/game.map." + roomCode;
                messagingTemplate.convertAndSend(mapUpdateTopic, new com.zombieland.backend.dto.MapUpdateDTO(nx, ny, itemType));
                System.out.println(">> ITEM RESPAWNED (" + itemType + ") at [" + nx + "," + ny + "]");
                break;
            }
        }
    }

    @Scheduled(fixedRate = 1000)
    public void processRespawns() {
        long now = System.currentTimeMillis();
        for (String roomCode : roomPlayers.keySet()) {
            Map<String, GameActionMessage> players = roomPlayers.get(roomCode);
            if (players == null) continue;

            for (GameActionMessage player : players.values()) {
                if (player.getHealth() <= 0) {
                    String timerKey = roomCode + ":" + player.getPlayerId();
                    deathTimers.putIfAbsent(timerKey, now);
                    long diedAt = deathTimers.get(timerKey);
                    
                    if (now - diedAt >= 30000) { // 30 seconds
                        WorldMapDTO map = roomMaps.get(roomCode);
                        if (map != null) {
                            // Revive exactly where they died (or at start if position was missing)
                            if (player.getX() == null) player.setX((double)map.getStartX());
                            if (player.getY() == null) player.setY((double)map.getStartY());
                            
                            player.setHealth(100);
                            player.setAmmo(30);
                            player.setAction("RESPAWN");
                            
                            deathTimers.remove(timerKey);
                            
                            String stateTopic = "/topic/game.state." + roomCode;
                            messagingTemplate.convertAndSend(stateTopic, player);
                            System.out.println(">> PLAYER RESPAWNED (AT SPOT): " + player.getPlayerId());
                        }
                    }
                } else {
                    deathTimers.remove(roomCode + ":" + player.getPlayerId());
                }
            }
        }
    }
}

