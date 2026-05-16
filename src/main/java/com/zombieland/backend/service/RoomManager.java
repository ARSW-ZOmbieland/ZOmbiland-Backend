package com.zombieland.backend.service;

import com.zombieland.backend.dto.GameActionMessage;
import com.zombieland.backend.dto.WorldMapDTO;
import com.zombieland.backend.dto.ZombieState;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio encargado de gestionar las salas de juego, las sesiones de los jugadores y el estado del juego.
 * Maneja la creación de salas, la entrada/salida de jugadores y la sincronización de la lógica del juego.
 */
@Service
public class RoomManager {
    /** Mapa de códigos de sala a sus respectivos mapas del mundo. */
    private final ConcurrentHashMap<String, WorldMapDTO> roomMaps = new ConcurrentHashMap<>();
    
    private final MapGenerator mapGenerator;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    public RoomManager(MapGenerator mapGenerator, org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate) {
        this.mapGenerator = mapGenerator;
        this.messagingTemplate = messagingTemplate;
    }

    /** Mapa de códigos de sala a un mapa de IDs de jugador y su estado de juego actual. */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, GameActionMessage>> roomPlayers = new ConcurrentHashMap<>();
    
    /** Mapa de códigos de sala a la lista de zombis actualmente en esa sala. */
    private final ConcurrentHashMap<String, List<ZombieState>> roomZombies = new ConcurrentHashMap<>();
    
    /** Mapa de IDs de sesión a la información de la sesión del jugador. */
    private final ConcurrentHashMap<String, PlayerSessionInfo> sessionTrackers = new ConcurrentHashMap<>();
    
    /** Mapa de códigos de sala a su estado de pausa actual. */
    private final ConcurrentHashMap<String, Boolean> roomPausedStates = new ConcurrentHashMap<>();
    
    /** Conjunto de códigos de sala actualmente válidos y activos. */
    private final Set<String> validRooms = ConcurrentHashMap.newKeySet();

    /** Mapa de roomCode:playerId a la marca de tiempo cuando el jugador murió. */
    private final ConcurrentHashMap<String, Long> deathTimers = new ConcurrentHashMap<>();

    /** Conjunto de jugadores eliminados en el modo torneo (roomCode:playerId). */
    private final Set<String> eliminatedPlayers = ConcurrentHashMap.newKeySet();

    /** Mapa de códigos de sala a su modo de juego (ej., "TRADICIONAL", "TORNEO"). */
    private final ConcurrentHashMap<String, String> roomModes = new ConcurrentHashMap<>();

    /** Mapa de códigos de sala a su marca de tiempo de creación/inicio. */
    private final ConcurrentHashMap<String, Long> roomStartTimes = new ConcurrentHashMap<>();

    /**
     * Crea una nueva sala de juego con el código y modo especificados.
     * Genera el mapa y genera zombis si corresponde.
     * 
     * @param roomCode El código único para la sala.
     * @param mode El modo de juego ("TRADICIONAL" o "TORNEO").
     */
    public void createRoom(String roomCode, String mode) {
        String code = roomCode.toUpperCase();
        String finalMode = mode != null ? mode.toUpperCase() : "TRADICIONAL";
        validRooms.add(code);
        roomModes.put(code, finalMode);
        roomStartTimes.put(code, System.currentTimeMillis());
        roomPlayers.putIfAbsent(code, new ConcurrentHashMap<>());
        WorldMapDTO map = mapGenerator.generateMap(finalMode);
        roomMaps.putIfAbsent(code, map);
        
        List<ZombieState> zombies = new ArrayList<>();
        
        if (!"TORNEO".equals(finalMode)) {
            Random rand = new Random();
            int[][] matrix = map.getMatrix();
            
            for (int i = 1; i <= 40; i++) {
                int rx = 0, ry = 0;
                boolean found = false;
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
                if (!found) {
                    rx = (int)map.getStartX() + (i % 3);
                    ry = (int)map.getStartY() + (i / 3);
                }
                
                String type;
                int chance = rand.nextInt(100);
                if (chance < 35) {
                    type = "comun";
                } else if (chance < 60) {
                    type = "chasqueador";
                } else if (chance < 80) {
                    type = "tanke";
                } else {
                    type = "hunter";
                }
                ZombieState newZombie = new ZombieState("zombie-" + i, rx, ry, "abajo", type);
                if ("tanke".equals(type)) {
                    newZombie.setHealth(680); 
                }
                zombies.add(newZombie);
            }
        } 
        
        roomZombies.put(code, zombies);
    }

    /**
     * Obtiene el mapa para una sala específica.
     * 
     * @param roomCode El código de la sala.
     * @return El WorldMapDTO asociado a la sala.
     */
    public WorldMapDTO getRoomMap(String roomCode) {
        return roomMaps.get(roomCode.toUpperCase());
    }

    /**
     * Obtiene el modo de juego para una sala específica.
     * 
     * @param roomCode El código de la sala.
     * @return El modo de juego ("TRADICIONAL" o "TORNEO").
     */
    public String getRoomMode(String roomCode) {
        return roomModes.getOrDefault(roomCode.toUpperCase(), "TRADICIONAL");
    }

    /**
     * Obtiene el tiempo de inicio de una sala específica.
     * 
     * @param roomCode El código de la sala.
     * @return El tiempo de inicio en milisegundos.
     */
    public Long getRoomStartTime(String roomCode) {
        return roomStartTimes.get(roomCode.toUpperCase());
    }

    /**
     * Verifica si una sala existe.
     * 
     * @param roomCode El código de la sala a verificar.
     * @return true si la sala existe, false en caso contrario.
     */
    public boolean roomExists(String roomCode) {
        return validRooms.contains(roomCode.toUpperCase());
    }

    /**
     * Registra la eliminación de un jugador en el modo torneo.
     * 
     * @param roomCode El código de la sala.
     * @param playerId El ID del jugador.
     */
    public void registerElimination(String roomCode, String playerId) {
        String mode = getRoomMode(roomCode);
        if ("TORNEO".equals(mode)) {
            eliminatedPlayers.add(roomCode.toUpperCase() + ":" + playerId);
        }
    }

    /**
     * Clase interna para rastrear la información de la sesión del jugador.
     */
    public static class PlayerSessionInfo {
        public String roomCode;
        public String playerId;
        public PlayerSessionInfo(String roomCode, String playerId) {
            this.roomCode = roomCode;
            this.playerId = playerId;
        }
    }

    /**
     * Gestiona la unión de un jugador a una sala. Valida la capacidad de la sala y las restricciones del modo.
     * Establece la salud y munición inicial, y determina la posición de aparición.
     * 
     * @param message El mensaje de unión que contiene los detalles del jugador y la sala.
     * @param sessionId El ID de sesión del jugador.
     * @return true si el jugador se unió con éxito, false en caso contrario.
     */
    public synchronized boolean joinRoom(GameActionMessage message, String sessionId) {
        String roomCode = message.getRoomCode().toUpperCase();
        String playerId = message.getPlayerId();
        
        String mode = roomModes.getOrDefault(roomCode, "TRADICIONAL");
        if ("TORNEO".equals(mode) && eliminatedPlayers.contains(roomCode + ":" + playerId)) {
            return false;
        }
        
        roomPlayers.putIfAbsent(roomCode, new ConcurrentHashMap<>());
        Map<String, GameActionMessage> players = roomPlayers.get(roomCode);
        
        if (players.size() >= 4 && !players.containsKey(playerId)) {
            return false; 
        }
        
        message.setHealth(100);
        message.setAmmo(30);
        
        GameActionMessage existing = players.get(playerId);
        if (existing != null && "world".equals(existing.getLocation()) && "world".equals(message.getLocation())) {
            message.setX(existing.getX());
            message.setY(existing.getY());
        } else if ("world".equals(message.getLocation())) {
            if ("TORNEO".equals(mode)) {
                assignRandomSpawn(message);
            } else {
                WorldMapDTO map = roomMaps.get(roomCode);
                if (map != null) {
                    message.setX((double)map.getStartX());
                    message.setY((double)map.getStartY());
                }
            }
        }
        
        players.put(playerId, message);

        sessionTrackers.put(sessionId, new PlayerSessionInfo(roomCode, playerId));
        return true;
    }

    /**
     * Asigna una posición de aparición aleatoria a un jugador, asegurando que sea una baldosa transitable
     * e idealmente alejada de otros jugadores.
     * 
     * @param message El mensaje a actualizar con la nueva posición.
     */
    private void assignRandomSpawn(GameActionMessage message) {
        String roomCode = message.getRoomCode().toUpperCase();
        WorldMapDTO map = roomMaps.get(roomCode);
        if (map == null) return;
        
        int[][] matrix = map.getMatrix();
        int size = matrix.length;
        Random rand = new Random();
        
        double bestX = 32, bestY = 32;
        double maxMinDist = -1;
        
        Map<String, GameActionMessage> existingPlayers = roomPlayers.get(roomCode);
        
        for (int attempts = 0; attempts < 100; attempts++) {
            int rx = rand.nextInt(size);
            int ry = rand.nextInt(size);
            
            if (matrix[ry][rx] >= 0 && matrix[ry][rx] <= 7) {
                double minDist = 100.0;
                
                if (existingPlayers != null && !existingPlayers.isEmpty()) {
                    for (GameActionMessage other : existingPlayers.values()) {
                        if (other.getX() == null || other.getY() == null || other.getPlayerId().equals(message.getPlayerId())) continue;
                        double dist = Math.sqrt(Math.pow(rx - other.getX(), 2) + Math.pow(ry - other.getY(), 2));
                        if (dist < minDist) minDist = dist;
                    }
                }
                
                if (minDist > maxMinDist) {
                    maxMinDist = minDist;
                    bestX = rx;
                    bestY = ry;
                }
                
                if (maxMinDist > 20) break;
            }
        }
        
        message.setX(bestX);
        message.setY(bestY);
        message.setAction("TELEPORT");
    }


    /**
     * Actualiza el estado de un jugador en la sala. Maneja la lógica de pausa global,
     * transiciones entre búnker y mundo, y recolección de objetos (botiquines y munición).
     * 
     * @param message El mensaje que contiene el nuevo estado del jugador.
     */
    public void updatePlayerState(GameActionMessage message) {
        String roomCode = message.getRoomCode().toUpperCase();
        
        if ("PAUSE".equals(message.getAction())) {
            roomPausedStates.put(roomCode, true);
        } else if ("RESUME".equals(message.getAction())) {
            roomPausedStates.put(roomCode, false);
        }

        if (roomPlayers.containsKey(roomCode)) {
            Map<String, GameActionMessage> players = roomPlayers.get(roomCode);
            GameActionMessage existing = players.get(message.getPlayerId());
            if (existing != null) {
                if (message.getX() == null) message.setX(existing.getX());
                if (message.getY() == null) message.setY(existing.getY());
                if (message.getAimAngle() == null) message.setAimAngle(existing.getAimAngle());
                
                message.setHealth(existing.getHealth());
                message.setAmmo(existing.getAmmo());
                message.setParalyzed(existing.isParalyzed());
                
                String mode = getRoomMode(roomCode);
                if ("world".equals(message.getLocation()) && "bunker".equals(existing.getLocation())) {
                    if ("TORNEO".equals(mode)) {
                        assignRandomSpawn(message);
                    } else {
                        WorldMapDTO map = roomMaps.get(roomCode);
                        if (map != null) {
                            message.setX((double)map.getStartX());
                            message.setY((double)map.getStartY());
                            message.setAction("TELEPORT");
                        }
                    }
                }

                if (message.getLocation() == null) message.setLocation(existing.getLocation());
                
                if (existing.isParalyzed()) {
                    message.setX(existing.getX());
                    message.setY(existing.getY());
                }
                
                WorldMapDTO map = roomMaps.get(roomCode);
                if (map != null && message.getX() != null && message.getY() != null) {
                    int px = message.getX().intValue();
                    int py = message.getY().intValue();
                    int[][] matrix = map.getMatrix();
                    
                    if (py >= 0 && py < matrix.length && px >= 0 && px < matrix[0].length) {
                        int tile = matrix[py][px];
                        if (tile == 100) { 
                            matrix[py][px] = 0; 
                            message.setHealth(100); 
                            
                            String stateTopic = "/topic/game.state." + roomCode;
                            messagingTemplate.convertAndSend(stateTopic, message);
                            
                            String mapUpdateTopic = "/topic/game.map." + roomCode;
                            messagingTemplate.convertAndSend(mapUpdateTopic, new com.zombieland.backend.dto.MapUpdateDTO(px, py, 0));
                            
                            respawnItem(roomCode, 100);
                        } else if (tile == 101) { 
                            matrix[py][px] = 0; 
                            message.setAmmo(message.getAmmo() + 30); 
                            
                            String stateTopic = "/topic/game.state." + roomCode;
                            messagingTemplate.convertAndSend(stateTopic, message);
                            
                            String mapUpdateTopic = "/topic/game.map." + roomCode;
                            messagingTemplate.convertAndSend(mapUpdateTopic, new com.zombieland.backend.dto.MapUpdateDTO(px, py, 0));
                            
                            respawnItem(roomCode, 101);
                        }
                    }
                }
                
                players.put(message.getPlayerId(), message);
            }
        }
    }

    /**
     * Verifica si una sala está en pausa.
     * 
     * @param roomCode El código de la sala.
     * @return true si la sala está pausada, false en caso contrario.
     */
    public boolean isRoomPaused(String roomCode) {
        return roomPausedStates.getOrDefault(roomCode.toUpperCase(), false);
    }

    /**
     * Gestiona la salida de un jugador de una sala basándose en su ID de sesión.
     * 
     * @param sessionId El ID de sesión del jugador.
     * @return El código de la sala que el jugador abandonó, o null si no se encontró.
     */
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

    /**
     * Obtiene el conjunto de IDs de jugadores en una sala específica.
     * 
     * @param roomCode El código de la sala.
     * @return Conjunto de IDs de jugadores.
     */
    public Set<String> getPlayersInRoom(String roomCode) {
        if (!roomPlayers.containsKey(roomCode)) return Collections.emptySet();
        return roomPlayers.get(roomCode).keySet();
    }

    /**
     * Obtiene el estado actual de todos los jugadores en una sala específica.
     * 
     * @param roomCode El código de la sala.
     * @return Colección de mensajes de acción de juego que representan el estado de los jugadores.
     */
    public Collection<GameActionMessage> getRoomState(String roomCode) {
        if (!roomPlayers.containsKey(roomCode)) return Collections.emptyList();
        return roomPlayers.get(roomCode).values();
    }

    /**
     * Obtiene la lista de zombis en una sala específica.
     * 
     * @param roomCode El código de la sala.
     * @return Lista de estados de los zombis.
     */
    public List<ZombieState> getZombiesInRoom(String roomCode) {
        return roomZombies.getOrDefault(roomCode.toUpperCase(), Collections.emptyList());
    }

    /**
     * Gestiona un ataque realizado por un jugador. Valida munición, calcula la trayectoria
     * y aplica daño al objetivo más cercano (zombi o jugador en modo torneo).
     * 
     * @param message El mensaje de ataque que contiene la posición y el objetivo.
     */
    public synchronized void handleAttack(GameActionMessage message) {
        String roomCode = message.getRoomCode().toUpperCase();
        
        Map<String, GameActionMessage> players = roomPlayers.get(roomCode);
        if (players == null) return;
        GameActionMessage serverState = players.get(message.getPlayerId());
        if (serverState == null || serverState.getAmmo() <= 0) {
            message.setAmmo(0); 
            message.setAction("NO_AMMO"); 
            return;
        }

        serverState.setAmmo(serverState.getAmmo() - 1);
        message.setAmmo(serverState.getAmmo()); 
        message.setHealth(serverState.getHealth()); 

        List<ZombieState> zombies = roomZombies.get(roomCode);
        if (zombies == null) return;

        double tx = message.getTargetX();
        double ty = message.getTargetY();
        double px = message.getX();
        double py = message.getY();

        double dx = tx - px;
        double dy = ty - py;
        double magnitude = Math.sqrt(dx * dx + dy * dy);
        if (magnitude < 0.01) return; 

        double angle = Math.atan2(dy, dx);
        double snappedAngle = Math.round(angle / (Math.PI / 4.0)) * (Math.PI / 4.0);
        double vx = Math.cos(snappedAngle);
        double vy = Math.sin(snappedAngle);

        ZombieState targetZombie = null;
        GameActionMessage targetPlayer = null;
        double closestDist = 7.0; 

        for (ZombieState zombie : zombies) {
            double zx = zombie.getX() - px;
            double zy = zombie.getY() - py;
            
            double dot = zx * vx + zy * vy;
            
            double distSq = (zx * zx + zy * zy) - (dot * dot);
            
            if (dot > 0 && dot < 6.0 && distSq < 0.2) { 
                if (dot < closestDist) {
                    closestDist = dot;
                    targetZombie = zombie;
                    targetPlayer = null;
                }
            }
        }

        String mode = roomModes.getOrDefault(roomCode, "TRADICIONAL");
        if ("TORNEO".equals(mode) && "world".equals(message.getLocation())) {
            for (GameActionMessage otherPlayer : players.values()) {
                if (otherPlayer.getPlayerId().equals(message.getPlayerId())) continue;
                if (otherPlayer.getHealth() <= 0) continue;
                if (!"world".equals(otherPlayer.getLocation())) continue; 

                double ox = otherPlayer.getX() - px;
                double oy = otherPlayer.getY() - py;
                double dot = ox * vx + oy * vy;
                double distSq = (ox * ox + oy * oy) - (dot * dot);

                if (dot > 0 && dot < 6.0 && distSq < 0.2) {
                    if (dot < closestDist) {
                        closestDist = dot;
                        targetZombie = null;
                        targetPlayer = otherPlayer;
                    }
                }
            }
        }

        if (targetZombie != null) {
            targetZombie.setHealth(targetZombie.getHealth() - 34); 
            if (targetZombie.getHealth() <= 0) {
                zombies.remove(targetZombie);
            }
        } else if (targetPlayer != null) {
            targetPlayer.setHealth(Math.max(0, targetPlayer.getHealth() - 20));
            if (targetPlayer.getHealth() <= 0) {
                registerElimination(roomCode, targetPlayer.getPlayerId());
            }
            String stateTopic = "/topic/game.state." + roomCode;
            messagingTemplate.convertAndSend(stateTopic, targetPlayer);
        }
    }

    /**
     * Obtiene todos los códigos de salas activas.
     * 
     * @return Conjunto de códigos de sala.
     */
    public Set<String> getAllActiveRooms() {
        return roomPlayers.keySet();
    }

    /**
     * Reaparece un objeto (botiquín o munición) en una posición transitable aleatoria del mapa.
     * 
     * @param roomCode El código de la sala.
     * @param itemType El tipo de objeto (100 para botiquín, 101 para munición).
     */
    private void respawnItem(String roomCode, int itemType) {
        WorldMapDTO map = roomMaps.get(roomCode);
        if (map == null) return;
        int[][] matrix = map.getMatrix();
        int size = matrix.length;
        java.util.Random rand = new java.util.Random();
        
        for (int attempts = 0; attempts < 100; attempts++) {
            int nx = rand.nextInt(size);
            int ny = rand.nextInt(size);
            if (ny >= 0 && ny < size && nx >= 0 && nx < size && matrix[ny][nx] >= 0 && matrix[ny][nx] <= 7) {
                matrix[ny][nx] = itemType;
                
                String mapUpdateTopic = "/topic/game.map." + roomCode;
                messagingTemplate.convertAndSend(mapUpdateTopic, new com.zombieland.backend.dto.MapUpdateDTO(nx, ny, itemType));
                break;
            }
        }
    }

    /**
     * Procesa la reaparición de jugadores muertos. Si el jugador ha estado muerto
     * por más de 15 segundos y no está en modo torneo, reaparece en la posición inicial.
     */
    @Scheduled(fixedRate = 1000)
    public void processRespawns() {
        long now = System.currentTimeMillis();
        for (String roomCode : roomPlayers.keySet()) {
            String mode = getRoomMode(roomCode);
            Map<String, GameActionMessage> players = roomPlayers.get(roomCode);
            if (players == null) continue;

            for (GameActionMessage player : players.values()) {
                if (player.getHealth() <= 0) {
                    if ("TORNEO".equals(mode)) {
                        continue;
                    }

                    String timerKey = roomCode + ":" + player.getPlayerId();
                    deathTimers.putIfAbsent(timerKey, now);
                    long diedAt = deathTimers.get(timerKey);
                    
                    if (now - diedAt >= 15000) { 
                        WorldMapDTO map = roomMaps.get(roomCode);
                        if (map != null) {
                            player.setX((double)map.getStartX());
                            player.setY((double)map.getStartY());

                            player.setHealth(100);
                            player.setAmmo(30);
                            player.setParalyzed(false);
                            player.setAction("RESPAWN"); 

                            deathTimers.remove(timerKey);
                            
                            String stateTopic = "/topic/game.state." + roomCode;
                            messagingTemplate.convertAndSend(stateTopic, player);
                        }
                    }
                } else {
                    deathTimers.remove(roomCode + ":" + player.getPlayerId());
                }
            }
        }
    }
}

