package com.zombieland.backend.service;

import com.zombieland.backend.dto.GameActionMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio encargado de gestionar la lógica del modo torneo (Battle Royale).
 * Controla la reducción de la zona, el daño por zona y la determinación del ganador.
 */
@Service
public class TournamentService {

    private final RoomManager roomManager;
    private final SimpMessagingTemplate messagingTemplate;

    public TournamentService(RoomManager roomManager, SimpMessagingTemplate messagingTemplate) {
        this.roomManager = roomManager;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Tarea programada que procesa las zonas del torneo cada segundo.
     * Calcula el radio de la zona segura, aplica daño a los jugadores fuera de ella
     * y verifica si hay un ganador.
     */
    @Scheduled(fixedRate = 1000)
    public void processTournamentZones() {
        Set<String> rooms = roomManager.getAllActiveRooms();
        long now = System.currentTimeMillis();

        for (String roomCode : rooms) {
            String mode = roomManager.getRoomMode(roomCode);
            if (!"TORNEO".equals(mode)) continue;

            Long startTime = roomManager.getRoomStartTime(roomCode);
            if (startTime == null) continue;

            long elapsedSeconds = (now - startTime) / 1000;
            
            Collection<GameActionMessage> players = roomManager.getRoomState(roomCode);
            GameActionMessage winner = null;
            int aliveCount = 0;
            
            for (GameActionMessage p : players) {
                if (p.getHealth() > 0) {
                    aliveCount++;
                    winner = p;
                }
            }

            if (aliveCount == 1 && players.size() > 1) {
                handleTournamentWin(roomCode, winner.getPlayerId());
                continue;
            }

            if (elapsedSeconds > 300) {
                handleTournamentEnd(roomCode);
                continue;
            }

            double maxRadius = 50.0;
            double currentRadius = maxRadius * (1.0 - (double) elapsedSeconds / 300.0);
            if (currentRadius < 0) currentRadius = 0;

            double centerX = 32.0;
            double centerY = 32.0;

            for (GameActionMessage player : players) {
                if (player.getHealth() <= 0) continue;
                if (!"world".equals(player.getLocation())) continue;

                double dx = player.getX() - centerX;
                double dy = player.getY() - centerY;
                double dist = Math.sqrt(dx * dx + dy * dy);

                if (dist > currentRadius) {
                    player.setHealth(Math.max(0, player.getHealth() - 5));
                    
                    if (player.getHealth() <= 0) {
                        roomManager.registerElimination(roomCode, player.getPlayerId());
                    }

                    messagingTemplate.convertAndSend("/topic/game.state." + roomCode, player);
                }
            }

            Map<String, Object> zoneStatus = new ConcurrentHashMap<>();
            zoneStatus.put("radius", currentRadius);
            zoneStatus.put("timeLeft", 300 - (int)elapsedSeconds);
            messagingTemplate.convertAndSend("/topic/game.zone." + roomCode, (Object) zoneStatus);
        }
    }

    /**
     * Gestiona la victoria de un jugador en el torneo.
     * Notifica a todos los jugadores en la sala sobre el ganador.
     * 
     * @param roomCode El código de la sala.
     * @param playerId El ID del jugador ganador.
     */
    private void handleTournamentWin(String roomCode, String playerId) {
        Map<String, Object> winData = new ConcurrentHashMap<>();
        winData.put("action", "TOURNAMENT_WIN");
        winData.put("winnerId", playerId);
        messagingTemplate.convertAndSend("/topic/game.state." + roomCode, (Object) winData);
    }

    /**
     * Gestiona el fin del torneo por tiempo agotado.
     * 
     * @param roomCode El código de la sala.
     */
    private void handleTournamentEnd(String roomCode) {
        Map<String, Object> endData = new ConcurrentHashMap<>();
        endData.put("action", "TOURNAMENT_END");
        messagingTemplate.convertAndSend("/topic/game.state." + roomCode, (Object) endData);
    }
}
