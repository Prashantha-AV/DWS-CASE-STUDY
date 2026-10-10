package com.dws.challenge.config;


import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    @Bean
    public GroupedOpenApi controllerApi() {
        return GroupedOpenApi.builder()
                .group("account-api")
                .packagesToScan("com.dws.challenge.web")
                .pathsToMatch("//**")
                .build();
    }
}
