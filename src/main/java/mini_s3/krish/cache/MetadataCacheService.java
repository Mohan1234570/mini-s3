package mini_s3.krish.cache;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${cache.object-metadata.ttl-seconds:300}")
    private long ttlSeconds;

    private static final String PREFIX = "meta:";

    // ── Cache key ─────────────────────────────────────────────────────────────

    private String key(String bucket, String objectKey) {
        return PREFIX + bucket + ":" + objectKey;
    }

    // ── Put ───────────────────────────────────────────────────────────────────

    public void put(ObjectMetadataCache metadata) {
        String key = key(metadata.getBucketName(), metadata.getObjectKey());
        redisTemplate.opsForValue().set(key, metadata, ttlSeconds,
                TimeUnit.SECONDS);
        log.debug("Cached metadata for {}/{} (TTL={}s)",
                metadata.getBucketName(), metadata.getObjectKey(), ttlSeconds);
    }

    // ── Get ───────────────────────────────────────────────────────────────────

    public Optional<ObjectMetadataCache> get(String bucket, String objectKey) {
        String key = key(bucket, objectKey);
        Object value = redisTemplate.opsForValue().get(key);
        if (value instanceof ObjectMetadataCache cached) {
            log.debug("Cache HIT for {}/{}", bucket, objectKey);
            return Optional.of(cached);
        }
        log.debug("Cache MISS for {}/{}", bucket, objectKey);
        return Optional.empty();
    }

    // ── Evict ─────────────────────────────────────────────────────────────────

    public void evict(String bucket, String objectKey) {
        redisTemplate.delete(key(bucket, objectKey));
        log.debug("Evicted cache for {}/{}", bucket, objectKey);
    }

    // ── Evict all for bucket ──────────────────────────────────────────────────

    public void evictBucket(String bucket) {
        var keys = redisTemplate.keys(PREFIX + bucket + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.debug("Evicted {} cache entries for bucket {}",
                    keys.size(), bucket);
        }
    }
}
