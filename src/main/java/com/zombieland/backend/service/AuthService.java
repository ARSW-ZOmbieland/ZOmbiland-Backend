package com.zombieland.backend.service;

import com.zombieland.backend.model.User;
import com.zombieland.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Procesa la información del usuario obtenida de OAuth2 (Google),
     * lo busca en la base de datos o crea uno nuevo,
     * y actualiza su última fecha de conexión.
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
