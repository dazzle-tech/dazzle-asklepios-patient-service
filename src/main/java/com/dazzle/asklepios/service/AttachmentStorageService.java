package com.dazzle.asklepios.service;

import com.dazzle.asklepios.attachments.AttachmentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;


import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AttachmentStorageService {
    private final S3Client s3;
    private final S3Presigner presigner;
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

    public PresignedPutObjectRequest presignPut(String key, String mime, long size) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(props.getBucket()).key(key)
                .contentType(mime).contentLength(size)
                .build();
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(props.getPresignExpirySeconds()))
                .putObjectRequest(put).build());
    }

    public PresignedGetObjectRequest presignGet(String key, String downloadName) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(props.getBucket()).key(key)
                .responseContentDisposition("attachment; filename=\"" + downloadName + "\"")
                .build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(props.getPresignExpirySeconds()))
                .getObjectRequest(get).build());
    }

    public HeadObjectResponse head(String key) {
        return s3.headObject(HeadObjectRequest.builder()
                .bucket(props.getBucket()).key(key).build());
    }

    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(props.getBucket()).key(key).build());
    }
    public PresignedGetObjectRequest presignView(String key) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .build();

        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(props.getPresignExpirySeconds()))
                .getObjectRequest(get)
                .build());
    }
    public void putPublic(String key, String mime, long size, InputStream in) {
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(props.getBucket())
                        .key(key)
                        .contentType(mime)
                        .contentLength(size)
                        .acl(ObjectCannedACL.PUBLIC_READ)
                        .build(),
                RequestBody.fromInputStream(in, size)
        );
    }
    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
