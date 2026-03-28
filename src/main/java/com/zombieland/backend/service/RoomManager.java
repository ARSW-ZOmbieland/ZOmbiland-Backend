package com.zombieland.backend.service;

import com.zombieland.backend.dto.GameActionMessage;
import com.zombieland.backend.dto.WorldMapDTO;
import com.zombieland.backend.dto.ZombieState;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomManager {
    // roomCode -> WorldMapDTO
    private final ConcurrentHashMap<String, WorldMapDTO> roomMaps = new ConcurrentHashMap<>();
    
    private final MapGenerator mapGenerator;

    public RoomManager(MapGenerator mapGenerator) {
        this.mapGenerator = mapGenerator;
    }

    // roomCode -> (playerId -> GameActionMessage)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, GameActionMessage>> roomPlayers = new ConcurrentHashMap<>();
    
    // roomCode -> List of Zombies
    private final ConcurrentHashMap<String, List<ZombieState>> roomZombies = new ConcurrentHashMap<>();
    
    // sessionId -> PlayerSessionInfo
    private final ConcurrentHashMap<String, PlayerSessionInfo> sessionTrackers = new ConcurrentHashMap<>();
    
    // Explicitly generated room codes
    private final Set<String> validRooms = ConcurrentHashMap.newKeySet();

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
        
        for (int i = 1; i <= 20; i++) {
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
            
            zombies.add(new ZombieState("zombie-" + i, rx, ry, "abajo"));
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
        String roomCode = message.getRoomCode();
        if (roomPlayers.containsKey(roomCode)) {
            Map<String, GameActionMessage> players = roomPlayers.get(roomCode);
            GameActionMessage existing = players.get(message.getPlayerId());
            if (existing != null) {
                // Preserve health from server state, update only pos/action
                message.setHealth(existing.getHealth());
                players.put(message.getPlayerId(), message);
            }
        }
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

    public Set<String> getAllActiveRooms() {
        return roomPlayers.keySet();
    }
}
