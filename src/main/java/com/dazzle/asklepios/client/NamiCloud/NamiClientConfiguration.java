package com.dazzle.asklepios.client.NamiCloud;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class NamiClientConfiguration {

    @Bean
    public RestClient namiRestClient(
            NamiProperties properties
    ) {

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }
}
