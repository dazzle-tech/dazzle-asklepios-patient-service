package com.dazzle.asklepios.integration.waseel.config;

import feign.Logger;
import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

public class WaseelFeignConfig {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    public Request.Options options() {
        return new Request.Options(
                90, TimeUnit.SECONDS,
                90, TimeUnit.SECONDS,
                true
        );
    }
}