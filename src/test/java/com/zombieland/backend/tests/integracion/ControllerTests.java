package com.zombieland.backend.tests.integracion;

import com.zombieland.backend.controller.RoomController;
import com.zombieland.backend.controller.AuthController;
import com.zombieland.backend.controller.GameWebSocketController;
import com.zombieland.backend.service.RoomManager;
import com.zombieland.backend.repository.UserRepository;
import com.zombieland.backend.model.User;
import com.zombieland.backend.dto.GameActionMessage;
import com.zombieland.backend.dto.WorldMapDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ControllerTests {

    private MockMvc roomMvc;
    private MockMvc authMvc;

    @Mock
    private RoomManager roomManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private RoomController roomController;

    @InjectMocks
    private AuthController authController;

    @InjectMocks
    private GameWebSocketController webSocketController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        roomMvc = MockMvcBuilders.standaloneSetup(roomController).build();
        authMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    void testRoomControllerFlows() throws Exception {
        when(roomManager.getPlayersInRoom("ABC")).thenReturn(Collections.singleton("P1"));
        when(roomManager.roomExists("ABC")).thenReturn(true);
        when(roomManager.getRoomMap("ABC")).thenReturn(new WorldMapDTO());
        when(roomManager.getRoomMode("ABC")).thenReturn("TORNEO");

        roomMvc.perform(get("/api/game/rooms/abc")).andExpect(status().isOk());
        roomMvc.perform(get("/api/game/rooms/abc/exists")).andExpect(content().string("true"));
        roomMvc.perform(get("/api/game/rooms/abc/state")).andExpect(status().isOk());
        roomMvc.perform(get("/api/game/rooms/abc/map")).andExpect(status().isOk());
        roomMvc.perform(get("/api/game/rooms/abc/mode")).andExpect(status().isOk());
        
        roomMvc.perform(post("/api/game/rooms/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomCode\": \"TEST\"}")).andExpect(status().isOk());
    }

    @Test
    void testAuthControllerFlows() throws Exception {
        authMvc.perform(get("/api/auth/logout-info")).andExpect(status().isOk());
        authMvc.perform(get("/")).andExpect(status().is3xxRedirection());
        
        // Probar getCurrentUser con null principal (Error 401)
        ResponseEntity<?> response = authController.getCurrentUser(null);
        assertEquals(401, response.getStatusCode().value());

        // Probar con principal pero usuario no encontrado (Error 404)
        OAuth2User principal = mock(OAuth2User.class);
        when(principal.getAttribute("sub")).thenReturn("123");
        when(userRepository.findByGoogleId("123")).thenReturn(Optional.empty());
        
        response = authController.getCurrentUser(principal);
        assertEquals(404, response.getStatusCode().value());

        // Probar con éxito
        User user = new User();
        user.setId(1L);
        user.setName("Isabel");
        user.setEmail("isa@test.com");
        user.setRole(User.Role.USER);
        when(userRepository.findByGoogleId("123")).thenReturn(Optional.of(user));
        
        response = authController.getCurrentUser(principal);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void testGameWebSocketControllerDirectly() {
        GameActionMessage msg = new GameActionMessage();
        msg.setRoomCode("ROOM1");
        msg.setPlayerId("P1");
        msg.setAction("MOVE");

        SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
        when(accessor.getSessionId()).thenReturn("sess1");
        when(roomManager.joinRoom(any(), any())).thenReturn(true);

        // Probar Join
        webSocketController.handleJoin(msg, accessor);

        // Probar Action
        msg.setAction("ATTACK");
        webSocketController.handleGameAction(msg);
        verify(roomManager, times(1)).handleAttack(msg);

        msg.setAction("MOVE");
        webSocketController.handleGameAction(msg);
        verify(roomManager, times(1)).updatePlayerState(msg);
    }
}
