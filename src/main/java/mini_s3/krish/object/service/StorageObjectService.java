package mini_s3.krish.object.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mini_s3.krish.bucket.repo.BucketRepository;
import mini_s3.krish.cache.MetadataCacheService;
import mini_s3.krish.cache.ObjectMetadataCache;
import mini_s3.krish.cache.VersioningService;
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

    // Add to existing fields in StorageObjectService
    private final MetadataCacheService cacheService;
    private final VersioningService versioningService;

    // ─── Upload ────────────────────────────────────────────────────────────────

    public StorageObject uploadObject(String bucketName,
                                      String objectKey,
                                      MultipartFile file) throws IOException {

        // 1. Verify bucket exists
        bucketRepository.findByName(bucketName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Bucket not found: " + bucketName));

        // 2. Resolve destination path: storage-data/{bucket}/{key}
        Path bucketDir = Paths.get(storageProperties.getBasePath(), bucketName);
        Files.createDirectories(bucketDir);
        Path destination = bucketDir.resolve(objectKey);
        // ADD THIS LINE (VERY IMPORTANT)
        Files.createDirectories(destination.getParent());

        // 3. Stream file to disk while computing MD5 checksum simultaneously
        String etag = writeFileAndComputeEtag(file.getInputStream(), destination);

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
        // Create a new version on every upload
        versioningService.createVersion(
                bucketName, objectKey,
                destination.toString(),
                file.getSize(), etag,
                obj.getContentType());

        // Cache the metadata in Redis
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

        // ← THIS IS MISSING — add it right here
        replicationManager.replicateObject(
                bucketName,
                objectKey,
                destination.toString(),
                "node-1",           // primary node
                file.getSize(),
                etag
        );
        return saved;
    
    }

    // ─── Download ──────────────────────────────────────────────────────────────

    public ObjectDownload downloadObject(String bucketName,
                                         String objectKey) throws IOException {
        // 1. Check Redis cache first — fast path
        Optional<ObjectMetadataCache> cached =
                cacheService.get(bucketName, objectKey);

        String storagePath;
        String contentType;
        String etag;
        long size;

        if (cached.isPresent()) {
            // Cache HIT — use cached metadata
            ObjectMetadataCache meta = cached.get();
            storagePath  = meta.getStoragePath();
            contentType  = meta.getContentType();
            etag         = meta.getEtag();
            size         = meta.getSize();
            System.out.println("CACHE HIT");
        } else {
            // Cache MISS — query PostgreSQL
            StorageObject obj = objectRepository
                    .findByBucketNameAndObjectKey(bucketName, objectKey)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Object not found: " + bucketName + "/" + objectKey));

            storagePath  = obj.getStoragePath();
            contentType  = obj.getContentType();
            etag         = obj.getEtag();
            size         = obj.getSize();

            // Populate cache for next request
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
        }

        Path filePath = Paths.get(storagePath);
        if (!Files.exists(filePath)) {
            throw new IllegalStateException("File missing: " + filePath);
        }

        Resource resource = new UrlResource(filePath.toUri());
        return new ObjectDownload(resource, contentType, etag, size);
    }

    // ─── List objects ──────────────────────────────────────────────────────────

    public List<StorageObject> listObjects(String bucketName) {
        bucketRepository.findByName(bucketName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Bucket not found: " + bucketName));
        return objectRepository.findAllByBucketName(bucketName);
    }

    // ─── Delete ────────────────────────────────────────────────────────────────

    public void deleteObject(String bucketName,
                             String objectKey) throws IOException {
        StorageObject obj = objectRepository
                .findByBucketNameAndObjectKey(bucketName, objectKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Object not found: " + bucketName + "/" + objectKey));

        Files.deleteIfExists(Paths.get(obj.getStoragePath()));
        objectRepository.deleteByBucketNameAndObjectKey(bucketName, objectKey);

        // Evict from Redis cache
        cacheService.evict(bucketName, objectKey);

        log.info("Deleted object: {}/{}", bucketName, objectKey);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private String writeFileAndComputeEtag(InputStream inputStream,
                                           Path destination) throws IOException {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            try (DigestInputStream dis = new DigestInputStream(inputStream, md5)) {
                Files.copy(dis, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return HexFormat.of().formatHex(md5.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    // ─── Inner record for download response ───────────────────────────────────

    public record ObjectDownload(Resource resource,
                                 String contentType,
                                 String etag,
                                 Long size) {}
}
