package com.zombieland.backend.service;

import com.zombieland.backend.model.User;
import com.zombieland.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Servicio encargado de la autenticación y gestión de usuarios a través de OAuth2.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Procesa la información del usuario obtenida tras el inicio de sesión con OAuth2 (Google).
     * Busca al usuario en la base de datos por su ID de Google; si no existe, crea uno nuevo.
     * Actualiza los datos del perfil y la fecha de última conexión.
     * 
     * @param googleId El ID único del usuario en Google.
     * @param name El nombre del usuario.
     * @param email El correo electrónico del usuario.
     * @param imageUrl La URL de la imagen de perfil del usuario.
     * @return El objeto User actualizado o creado.
     */
    public User processOAuthPostLogin(String googleId, String name, String email, String imageUrl) {
        User user = userRepository.findByGoogleId(googleId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setGoogleId(googleId);
                    newUser.setRole(User.Role.USER);
                    return newUser;
                });

        user.setName(name);
        user.setEmail(email);
        user.setImageUrl(imageUrl);
        user.setLastLogin(LocalDateTime.now());

        return userRepository.save(user);
    }
}
