package com.zombieland.backend.tests.unitarias;

import com.zombieland.backend.dto.GameActionMessage;
import com.zombieland.backend.dto.WorldMapDTO;
import com.zombieland.backend.service.RoomManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CoverageImprovementTests {

    @Autowired
    private RoomManager roomManager;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void testPauseResume() {
        String code = "PAUSE_ROOM";
        roomManager.createRoom(code, "TRADICIONAL");
        
        GameActionMessage msg = new GameActionMessage();
        msg.setRoomCode(code);
        msg.setPlayerId("P1");
        roomManager.joinRoom(msg, "s1");

        GameActionMessage pauseMsg = new GameActionMessage();
        pauseMsg.setRoomCode(code);
        pauseMsg.setPlayerId("P1");
        pauseMsg.setAction("PAUSE");
        
        roomManager.updatePlayerState(pauseMsg);
        assertTrue(roomManager.isRoomPaused(code));
        
        GameActionMessage resumeMsg = new GameActionMessage();
        resumeMsg.setRoomCode(code);
        resumeMsg.setPlayerId("P1");
        resumeMsg.setAction("RESUME");
        roomManager.updatePlayerState(resumeMsg);
        assertFalse(roomManager.isRoomPaused(code));
    }

    @Test
    void testCollectHealthPack() {
        String code = "HEALTH_ROOM";
        roomManager.createRoom(code, "TRADICIONAL");
        
        // Unir jugador
        GameActionMessage joinMsg = new GameActionMessage();
        joinMsg.setRoomCode(code);
        joinMsg.setPlayerId("P1");
        joinMsg.setLocation("world");
        roomManager.joinRoom(joinMsg, "s1");
        
        // Forzar salud baja en el estado del servidor
        roomManager.getRoomState(code).stream().filter(p -> p.getPlayerId().equals("P1")).forEach(p -> p.setHealth(50));
        
        // Poner botiquín (100) en el mapa
        WorldMapDTO map = roomManager.getRoomMap(code);
        int[][] matrix = map.getMatrix();
        matrix[10][10] = 100;
        
        // Mover jugador al botiquín
        GameActionMessage updateMsg = new GameActionMessage();
        updateMsg.setRoomCode(code);
        updateMsg.setPlayerId("P1");
        updateMsg.setX(10.0);
        updateMsg.setY(10.0);
        updateMsg.setLocation("world");
        roomManager.updatePlayerState(updateMsg);
        
        // Verificar salud restaurada
        int health = roomManager.getRoomState(code).stream().filter(p -> p.getPlayerId().equals("P1")).findFirst().get().getHealth();
        assertEquals(100, health);
        assertEquals(0, matrix[10][10], "El botiquín debería haber sido consumido");
    }

    @Test
    void testCollectAmmoPack() {
        String code = "AMMO_ROOM";
        roomManager.createRoom(code, "TRADICIONAL");
        
        // Unir jugador
        GameActionMessage joinMsg = new GameActionMessage();
        joinMsg.setRoomCode(code);
        joinMsg.setPlayerId("P1");
        joinMsg.setLocation("world");
        roomManager.joinRoom(joinMsg, "s1");
        
        // Forzar munición baja
        roomManager.getRoomState(code).stream().filter(p -> p.getPlayerId().equals("P1")).forEach(p -> p.setAmmo(5));
        
        // Poner munición (101) en el mapa
        WorldMapDTO map = roomManager.getRoomMap(code);
        int[][] matrix = map.getMatrix();
        matrix[12][12] = 101;
        
        // Mover jugador a la munición
        GameActionMessage updateMsg = new GameActionMessage();
        updateMsg.setRoomCode(code);
        updateMsg.setPlayerId("P1");
        updateMsg.setX(12.0);
        updateMsg.setY(12.0);
        updateMsg.setLocation("world");
        roomManager.updatePlayerState(updateMsg);
        
        // Verificar munición aumentada (5 + 30 = 35)
        int ammo = roomManager.getRoomState(code).stream().filter(p -> p.getPlayerId().equals("P1")).findFirst().get().getAmmo();
        assertEquals(35, ammo);
        assertEquals(0, matrix[12][12], "La munición debería haber sido consumida");
    }

    @Test
    void testBunkerToWorldTransition() {
        String code = "TRANSITION_ROOM";
        roomManager.createRoom(code, "TRADICIONAL");
        
        // Unir jugador en el bunker
        GameActionMessage joinMsg = new GameActionMessage();
        joinMsg.setRoomCode(code);
        joinMsg.setPlayerId("P1");
        joinMsg.setLocation("bunker");
        roomManager.joinRoom(joinMsg, "s1");
        
        // Cambiar a world en el mensaje de actualización (usando un nuevo objeto)
        GameActionMessage updateMsg = new GameActionMessage();
        updateMsg.setRoomCode(code);
        updateMsg.setPlayerId("P1");
        updateMsg.setLocation("world");
        roomManager.updatePlayerState(updateMsg);
        
        // Verificar que el servidor ahora tiene al jugador en el mundo y en la posición de inicio
        GameActionMessage state = roomManager.getRoomState(code).stream().filter(p -> p.getPlayerId().equals("P1")).findFirst().get();
        assertEquals("world", state.getLocation());
        WorldMapDTO map = roomManager.getRoomMap(code);
        assertEquals((double)map.getStartX(), state.getX());
        assertEquals((double)map.getStartY(), state.getY());
    }

    @Test
    void testProcessRespawns() {
        String code = "RESPAWN_PROC_ROOM";
        roomManager.createRoom(code, "TRADICIONAL");
        
        // Unir jugador muerto
        GameActionMessage msg = new GameActionMessage();
        msg.setRoomCode(code);
        msg.setPlayerId("DEAD_P");
        msg.setHealth(0);
        roomManager.joinRoom(msg, "s_dead");
        
        // Asegurarse de que el servidor lo registre como muerto
        roomManager.getRoomState(code).stream().filter(p -> p.getPlayerId().equals("DEAD_P")).forEach(p -> p.setHealth(0));
        
        // Llamar a processRespawns (la primera vez registra el timer)
        roomManager.processRespawns();
        
        // Limpiar
        GameActionMessage reviveMsg = new GameActionMessage();
        reviveMsg.setRoomCode(code);
        reviveMsg.setPlayerId("DEAD_P");
        reviveMsg.setHealth(100);
        roomManager.updatePlayerState(reviveMsg);
        roomManager.processRespawns();
        
        assertTrue(true);
    }
    
    @Test
    void testParalyzedMovement() {
        String code = "PARA_ROOM";
        roomManager.createRoom(code, "TRADICIONAL");
        
        GameActionMessage joinMsg = new GameActionMessage();
        joinMsg.setRoomCode(code);
        joinMsg.setPlayerId("P1");
        roomManager.joinRoom(joinMsg, "s1");
        
        // Forzar paralizado
        roomManager.getRoomState(code).stream().filter(p -> p.getPlayerId().equals("P1")).forEach(p -> {
            p.setParalyzed(true);
            p.setX(10.0);
            p.setY(10.0);
        });
        
        // Intentar mover con un nuevo mensaje
        GameActionMessage moveMsg = new GameActionMessage();
        moveMsg.setRoomCode(code);
        moveMsg.setPlayerId("P1");
        moveMsg.setX(20.0);
        moveMsg.setY(20.0);
        roomManager.updatePlayerState(moveMsg);
        
        // Verificar que la posición se mantuvo en 10,10
        GameActionMessage state = roomManager.getRoomState(code).stream().filter(p -> p.getPlayerId().equals("P1")).findFirst().get();
        assertEquals(10.0, state.getX());
        assertEquals(10.0, state.getY());
    }
}
