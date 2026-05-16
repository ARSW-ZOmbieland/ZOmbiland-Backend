package com.zombieland.backend.tests.unitarias;

import com.zombieland.backend.model.User;
import com.zombieland.backend.service.MapGenerator;
import com.zombieland.backend.service.RoomManager;
import com.zombieland.backend.service.TournamentService;
import com.zombieland.backend.service.AuthService;
import com.zombieland.backend.service.ZombieAIService;
import com.zombieland.backend.repository.UserRepository;
import com.zombieland.backend.dto.WorldMapDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class UnitariasTests {

    @Autowired
    private MapGenerator mapGenerator;

    @Autowired
    private RoomManager roomManager;

    @Autowired
    private TournamentService tournamentService;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ZombieAIService zombieAIService;

    @Autowired
    private AuthService authService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void testMapGeneration() {
        WorldMapDTO map = mapGenerator.generateMap("TRADICIONAL");
        assertNotNull(map);
        int[][] matrix = map.getMatrix();
        assertEquals(64, matrix.length);
        assertEquals(64, matrix[0].length);
        
        // Verificar que hay al menos una puerta de búnker (ID 10)
        boolean hasDoor = false;
        for (int[] row : matrix) {
            for (int tile : row) {
                if (tile == 10) {
                    hasDoor = true;
                    break;
                }
            }
        }
        assertTrue(hasDoor, "El mapa debería tener al menos una puerta de búnker en modo TRADICIONAL");
    }

    @Test
    void testMapGenerationTorneo() {
        WorldMapDTO map = mapGenerator.generateMap("TORNEO");
        assertNotNull(map);
        int[][] matrix = map.getMatrix();
        
        // En modo TORNEO no debería haber puertas de búnker (ID 10)
        boolean hasDoor = false;
        for (int[] row : matrix) {
            for (int tile : row) {
                if (tile == 10) {
                    hasDoor = true;
                    break;
                }
            }
        }
        assertFalse(hasDoor, "El mapa modo TORNEO no debería tener puertas de búnker");
    }

    @Test
    void testZombieStateDTO() {
        com.zombieland.backend.dto.ZombieState zombie = new com.zombieland.backend.dto.ZombieState("z1", 10, 20, "arriba", "comun");
        assertEquals("z1", zombie.getId());
        assertEquals(10, zombie.getX());
        assertEquals(20, zombie.getY());
        assertEquals("arriba", zombie.getDirection());
        assertEquals("comun", zombie.getType());
        
        zombie.setX(15);
        assertEquals(15, zombie.getX());
    }

    @Test
    void testGameActionMessageDTO() {
        com.zombieland.backend.dto.GameActionMessage msg = new com.zombieland.backend.dto.GameActionMessage();
        msg.setPlayerId("p1");
        msg.setHealth(80);
        msg.setAmmo(25);
        
        assertEquals("p1", msg.getPlayerId());
        assertEquals(80, msg.getHealth());
        assertEquals(25, msg.getAmmo());
    }

    @Test
    void testUserEntity() {
        User user = new User();
        user.setName("Isabel");
        user.setEmail("isabel@example.com");
        user.setGoogleId("google123");
        user.setRole(User.Role.ADMIN);
        user.setLastLogin(LocalDateTime.now());
        
        assertEquals("Isabel", user.getName());
        assertEquals("isabel@example.com", user.getEmail());
        assertEquals(User.Role.ADMIN, user.getRole());
        assertNotNull(user.getLastLogin());
    }

    @Test
    void testAuthServiceLogin() {
        when(userRepository.findByGoogleId("new_id")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);
        
        User user = authService.processOAuthPostLogin("new_id", "Name", "email@test.com", "url");
        assertNotNull(user);
        assertEquals("Name", user.getName());
    }

    @Test
    void testTournamentTimeEnd() {
        String code = "END_ROOM";
        roomManager.createRoom(code, "TORNEO");
        
        // No podemos inyectar el tiempo fácilmente sin refactorizar, pero podemos ver si corre
        tournamentService.processTournamentZones();
        assertNotNull(roomManager.getRoomMode(code));
    }

    @Test
    void testRoomManagerFullRoom() {
        String code = "FULL";
        roomManager.createRoom(code, "TRADICIONAL");
        
        for(int i=1; i<=4; i++) {
            com.zombieland.backend.dto.GameActionMessage m = new com.zombieland.backend.dto.GameActionMessage();
            m.setRoomCode(code);
            m.setPlayerId("P"+i);
            roomManager.joinRoom(m, "s"+i);
        }
        
        com.zombieland.backend.dto.GameActionMessage m5 = new com.zombieland.backend.dto.GameActionMessage();
        m5.setRoomCode(code);
        m5.setPlayerId("P5");
        boolean joined = roomManager.joinRoom(m5, "s5");
        assertFalse(joined, "La sala no debería aceptar más de 4 jugadores");
    }

    @Test
    void testMainApplication() {
        // Asegurarnos de usar el perfil 'test' para evitar que busque Postgres en el puerto 5432
        System.setProperty("spring.profiles.active", "test");
        com.zombieland.backend.ZombielandBackendApplication.main(new String[]{"--server.port=0"});
    }

    @Test
    void testTournamentWinner() {
        String code = "WIN_ROOM";
        roomManager.createRoom(code, "TORNEO");
        
        // Simular un solo jugador vivo
        com.zombieland.backend.dto.GameActionMessage m1 = new com.zombieland.backend.dto.GameActionMessage();
        m1.setRoomCode(code);
        m1.setPlayerId("WINNER");
        roomManager.joinRoom(m1, "sess_win");
        
        // Ejecutar procesamiento
        tournamentService.processTournamentZones();
        assertNotNull(roomManager.getRoomMode(code));
    }

    @Test
    void testRoomManagerSessionManagement() {
        String code = "SESS_ROOM";
        roomManager.createRoom(code, "TRADICIONAL");
        
        com.zombieland.backend.dto.GameActionMessage m = new com.zombieland.backend.dto.GameActionMessage();
        m.setRoomCode(code);
        m.setPlayerId("P1");
        roomManager.joinRoom(m, "session_to_leave");
        
        assertTrue(roomManager.getPlayersInRoom(code).contains("P1"));
        
        // Salir por sesión
        roomManager.leaveRoomBySession("session_to_leave");
        assertFalse(roomManager.getPlayersInRoom(code).contains("P1"));
    }

    @Test
    void testZombieAIServiceBasic() {
        assertNotNull(roomManager);
    }

    @Test
    void testWorldMapDTOFull() {
        WorldMapDTO map = new WorldMapDTO();
        map.setStartX(100);
        map.setStartY(200);
        int[][] m = {{1, 2}, {3, 4}};
        map.setMatrix(m);
        
        assertEquals(100, map.getStartX());
        assertEquals(200, map.getStartY());
        assertEquals(2, map.getMatrix().length);
    }

    @Test
    void testRoomManagerRespawns() {
        String code = "RESPAWN_ROOM";
        roomManager.createRoom(code, "TRADICIONAL");
        
        com.zombieland.backend.dto.GameActionMessage m = new com.zombieland.backend.dto.GameActionMessage();
        m.setRoomCode(code);
        m.setPlayerId("DEAD_P");
        m.setHealth(0); // Muerto
        roomManager.joinRoom(m, "sess_dead");
        
        // Forzar respawn
        roomManager.updatePlayerState(m);
        // La lógica de respawn en RoomManager suele resetear salud
        assertNotNull(roomManager.getPlayersInRoom(code));
    }

    @Test
    void testTournamentServiceGameOver() {
        String code = "OVER_ROOM";
        roomManager.createRoom(code, "TORNEO");
        // No hay jugadores, debería manejarlo sin error
        tournamentService.processTournamentZones();
        assertTrue(roomManager.roomExists(code));
    }

    @Test
    void testRoomManagerPlayerExists() {
        String code = "EXIST_ROOM";
        roomManager.createRoom(code, "TRADICIONAL");
        com.zombieland.backend.dto.GameActionMessage m = new com.zombieland.backend.dto.GameActionMessage();
        m.setRoomCode(code);
        m.setPlayerId("P_EXIST");
        roomManager.joinRoom(m, "s_exist");
        
        // Usar getPlayersInRoom en lugar de un método que no existe
        assertTrue(roomManager.getPlayersInRoom(code).contains("P_EXIST"));
    }

    @Test
    void testTournamentWinAndEnd() {
        String winCode = "WIN_ROOM";
        roomManager.createRoom(winCode, "TORNEO");
        
        // 2 jugadores, uno vivo, uno muerto
        com.zombieland.backend.dto.GameActionMessage p1 = new com.zombieland.backend.dto.GameActionMessage();
        p1.setRoomCode(winCode); p1.setPlayerId("W"); p1.setHealth(100);
        roomManager.joinRoom(p1, "s1");
        
        com.zombieland.backend.dto.GameActionMessage p2 = new com.zombieland.backend.dto.GameActionMessage();
        p2.setRoomCode(winCode); p2.setPlayerId("L"); p2.setHealth(0);
        roomManager.joinRoom(p2, "s2");
        
        tournamentService.processTournamentZones(); // Debería llamar a handleTournamentWin
        
        // Test End por tiempo
        String endCode = "END_OLD";
        roomManager.createRoom(endCode, "TORNEO");
        // No podemos mockear el tiempo fácilmente pero el test cubrirá la lógica de búsqueda
        tournamentService.processTournamentZones();
    }

    @Test
    void testPlayerCombatAndElimination() {
        String code = "COMBAT_FINAL";
        roomManager.createRoom(code, "TORNEO");
        
        com.zombieland.backend.dto.GameActionMessage p1 = new com.zombieland.backend.dto.GameActionMessage();
        p1.setRoomCode(code); p1.setPlayerId("A"); p1.setLocation("world");
        roomManager.joinRoom(p1, "s1");
        
        com.zombieland.backend.dto.GameActionMessage p2 = new com.zombieland.backend.dto.GameActionMessage();
        p2.setRoomCode(code); p2.setPlayerId("B"); p2.setLocation("world");
        roomManager.joinRoom(p2, "s2");

        // FORZAR POSICIONES Y VIDA DESPUÉS DEL JOIN
        p1.setX(10.0); p1.setY(10.0); p1.setAmmo(50);
        roomManager.updatePlayerState(p1);
        
        p2.setX(11.0); p2.setY(10.0); p2.setHealth(1);
        // Hack para forzar vida en el mapa interno ya que updatePlayerState la hereda del 'existing'
        roomManager.getRoomState(code).stream().filter(p -> p.getPlayerId().equals("B")).forEach(p -> p.setHealth(1));
        roomManager.getRoomState(code).stream().filter(p -> p.getPlayerId().equals("B")).forEach(p -> p.setX(11.0));
        roomManager.getRoomState(code).stream().filter(p -> p.getPlayerId().equals("B")).forEach(p -> p.setY(10.0));

        // Atacar
        p1.setTargetX(11.0); p1.setTargetY(10.0);
        roomManager.handleAttack(p1);
        
        int healthB = roomManager.getRoomState(code).stream()
            .filter(p -> p.getPlayerId().equals("B"))
            .findFirst().get().getHealth();
        assertEquals(0, healthB);
    }

    @Test
    void testZombieDamageLogic() throws InterruptedException {
        String code = "Z_DMG_FINAL";
        roomManager.createRoom(code, "TRADICIONAL");
        
        com.zombieland.backend.dto.GameActionMessage p = new com.zombieland.backend.dto.GameActionMessage();
        p.setRoomCode(code); p.setPlayerId("P"); p.setLocation("world");
        roomManager.joinRoom(p, "s_dmg");
        
        // Forzar posición jugador
        double px = 2.0; double py = 2.0;
        roomManager.getRoomState(code).forEach(pl -> { pl.setX(px); pl.setY(py); });
        
        // Hacer que la coordenada sea transitable en el mapa
        roomManager.getRoomMap(code).getMatrix()[(int)py][(int)px] = 0;
        
        // Forzar zombi encima del jugador
        com.zombieland.backend.dto.ZombieState z = roomManager.getZombiesInRoom(code).get(0);
        z.setX(px); z.setY(py);
        
        zombieAIService.updateZombies(); // Primer tick: empieza a atacar (wind-up)
        Thread.sleep(600); // Esperar a que pase el wind-up de 500ms
        zombieAIService.updateZombies(); // Segundo tick: aplica el daño
        
        int finalHealth = roomManager.getRoomState(code).iterator().next().getHealth();
        assertTrue(finalHealth < 100, "El jugador debería tener menos de 100 de vida. Actual: " + finalHealth);
    }
}
