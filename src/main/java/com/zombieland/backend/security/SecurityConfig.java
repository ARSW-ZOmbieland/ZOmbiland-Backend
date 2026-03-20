package com.zombieland.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;

/**
 * Configuración principal de Spring Security con OAuth2 / Google.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

        private final CustomOAuth2UserService customOAuth2UserService;
        
        @Value("${app.frontend.url:http://localhost:5173}")
        private String frontendUrl;

        @Value("${app.cors.allowed-origins:http://localhost:5173}")
        private List<String> allowedOrigins;

        public SecurityConfig(CustomOAuth2UserService customOAuth2UserService) {
                this.customOAuth2UserService = customOAuth2UserService;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                // ── Autorización de rutas ──────────────────────────────────────
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(
                                                                "/", "/login", "/error",
                                                                "/h2-console/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                "/v3/api-docs/**")
                                                .permitAll()
                                                .anyRequest().authenticated())

                                // ── Configuración CORS ─────────────────────────────────────────
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                                // ── OAuth2 Login ───────────────────────────────────────────────
                                .oauth2Login(oauth -> oauth
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .oidcUserService(customOAuth2UserService))
                                                .defaultSuccessUrl(frontendUrl, true) // Redirigir al
                                                                                                  // frontend al tener
                                                                                                  // éxito
                                                .failureUrl(frontendUrl + "/login?error=true"))

                                // ── Logout ─────────────────────────────────────────────────────
                                .logout(logout -> logout
                                                .logoutRequestMatcher(PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/logout"))
                                                .logoutSuccessUrl(frontendUrl)
                                                .invalidateHttpSession(true)
                                                .clearAuthentication(true)
                                                .deleteCookies("JSESSIONID"))
                                
                                // ── H2 Console (solo dev) y WebSockets ──────────────────────────────────────
                                .csrf(csrf -> csrf
                                                .ignoringRequestMatchers("/h2-console/**", "/ws-game/**", "/api/game/rooms/create"))
                                .headers(headers -> headers
                                                .frameOptions(frame -> frame.sameOrigin()));

                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(allowedOrigins);
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(List.of("*"));
                configuration.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }
}
