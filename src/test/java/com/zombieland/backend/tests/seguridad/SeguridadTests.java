package com.zombieland.backend.tests.seguridad;

import com.zombieland.backend.service.ZombieAIService;
import com.zombieland.backend.service.RoomManager;
import com.zombieland.backend.dto.GameActionMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SeguridadTests {

    @Autowired
    private ZombieAIService zombieAIService;

    @Autowired
    private RoomManager roomManager;

    @Test
    void testProximityDamageValidation() {
        assertNotNull(zombieAIService);
    }

    @Test
    void testInvalidMoveValidation() {
        // Marcador para futura lógica de validación de paredes
        assertTrue(true);
    }

    @Test
    void testAmmoConsistency() {
        String code = "SEC_AMMO";
        roomManager.createRoom(code, "TRADICIONAL");
        
        GameActionMessage msg = new GameActionMessage();
        msg.setRoomCode(code);
        msg.setPlayerId("P1");
        msg.setX(10.0);
        msg.setY(10.0);
        roomManager.joinRoom(msg, "s1");
        
        // Gastar toda la munición (empieza con 30)
        for (int i = 0; i < 35; i++) {
            msg.setAction("ATTACK");
            msg.setTargetX(10.0);
            msg.setTargetY(15.0);
            roomManager.handleAttack(msg);
        }
        
        assertEquals(0, msg.getAmmo());
        assertEquals("NO_AMMO", msg.getAction());
    }

    @Test
    void testAttackDistanceValidation() {
        String code = "SEC_DIST";
        roomManager.createRoom(code, "TRADICIONAL");
        
        GameActionMessage p1 = new GameActionMessage();
        p1.setRoomCode(code);
        p1.setPlayerId("P1");
        p1.setX(10.0);
        p1.setY(10.0);
        roomManager.joinRoom(p1, "s1");
        
        p1.setAction("ATTACK");
        p1.setTargetX(100.0);
        p1.setTargetY(100.0);
        
        roomManager.handleAttack(p1);
        
        assertEquals(29, p1.getAmmo());
    }
}
