package com.zombieland.backend.repository;

import com.zombieland.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Busca un usuario por su Google Subject ID (campo "sub" del token de Google).
     */
    Optional<User> findByGoogleId(String googleId);

    /**
     * Busca un usuario por su email.
     */
    Optional<User> findByEmail(String email);
}
