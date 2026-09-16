package com.dazzle.asklepios.client.NamiCloud;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(
        NamiProperties.class
)
public class PosConfiguration {
}
