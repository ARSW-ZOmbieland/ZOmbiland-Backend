package com.zombieland.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import com.zombieland.backend.service.RoomManager;
import com.zombieland.backend.dto.GameActionMessage;

@Controller
public class GameWebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private RoomManager roomManager;

    @MessageMapping("/game.join")
    public void handleJoin(@Payload GameActionMessage message, SimpMessageHeaderAccessor headerAccessor) {
        System.out.println(">> STOMP JOIN RECEIVED! Player: " + message.getPlayerId() + " Room: " + message.getRoomCode());
        if (message.getRoomCode() != null && !message.getRoomCode().isEmpty()) {
            boolean joined = roomManager.joinRoom(message, headerAccessor.getSessionId());
            System.out.println(">> Player " + message.getPlayerId() + " join success: " + joined);
            if (joined) {
                String topic = "/topic/game.state." + message.getRoomCode();
                messagingTemplate.convertAndSend(topic, message);
            }
        }
    }

    @MessageMapping("/game.action")
    public void handleGameAction(@Payload GameActionMessage message) {
        System.out.println(">> STOMP ACTION RECEIVED! Player: " + message.getPlayerId() + " Dir: " + message.getAction());
        if (message.getRoomCode() != null && !message.getRoomCode().isEmpty()) {
            roomManager.updatePlayerState(message);
            String topic = "/topic/game.state." + message.getRoomCode();
            messagingTemplate.convertAndSend(topic, message);
        }
    }
}
