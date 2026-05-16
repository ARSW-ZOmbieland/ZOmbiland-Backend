package com.zombieland.backend.controller;

import com.zombieland.backend.model.User;
import com.zombieland.backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controlador REST de autenticación.
 * Expone endpoints para consultar el usuario autenticado.
 */
@RestController
@Tag(name = "Autenticación", description = "Endpoints para inicio de sesión, información del usuario y cierre de sesión")
public class AuthController {

    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * GET / → redirige a /api/auth/user (dispara login si no está autenticado)
     */
    @Operation(summary = "Redirigir a usuario", description = "Redirige al endpoint de información del usuario actual. Si no hay sesión, dispara el inicio de sesión OAuth2.")
    @GetMapping("/")
    public RedirectView home() {
        return new RedirectView("/api/auth/user");
    }

    /**
     * GET /api/auth/user
     * Retorna la información del usuario actualmente autenticado.
     */
    @Operation(summary = "Obtener usuario actual", description = "Devuelve la información del usuario autenticado (extraída de la sesión de Google).")
    @GetMapping("/api/auth/user")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }

        String googleId = principal.getAttribute("sub");
        return userRepository.findByGoogleId(googleId)
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(Map.of(
                        "id", user.getId(),
                        "name", user.getName(),
                        "email", user.getEmail(),
                        "imageUrl", user.getImageUrl() != null ? user.getImageUrl() : "",
                        "role", user.getRole().name())))
                .orElse(ResponseEntity.status(404).body(Map.of("error", "Usuario no encontrado")));
    }

    /**
     * GET /api/auth/users → retorna todos los usuarios registrados (dev only).
     */
    @Operation(summary = "Listar todos los usuarios", description = "Endpoint de desarrollo para listar todos los usuarios en la base de datos.")
    @GetMapping("/api/auth/users")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    /**
     * GET /api/auth/logout-info
     * Información del endpoint de logout (útil para el frontend).
     */
    @Operation(summary = "Información de Logout", description = "Retorna la ruta y método recomendados para cerrar la sesión actual.")
    @GetMapping("/api/auth/logout-info")
    public ResponseEntity<?> logoutInfo() {
        return ResponseEntity.ok(Map.of(
                "logoutUrl", "/logout",
                "method", "POST"));
    }
}
