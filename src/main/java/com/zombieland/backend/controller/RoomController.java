package com.zombieland.backend.controller;

import com.zombieland.backend.service.RoomManager;
import com.zombieland.backend.dto.GameActionMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
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
}
