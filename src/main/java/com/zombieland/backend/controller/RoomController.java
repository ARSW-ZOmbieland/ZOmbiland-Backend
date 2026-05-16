package com.zombieland.backend.controller;

import com.zombieland.backend.service.RoomManager;
import com.zombieland.backend.dto.GameActionMessage;
import com.zombieland.backend.dto.WorldMapDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/game/rooms")
@Tag(name = "Gestión de Salas", description = "Endpoints para crear, consultar y administrar salas de juego")
public class RoomController {

    @Autowired
    private RoomManager roomManager;

    @Operation(
        summary = "Obtener jugadores de la sala", 
        description = "Devuelve una lista con los nombres o IDs de los jugadores que se encuentran actualmente en la sala especificada."
    )
    @GetMapping("/{code}")
    public Set<String> getRoomInfo(@PathVariable String code) {
        return roomManager.getPlayersInRoom(code.toUpperCase());
    }

    @Operation(
        summary = "Obtener estado de la sala",
        description = "Devuelve una colección de todas las acciones y eventos recientes que han ocurrido en la sala (movimientos, disparos, etc)."
    )
    @GetMapping("/{code}/state")
    public Collection<GameActionMessage> getRoomState(@PathVariable String code) {
        return roomManager.getRoomState(code.toUpperCase());
    }

    @Operation(
        summary = "Obtener mapa de la sala",
        description = "Devuelve la información de la matriz y configuración del mapa generado para esta sala."
    )
    @GetMapping("/{code}/map")
    public WorldMapDTO getRoomMap(@PathVariable String code) {
        return roomManager.getRoomMap(code.toUpperCase());
    }

    @Operation(
        summary = "Crear una nueva sala",
        description = "Crea una nueva sala de juego con un código especificado y un modo de juego (TRADICIONAL, SUPERVIVENCIA, etc).",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "JSON con el código y el modo de la sala",
            required = true,
            content = @Content(schema = @Schema(
                example = "{\n  \"roomCode\": \"SALA123\",\n  \"mode\": \"TRADICIONAL\"\n}"
            ))
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Sala creada correctamente")
        }
    )
    @PostMapping("/create")
    public void createRoom(@RequestBody Map<String, String> payload) {
        if (payload.containsKey("roomCode")) {
            String mode = payload.getOrDefault("mode", "TRADICIONAL");
            roomManager.createRoom(payload.get("roomCode"), mode);
        }
    }

    @Operation(summary = "Obtener modo de juego", description = "Retorna el modo de juego configurado para esta sala (ej. TRADICIONAL).")
    @GetMapping("/{code}/mode")
    public Map<String, String> getRoomMode(@PathVariable String code) {
        return Map.of("mode", roomManager.getRoomMode(code.toUpperCase()));
    }

    @Operation(summary = "Verificar si sala existe", description = "Verifica si una sala con el código especificado ya existe y está activa.")
    @GetMapping("/{code}/exists")
    public boolean checkRoomExists(@PathVariable String code) {
        return roomManager.roomExists(code.toUpperCase());
    }
}
