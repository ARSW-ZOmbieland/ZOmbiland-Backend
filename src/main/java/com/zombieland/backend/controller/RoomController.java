package com.zombieland.backend.controller;

import com.zombieland.backend.service.RoomManager;
import com.zombieland.backend.dto.GameActionMessage;
import com.zombieland.backend.dto.WorldMapDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/game/rooms")
public class RoomController {

    @Autowired
    private RoomManager roomManager;

    @GetMapping("/{code}")
    public Set<String> getRoomInfo(@PathVariable String code) {
        return roomManager.getPlayersInRoom(code.toUpperCase());
    }

    @GetMapping("/{code}/state")
    public Collection<GameActionMessage> getRoomState(@PathVariable String code) {
        return roomManager.getRoomState(code.toUpperCase());
    }

    @GetMapping("/{code}/map")
    public WorldMapDTO getRoomMap(@PathVariable String code) {
        return roomManager.getRoomMap(code.toUpperCase());
    }

    @PostMapping("/create")
    public void createRoom(@RequestBody Map<String, String> payload) {
        if (payload.containsKey("roomCode")) {
            String mode = payload.getOrDefault("mode", "TRADICIONAL");
            roomManager.createRoom(payload.get("roomCode"), mode);
        }
    }

    @GetMapping("/{code}/mode")
    public Map<String, String> getRoomMode(@PathVariable String code) {
        return Map.of("mode", roomManager.getRoomMode(code.toUpperCase()));
    }

    @GetMapping("/{code}/exists")
    public boolean checkRoomExists(@PathVariable String code) {
        return roomManager.roomExists(code.toUpperCase());
    }
}
