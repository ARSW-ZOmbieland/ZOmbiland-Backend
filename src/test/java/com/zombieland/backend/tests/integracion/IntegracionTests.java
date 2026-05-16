package com.zombieland.backend.tests.integracion;

import com.zombieland.backend.service.RoomManager;
import com.zombieland.backend.dto.GameActionMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class IntegracionTests {

    @Autowired
    private RoomManager roomManager;

    @Test
    void testContextLoadsAndServicesArePresent() {
        assertNotNull(roomManager, "RoomManager debería estar cargado en el contexto");
    }

    @Test
    void testRoomLifecycle() {
        String code = "TEST123";
        roomManager.createRoom(code, "TRADICIONAL");
        
        assertTrue(roomManager.roomExists(code));
        assertNotNull(roomManager.getRoomMap(code));
        
        roomManager.leaveRoomBySession("dummy-session");
    }

    @Test
    void testFullGameFlow() {
        String code = "FLOW123";
        roomManager.createRoom(code, "TRADICIONAL");
        
        GameActionMessage msg = new GameActionMessage();
        msg.setRoomCode(code);
        msg.setPlayerId("Player1");
        msg.setLocation("world");
        
        roomManager.joinRoom(msg, "session1");
        
        GameActionMessage updateMsg = new GameActionMessage();
        updateMsg.setRoomCode(code);
        updateMsg.setPlayerId("Player1");
        updateMsg.setX(10.0);
        updateMsg.setY(10.0);
        updateMsg.setLocation("world");
        roomManager.updatePlayerState(updateMsg);
        
        GameActionMessage state = roomManager.getRoomState(code).iterator().next();
        assertEquals(10.0, state.getX());
    }

    @Test
    void testBunkerTransition() {
        String code = "BUNK123";
        roomManager.createRoom(code, "TRADICIONAL");
        
        GameActionMessage joinMsg = new GameActionMessage();
        joinMsg.setRoomCode(code);
        joinMsg.setPlayerId("Player1");
        joinMsg.setLocation("bunker");
        roomManager.joinRoom(joinMsg, "session1");
        
        GameActionMessage updateMsg = new GameActionMessage();
        updateMsg.setRoomCode(code);
        updateMsg.setPlayerId("Player1");
        updateMsg.setLocation("world");
        roomManager.updatePlayerState(updateMsg);
        
        GameActionMessage state = roomManager.getRoomState(code).iterator().next();
        assertNotNull(state.getX(), "X no debería ser nulo tras la transición");
        assertEquals("TELEPORT", state.getAction());
    }
}
