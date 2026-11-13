package com.sdpcheck.sdpcheck.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "push.enabled", havingValue = "true")
public class PushSchedulingConfig {
}
