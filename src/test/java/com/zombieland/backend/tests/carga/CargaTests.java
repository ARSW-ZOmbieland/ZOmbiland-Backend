package com.zombieland.backend.tests.carga;

import com.zombieland.backend.dto.GameActionMessage;
import com.zombieland.backend.service.RoomManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class CargaTests {

    @Autowired
    private RoomManager roomManager;

    @Test
    void testConcurrentRoomJoins() throws InterruptedException {
        String roomCode = "LOAD_TEST_ROOM";
        roomManager.createRoom(roomCode, "TRADICIONAL");

        int numberOfUsers = 50; // Ajusta este número para probar diferentes cargas
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfUsers);
        CountDownLatch latch = new CountDownLatch(numberOfUsers);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfUsers; i++) {
            final int userId = i;
            executorService.submit(() -> {
                try {
                    GameActionMessage msg = new GameActionMessage();
                    msg.setRoomCode(roomCode);
                    msg.setPlayerId("User_" + userId);
                    roomManager.joinRoom(msg, "session_" + userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Error joining room: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        System.out.println("Usuarios intentando unirse: " + numberOfUsers);
        System.out.println("Usuarios unidos con éxito: " + successCount.get());

        assertTrue(completed, "La prueba de carga no terminó a tiempo");
        assertTrue(successCount.get() > 0, "Ningún usuario pudo unirse");
    }
}
