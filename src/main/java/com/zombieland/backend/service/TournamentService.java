package com.zombieland.backend.service;

import com.zombieland.backend.dto.GameActionMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TournamentService {

    private final RoomManager roomManager;
    private final SimpMessagingTemplate messagingTemplate;

    public TournamentService(RoomManager roomManager, SimpMessagingTemplate messagingTemplate) {
        this.roomManager = roomManager;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedRate = 1000) // Cada segundo
    public void processTournamentZones() {
        Set<String> rooms = roomManager.getAllActiveRooms();
        long now = System.currentTimeMillis();

        for (String roomCode : rooms) {
            String mode = roomManager.getRoomMode(roomCode);
            if (!"TORNEO".equals(mode)) continue;

            Long startTime = roomManager.getRoomStartTime(roomCode);
            if (startTime == null) continue;

            long elapsedSeconds = (now - startTime) / 1000;
            if (elapsedSeconds > 300) {
                // Fin del juego o muerte súbita total
                handleTournamentEnd(roomCode);
                continue;
            }

            // Calcular radio de la zona (de 50 a 0 en 300 segundos)
            double maxRadius = 50.0;
            double currentRadius = maxRadius * (1.0 - (double) elapsedSeconds / 300.0);
            if (currentRadius < 0) currentRadius = 0;

            // Centro del mapa (64x64 -> 32, 32)
            double centerX = 32.0;
            double centerY = 32.0;

            Collection<GameActionMessage> players = roomManager.getRoomState(roomCode);
            for (GameActionMessage player : players) {
                if (player.getHealth() <= 0) continue;
                if (!"world".equals(player.getLocation())) continue; // Zona no afecta búnker

                double dx = player.getX() - centerX;
                double dy = player.getY() - centerY;
                double dist = Math.sqrt(dx * dx + dy * dy);

                if (dist > currentRadius) {
                    // Daño por zona (5 HP por segundo fuera de zona)
                    player.setHealth(Math.max(0, player.getHealth() - 5));
                    
                    if (player.getHealth() <= 0) {
                        roomManager.registerElimination(roomCode, player.getPlayerId());
                        System.out.println(">> PLAYER DIED TO ZONE: " + player.getPlayerId());
                    }

                    // Notificar actualización de vida
                    messagingTemplate.convertAndSend("/topic/game.state." + roomCode, player);
                }
            }

            // Enviar el estado de la zona a los clientes (radio actual)
            Map<String, Object> zoneStatus = new ConcurrentHashMap<>();
            zoneStatus.put("radius", currentRadius);
            zoneStatus.put("timeLeft", 300 - elapsedSeconds);
            messagingTemplate.convertAndSend("/topic/game.zone." + roomCode, zoneStatus);
        }
    }

    private void handleTournamentEnd(String roomCode) {
        // Lógica de finalización (opcional por ahora)
    }
}
