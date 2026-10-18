package com.store.catalog.service;

import com.store.common.config.AppProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Object storage (Cloudflare R2 or S3; R2 is S3-compatible via the endpoint override).
 * Admins upload images straight to storage with a presigned PUT; the API never streams image bytes.
 * Invoice PDFs (later phase) are written server-side with {@link #put}.
 */
@Service
public class StorageService {

    private static final Duration PRESIGN_TTL = Duration.ofMinutes(10);

    public record Presigned(String url, Map<String, String> headers, Instant expiresAt) {}

    private final AppProperties.Storage cfg;
    private final Clock clock;
    private volatile S3Presigner presigner;
    private volatile S3Client client;

    public StorageService(AppProperties props, Clock clock) {
        this.cfg = props.storage();
        this.clock = clock;
    }

    public Presigned presignPut(String key, String contentType, long contentLength) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(cfg.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .cacheControl("public, max-age=31536000, immutable")
                .build();
        PresignedPutObjectRequest req = presigner().presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGN_TTL)
                .putObjectRequest(put)
                .build());
        return new Presigned(req.url().toString(),
                Map.of("Content-Type", contentType, "Cache-Control", "public, max-age=31536000, immutable"),
                clock.instant().plus(PRESIGN_TTL));
    }

    public void put(String key, byte[] bytes, String contentType) {
        client().putObject(PutObjectRequest.builder().bucket(cfg.bucket()).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(bytes));
    }

    public String publicUrl(String key) {
        String base = cfg.publicBaseUrl().endsWith("/") ? cfg.publicBaseUrl() : cfg.publicBaseUrl() + "/";
        return base + key;
    }

    private S3Presigner presigner() {
        if (presigner == null) {
            synchronized (this) {
                if (presigner == null) {
                    S3Presigner.Builder b = S3Presigner.builder()
                            .region(Region.of(cfg.region()))
                            .credentialsProvider(credentials())
                            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(cfg.pathStyle()).build());
                    if (cfg.endpoint() != null && !cfg.endpoint().isBlank()) {
                        b.endpointOverride(URI.create(cfg.endpoint()));
                    }
                    presigner = b.build();
                }
            }
        }
        return presigner;
    }

    private S3Client client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    var b = S3Client.builder()
                            .region(Region.of(cfg.region()))
                            .credentialsProvider(credentials())
                            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(cfg.pathStyle()).build());
                    if (cfg.endpoint() != null && !cfg.endpoint().isBlank()) {
                        b.endpointOverride(URI.create(cfg.endpoint()));
                    }
                    client = b.build();
                }
            }
        }
        return client;
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(cfg.accessKey(), cfg.secretKey()));
    }
}
