package com.zombieland.backend.tests.resiliencia;

import com.zombieland.backend.service.RoomManager;
import com.zombieland.backend.dto.GameActionMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ResilienciaTests {

    @Autowired
    private RoomManager roomManager;

    @Test
    void testHandlingFullRoom() {
        String code = "FULL123";
        roomManager.createRoom(code, "TRADICIONAL");
        
        for (int i = 0; i < 4; i++) {
            GameActionMessage msg = new GameActionMessage();
            msg.setRoomCode(code);
            msg.setPlayerId("Player" + i);
            roomManager.joinRoom(msg, "session-" + i);
        }
        
        GameActionMessage msg5 = new GameActionMessage();
        msg5.setRoomCode(code);
        msg5.setPlayerId("Player5");
        boolean result = roomManager.joinRoom(msg5, "session-5");
        
        assertFalse(result, "No debería ser posible unirse a una sala llena");
    }

    @Test
    void testCleanupOnPlayerDisconnect() {
        String code = "CLEANUP123";
        roomManager.createRoom(code, "TRADICIONAL");
        
        GameActionMessage msg = new GameActionMessage();
        msg.setRoomCode(code);
        msg.setPlayerId("Host");
        roomManager.joinRoom(msg, "session-host");
        
        roomManager.leaveRoomBySession("session-host");
        assertTrue(roomManager.getRoomState(code).isEmpty());
    }

    @Test
    void testAutomaticRespawnLogic() {
        String code = "RESPAWN123";
        roomManager.createRoom(code, "TRADICIONAL");
        
        GameActionMessage msg = new GameActionMessage();
        msg.setRoomCode(code);
        msg.setPlayerId("DyingPlayer");
        roomManager.joinRoom(msg, "s_death");
        
        msg.setHealth(0);
        roomManager.updatePlayerState(msg);
        
        roomManager.processRespawns();
        
        assertEquals(0, msg.getHealth(), "No debería reaparecer inmediatamente");
    }
}
