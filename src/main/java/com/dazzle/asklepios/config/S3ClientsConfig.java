package com.dazzle.asklepios.config;

import com.dazzle.asklepios.attachments.AttachmentProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
public class S3ClientsConfig {
    @Bean S3Client s3(AttachmentProperties p) {
        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(p.getAccessKeyId(), p.getSecretAccessKey())))
                .region(p.getRegion())
                .endpointOverride(URI.create(p.getEndpoint()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(false).build())
                .build();
    }
    @Bean S3Presigner presigner(AttachmentProperties p) {
        return S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(p.getAccessKeyId(), p.getSecretAccessKey())))
                .region(p.getRegion())
                .endpointOverride(URI.create(p.getEndpoint()))
                .build();
    }
}

