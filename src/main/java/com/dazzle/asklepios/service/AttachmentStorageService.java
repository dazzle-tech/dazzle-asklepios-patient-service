package com.dazzle.asklepios.service;

import com.dazzle.asklepios.attachments.AttachmentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class AttachmentStorageService {

    private final S3Client s3;
    private final AttachmentProperties props;

    public void put(String key, String mime, long size, InputStream in) {
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(props.getBucket())
                        .key(key)
                        .contentType(mime)
                        .contentLength(size)
                        .build(),
                RequestBody.fromInputStream(in, size)
        );
    }

    public void putBytes(String key, String mime, byte[] bytes) {
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(props.getBucket())
                        .key(key)
                        .contentType(mime)
                        .contentLength((long) bytes.length)
                        .build(),
                RequestBody.fromBytes(bytes)
        );
    }

    public byte[] getBytes(String key) {
        return s3.getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(props.getBucket())
                        .key(key)
                        .build()
        ).asByteArray();
    }

    /**
     * Build a CDN/public URL for the object. Avoids s3-presigner (not available in local repos).
     */
    public String publicUrl(String key) {
        String cdn = props.getCdnEndpoint();
        if (cdn != null && !cdn.isBlank()) {
            return trimTrailingSlash(cdn) + "/" + key;
        }
        String endpoint = props.getEndpoint();
        String bucket = props.getBucket();
        return trimTrailingSlash(endpoint) + "/" + bucket + "/" + key;
    }

    public void delete(String key) {
        s3.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(props.getBucket())
                        .key(key)
                        .build()
        );
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
