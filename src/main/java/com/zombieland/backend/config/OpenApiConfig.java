package com.zombieland.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de OpenAPI / Swagger UI para ZOmbiland Backend.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI zombielandOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ZOmbiland Backend API")
                        .description("API REST del juego ZOmbiland con autenticación Google OAuth2")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("ZOmbiland Team")
                                .email("team@zombieland.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addSecurityItem(new SecurityRequirement().addList("Google OAuth2"))
                .components(new Components()
                        .addSecuritySchemes("Google OAuth2", new SecurityScheme()
                                .name("Google OAuth2")
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("Autenticación con Google OAuth2")));
    }
}
