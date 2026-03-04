package com.zombieland.backend.security;

import com.zombieland.backend.model.User;
import com.zombieland.backend.service.AuthService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Servicio personalizado OIDC (Google usa OpenID Connect cuando el scope
 * incluye "openid").
 * Intercepta el login, extrae los atributos del token y persiste el usuario en
 * BD.
 */
@Service
public class CustomOAuth2UserService extends OidcUserService {

    private final AuthService authService;

    public CustomOAuth2UserService(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        // 1. Delegar al servicio OIDC base para validar el token de Google
        OidcUser oidcUser = super.loadUser(userRequest);

        // 2. Extraer atributos del token OIDC
        String googleId = oidcUser.getSubject(); // campo "sub"
        String name = oidcUser.getFullName();
        String email = oidcUser.getEmail();
        String imageUrl = oidcUser.getPicture();

        // 3 y 4. Delegar al AuthService para buscar/crear y actualizar lastLogin
        User user = authService.processOAuthPostLogin(googleId, name, email, imageUrl);

        return oidcUser;
    }
}
