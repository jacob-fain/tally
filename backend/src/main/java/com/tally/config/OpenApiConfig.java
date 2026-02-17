package com.tally.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) configuration for Tally API documentation.
 *
 * Access the docs at:
 * - Swagger UI:   http://localhost:9090/swagger-ui/index.html
 * - OpenAPI JSON: http://localhost:9090/v3/api-docs
 *
 * Why JWT in Swagger?
 * Adding "Bearer Authentication" to the OpenAPI spec allows developers to
 * authenticate directly in the Swagger UI by entering their JWT token via
 * the "Authorize" button. This makes manual API testing much easier.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tally API")
                        .description(
                                "REST API for the Tally habit tracking application. "
                                + "Use /api/auth/login to obtain a JWT token, "
                                + "then click 'Authorize' and enter: Bearer <your-token>")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT access token")));
    }
}
