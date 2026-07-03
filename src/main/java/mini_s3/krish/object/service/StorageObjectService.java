package mini_s3.krish.object.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mini_s3.krish.bucket.repo.BucketRepository;
import mini_s3.krish.cache.MetadataCacheService;
import mini_s3.krish.cache.ObjectMetadataCache;
import mini_s3.krish.cache.VersioningService;
import mini_s3.krish.metrics.StorageMetrics;
import mini_s3.krish.object.config.StorageProperties;
import mini_s3.krish.object.entity.StorageObject;
import mini_s3.krish.object.repo.StorageObjectRepository;
import mini_s3.krish.replication.ReplicationManager;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class StorageObjectService {

    private final StorageObjectRepository objectRepository;
    private final BucketRepository bucketRepository;
    private final StorageProperties storageProperties;
    private final ReplicationManager replicationManager;
    private final MetadataCacheService cacheService;
    private final VersioningService versioningService;
    private final StorageMetrics metrics;

    // ─── Upload ───────────────────────────────────────────────────────────────

    public StorageObject uploadObject(String bucketName,
                                      String objectKey,
                                      MultipartFile file) throws IOException {

        metrics.getActiveUploadCount().incrementAndGet();

        try {
            return metrics.getUploadTimer().recordCallable(() -> {
                try {
                    // 1. Verify bucket exists
                    bucketRepository.findByName(bucketName)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Bucket not found: " + bucketName));

                    // 2. Resolve destination path
                    Path bucketDir = Paths.get(
                            storageProperties.getBasePath(), bucketName);
                    Files.createDirectories(bucketDir);
                    Path destination = bucketDir.resolve(objectKey);
                    Files.createDirectories(destination.getParent());

                    // 3. Write file + compute MD5
                    String etag = writeFileAndComputeEtag(
                            file.getInputStream(), destination);

                    // 4. Save or update metadata in PostgreSQL
                    StorageObject obj = objectRepository
                            .findByBucketNameAndObjectKey(bucketName, objectKey)
                            .orElse(StorageObject.builder()
                                    .bucketName(bucketName)
                                    .objectKey(objectKey)
                                    .build());

                    obj.setSize(file.getSize());
                    obj.setContentType(file.getContentType() != null
                            ? file.getContentType() : "application/octet-stream");
                    obj.setEtag(etag);
                    obj.setStoragePath(destination.toString());

                    StorageObject saved = objectRepository.save(obj);
                    log.info("Uploaded object: {}/{} | size={}B | etag={}",
                            bucketName, objectKey, file.getSize(), etag);

                    // 5. Create version
                    versioningService.createVersion(
                            bucketName, objectKey,
                            destination.toString(),
                            file.getSize(), etag,
                            obj.getContentType());

                    // 6. Cache metadata in Redis
                    cacheService.put(ObjectMetadataCache.builder()
                            .id(saved.getId())
                            .bucketName(saved.getBucketName())
                            .objectKey(saved.getObjectKey())
                            .size(saved.getSize())
                            .contentType(saved.getContentType())
                            .etag(saved.getEtag())
                            .storagePath(saved.getStoragePath())
                            .createdAt(saved.getCreatedAt())
                            .build());

                    // 7. Trigger async replication
                    replicationManager.replicateObject(
                            bucketName, objectKey,
                            destination.toString(),
                            "node-1",
                            file.getSize(),
                            etag);

                    // 8. Update metrics counters
                    metrics.getUploadCounter().increment();
                    metrics.getTotalBytesStored().addAndGet(file.getSize());
                    metrics.getTotalObjectCount().incrementAndGet();
                    metrics.getActiveUploadCount().decrementAndGet();

                    return saved;

                } catch (Exception e) {
                    metrics.getActiveUploadCount().decrementAndGet();
                    throw e;
                }
            });
        } catch (IOException e) {
            throw e;                                    // rethrow IO as-is
        } catch (Exception e) {
            throw new IOException("Upload failed: " + e.getMessage(), e); // wrap others
        }
    }

    // ─── Download ─────────────────────────────────────────────────────────────

    public ObjectDownload downloadObject(String bucketName,
                                         String objectKey) throws IOException {
        try {
            return metrics.getDownloadTimer().recordCallable(() -> {
                try {
                    // Metadata lookup — timed separately
                    Optional<ObjectMetadataCache> cached =
                            metrics.getMetadataLookupTimer().recordCallable(() -> {
                                Optional<ObjectMetadataCache> result =
                                        cacheService.get(bucketName, objectKey);
                                if (result.isPresent()) {
                                    metrics.getCacheHitCounter().increment();
                                } else {
                                    metrics.getCacheMissCounter().increment();
                                }
                                return result;
                            });

                    String storagePath;
                    String contentType;
                    String etag;
                    long size;

                    if (cached.isPresent()) {
                        ObjectMetadataCache meta = cached.get();
                        storagePath = meta.getStoragePath();
                        contentType = meta.getContentType();
                        etag        = meta.getEtag();
                        size        = meta.getSize();
                        log.debug("Cache HIT for {}/{}", bucketName, objectKey);
                    } else {
                        StorageObject obj = objectRepository
                                .findByBucketNameAndObjectKey(bucketName, objectKey)
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "Object not found: "
                                                + bucketName + "/" + objectKey));

                        storagePath = obj.getStoragePath();
                        contentType = obj.getContentType();
                        etag        = obj.getEtag();
                        size        = obj.getSize();

                        cacheService.put(ObjectMetadataCache.builder()
                                .id(obj.getId())
                                .bucketName(obj.getBucketName())
                                .objectKey(obj.getObjectKey())
                                .size(obj.getSize())
                                .contentType(obj.getContentType())
                                .etag(obj.getEtag())
                                .storagePath(obj.getStoragePath())
                                .createdAt(obj.getCreatedAt())
                                .build());

                        log.debug("Cache MISS for {}/{}", bucketName, objectKey);
                    }

                    Path filePath = Paths.get(storagePath);
                    if (!Files.exists(filePath)) {
                        throw new IllegalStateException(
                                "File missing on disk: " + filePath);
                    }

                    Resource resource = new UrlResource(filePath.toUri());
                    metrics.getDownloadCounter().increment();

                    return new ObjectDownload(resource, contentType, etag, size);

                } catch (Exception e) {
                    throw e;
                }
            });
        } catch (IOException e) {
            throw e;                                      // rethrow IO as-is
        } catch (Exception e) {
            throw new IOException("Download failed: " + e.getMessage(), e); // wrap others
        }
    }

    // ─── List objects ─────────────────────────────────────────────────────────

    public List<StorageObject> listObjects(String bucketName) {
        bucketRepository.findByName(bucketName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Bucket not found: " + bucketName));
        return objectRepository.findAllByBucketName(bucketName);
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    public void deleteObject(String bucketName,
                             String objectKey) throws IOException {
        StorageObject obj = objectRepository
                .findByBucketNameAndObjectKey(bucketName, objectKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Object not found: " + bucketName + "/" + objectKey));

        Files.deleteIfExists(Paths.get(obj.getStoragePath()));
        objectRepository.deleteByBucketNameAndObjectKey(
                bucketName, objectKey);

        // Evict from Redis cache
        cacheService.evict(bucketName, objectKey);

        // Update metrics
        metrics.getDeleteCounter().increment();
        metrics.getTotalObjectCount().decrementAndGet();

        log.info("Deleted object: {}/{}", bucketName, objectKey);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String writeFileAndComputeEtag(InputStream inputStream,
                                           Path destination) throws IOException {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            try (DigestInputStream dis =
                         new DigestInputStream(inputStream, md5)) {
                Files.copy(dis, destination,
                        StandardCopyOption.REPLACE_EXISTING);
            }
            return HexFormat.of().formatHex(md5.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    // ─── Download response record ─────────────────────────────────────────────

    public record ObjectDownload(Resource resource,
                                 String contentType,
                                 String etag,
                                 Long size) {}
}