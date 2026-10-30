package com.sdpcheck.sdpcheck.domain.home.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class HomeConfig {
    @Bean
    public Clock homeClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
