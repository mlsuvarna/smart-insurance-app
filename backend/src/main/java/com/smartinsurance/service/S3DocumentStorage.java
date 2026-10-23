package com.smartinsurance.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import jakarta.annotation.PreDestroy;

@Component
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "s3")
public class S3DocumentStorage implements DocumentStorage {
    private final S3Client s3;
    private final String bucket;
    public S3DocumentStorage(@Value("${AWS_REGION}") String region, @Value("${S3_BUCKET}") String bucket) {
        this.s3 = S3Client.builder().region(Region.of(region)).build();
        this.bucket = bucket;
    }
    @Override
    public void store(String key, byte[] content) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).serverSideEncryption(ServerSideEncryption.AES256).build(),
                RequestBody.fromBytes(content));
    }
    @Override
    public byte[] load(String key) {
        return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }
    @Override
    public void delete(String key) {
        String versionId = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).versionId();
        DeleteObjectRequest.Builder request = DeleteObjectRequest.builder().bucket(bucket).key(key);
        if (versionId != null && !versionId.isBlank() && !"null".equals(versionId)) {
            request.versionId(versionId);
        }
        s3.deleteObject(request.build());
    }
    @PreDestroy
    void close() { s3.close(); }
}
