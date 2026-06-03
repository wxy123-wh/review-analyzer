package com.wh.reputation.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Reputation Analysis System API")
                        .version("1.0")
                        .description("Backend API for Bluetooth Headphone Reputation Analysis MVP")
                        .contact(new Contact().name("WH Team")));
    }
}
