package com.zombieland.backend.tests.concurrencia;

import com.zombieland.backend.service.RoomManager;
import com.zombieland.backend.dto.GameActionMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrenciaTests {

    @Autowired
    private RoomManager roomManager;

    @Test
    void testConcurrentRoomJoining() throws InterruptedException {
        String code = "CONC123";
        roomManager.createRoom(code, "TRADICIONAL");
        
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int id = i;
            executor.submit(() -> {
                GameActionMessage msg = new GameActionMessage();
                msg.setRoomCode(code);
                msg.setPlayerId("Player" + id);
                msg.setLocation("world");
                
                if (roomManager.joinRoom(msg, "session-" + id)) {
                    successCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertTrue(roomManager.roomExists(code));
        assertTrue(roomManager.getRoomState(code).size() > 1);
    }

    @Test
    void testConcurrentMapUpdates() throws InterruptedException {
        String code = "MAPCONC";
        roomManager.createRoom(code, "TRADICIONAL");
        
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int id = i;
            executor.submit(() -> {
                GameActionMessage msg = new GameActionMessage();
                msg.setRoomCode(code);
                msg.setPlayerId("Player" + id);
                msg.setX(32.0 + id);
                msg.setY(32.0 + id);
                roomManager.updatePlayerState(msg);
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        assertNotNull(roomManager.getRoomState(code));
    }
}
