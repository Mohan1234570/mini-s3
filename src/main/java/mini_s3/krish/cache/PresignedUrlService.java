package mini_s3.krish.cache;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mini_s3.krish.metrics.StorageMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresignedUrlService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${presign.secret:mini-s3-secret-key}")
    private String secret;

    @Value("${cache.presigned-url.ttl-seconds:900}")
    private long defaultTtlSeconds;

    private static final String PREFIX = "presign:";

    // Add field
    private final StorageMetrics metrics;




    // ── Generate presigned URL token ──────────────────────────────────────────

    public PresignedUrlResponse generate(String bucketName,
                                         String objectKey,
                                         String operation,
                                         long expirySeconds) {
        long expiresAt = Instant.now().getEpochSecond() + expirySeconds;
        String tokenId = UUID.randomUUID().toString();

        // Payload: tokenId|bucket|key|operation|expiresAt
        String payload = String.join("|",
                tokenId, bucketName, objectKey, operation,
                String.valueOf(expiresAt));

        // Sign with HMAC-SHA256
        String signature = sign(payload);

        // Token = base64(payload) + "." + signature
        String encodedPayload = Base64.getUrlEncoder()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String token = encodedPayload + "." + signature;

        // Store in Redis with TTL
        PresignedToken stored = PresignedToken.builder()
                .tokenId(tokenId)
                .bucketName(bucketName)
                .objectKey(objectKey)
                .operation(operation)
                .expiresAt(expiresAt)
                .used(false)
                .build();

        redisTemplate.opsForValue().set(
                PREFIX + tokenId, stored,
                expirySeconds, TimeUnit.SECONDS);

        String presignedUrl = "/presigned/access/" + token;

        log.info("Generated presigned URL for {}/{} op={} ttl={}s",
                bucketName, objectKey, operation, expirySeconds);
        metrics.getPresignedUrlGeneratedCounter().increment();

        return new PresignedUrlResponse(presignedUrl, token,
                expiresAt, operation, expirySeconds);
    }

    // ── Validate presigned token ──────────────────────────────────────────────

    public PresignedToken validate(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid token format");
            }

            String encodedPayload = parts[0];
            String signature     = parts[1];

            // Decode payload
            String payload = new String(
                    Base64.getUrlDecoder().decode(encodedPayload),
                    StandardCharsets.UTF_8);

            // Verify signature
            String expectedSig = sign(payload);
            if (!expectedSig.equals(signature)) {
                throw new IllegalArgumentException("Invalid token signature");
            }

            // Parse payload
            String[] fields = payload.split("\\|");
            String tokenId   = fields[0];
            long expiresAt   = Long.parseLong(fields[4]);

            // Check expiry
            if (Instant.now().getEpochSecond() > expiresAt) {
                throw new IllegalArgumentException("Token has expired");
            }

            // Check Redis — token must still exist
            Object stored = redisTemplate.opsForValue().get(
                    PREFIX + tokenId);
            if (!(stored instanceof PresignedToken presignedToken)) {
                throw new IllegalArgumentException(
                        "Token not found or already used");
            }
            // In validate() — after successful validation
            metrics.getPresignedUrlAccessedCounter().increment();

            return presignedToken;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Token validation failed: " + e.getMessage());
        }
    }

    // ── HMAC-SHA256 signing ───────────────────────────────────────────────────

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(
                    payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }
}
