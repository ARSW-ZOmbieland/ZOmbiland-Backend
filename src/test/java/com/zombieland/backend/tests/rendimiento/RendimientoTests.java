package com.zombieland.backend.tests.rendimiento;

import com.zombieland.backend.service.MapGenerator;
import com.zombieland.backend.service.RoomManager;
import com.zombieland.backend.dto.GameActionMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RendimientoTests {

    @Autowired
    private MapGenerator mapGenerator;

    @Autowired
    private RoomManager roomManager;

    @Test
    void testLargeMapGenerationPerformance() {
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < 10; i++) {
            mapGenerator.generateMap("TRADICIONAL");
        }
        
        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 2000, "La generación de mapas tardó demasiado: " + duration + "ms");
    }

    @Test
    void testHighFrequencyUpdates() {
        String code = "PERF_HIGH";
        roomManager.createRoom(code, "TRADICIONAL");
        
        GameActionMessage msg = new GameActionMessage();
        msg.setRoomCode(code);
        msg.setPlayerId("FastPlayer");
        roomManager.joinRoom(msg, "s_perf");
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < 500; i++) {
            msg.setX((double)i);
            msg.setY((double)i);
            roomManager.updatePlayerState(msg);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 1000, "500 actualizaciones tardaron demasiado: " + duration + "ms");
    }
}
