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
import java.util.List;

/**
 * Configuración principal de Spring Security con OAuth2 / Google.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

        private final CustomOAuth2UserService customOAuth2UserService;

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
                                                .defaultSuccessUrl("http://localhost:5173", true) // Redirigir al
                                                                                                  // frontend al tener
                                                                                                  // éxito
                                                .failureUrl("http://localhost:5173/login?error=true"))

                                // ── Logout ─────────────────────────────────────────────────────
                                .logout(logout -> logout
                                                .logoutRequestMatcher(PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/logout"))
                                                .logoutSuccessUrl("http://localhost:5173")
                                                .invalidateHttpSession(true)
                                                .clearAuthentication(true)
                                                .deleteCookies("JSESSIONID"))

                                // ── H2 Console (solo dev) ──────────────────────────────────────
                                .csrf(csrf -> csrf
                                                .ignoringRequestMatchers("/h2-console/**"))
                                .headers(headers -> headers
                                                .frameOptions(frame -> frame.sameOrigin()));

                return http.build();
        }

        @Bean
        public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
                org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
                configuration.setAllowedOrigins(java.util.List.of("http://localhost:5173")); // URL del frontend en
                                                                                             // React
                configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(java.util.List.of("*"));
                configuration.setAllowCredentials(true); // Permitir el envío de cookies de sesión (JSESSIONID)

                org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }
}
