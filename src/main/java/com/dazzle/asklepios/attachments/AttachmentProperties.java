package com.dazzle.asklepios.attachments;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;

import java.util.Set;

@Component
@Data
@ConfigurationProperties(prefix = "patient.attachments")
public class AttachmentProperties {
    private String bucket;
    private Region region;
    private String endpoint;
    private String cdnEndpoint;
    private int presignExpirySeconds;
    private long maxBytes;
    private Set<String> allowed;
    private String accessKeyId;
    private String secretAccessKey;
}
