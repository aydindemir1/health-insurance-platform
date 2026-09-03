package com.aydindemir.health.authorization.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ApplicationConfig {
    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
