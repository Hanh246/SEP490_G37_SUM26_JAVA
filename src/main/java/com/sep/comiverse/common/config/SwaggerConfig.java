package com.sep.comiverse.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        Server localServer = new Server();
        localServer.setUrl("/api");
        localServer.setDescription("Môi trường phát triển cục bộ (Local)");

        return new OpenAPI()
                .info(new Info()
                        .title("COMIVERSE - WEBSITE ĐỌC TRUYỆN TRANH API")
                        .version("1.0.0")
                        .description("Tài liệu đặc tả hệ thống API cho dự án Web Truyện Tranh Comiverse. Sử dụng Plugin-based Architecture.")
                        .contact(new Contact()
                                .name("Nguyễn Xuân Hanh")
                                .email("nguyenxuanhanh0440@gmail.com")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }

    @Bean
    public ModelResolver modelResolver(ObjectMapper objectMapper) {
        return new ModelResolver(objectMapper);
    }
}
