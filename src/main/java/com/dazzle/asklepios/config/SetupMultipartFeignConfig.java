package com.dazzle.asklepios.config;

import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import org.springframework.context.annotation.Bean;

/**
 * Multipart encoder for setup clients that send files.
 * Not a @Configuration on purpose, so it applies only to the clients that list it.
 */
public class SetupMultipartFeignConfig {

    @Bean
    public Encoder feignFormEncoder() {
        return new SpringFormEncoder();
    }
}
