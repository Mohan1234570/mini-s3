package mini_s3.krish.object.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mini_s3.krish.bucket.repo.BucketRepository;
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

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class StorageObjectService {

    private final StorageObjectRepository objectRepository;
    private final BucketRepository bucketRepository;
    private final StorageProperties storageProperties;
    private final ReplicationManager replicationManager;

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
        // In StorageObjectService.uploadObject() — ADD these lines after save
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

        StorageObject obj = objectRepository
                .findByBucketNameAndObjectKey(bucketName, objectKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Object not found: " + bucketName + "/" + objectKey));

        Path filePath = Paths.get(obj.getStoragePath());
        if (!Files.exists(filePath)) {
            throw new IllegalStateException(
                    "File missing on disk: " + filePath);
        }

        Resource resource = new UrlResource(filePath.toUri());
        return new ObjectDownload(resource, obj.getContentType(),
                obj.getEtag(), obj.getSize());
    }

    // ─── List objects ──────────────────────────────────────────────────────────

    public List<StorageObject> listObjects(String bucketName) {
        bucketRepository.findByName(bucketName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Bucket not found: " + bucketName));
        return objectRepository.findAllByBucketName(bucketName);
    }

    // ─── Delete ────────────────────────────────────────────────────────────────

    public void deleteObject(String bucketName, String objectKey) throws IOException {
        StorageObject obj = objectRepository
                .findByBucketNameAndObjectKey(bucketName, objectKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Object not found: " + bucketName + "/" + objectKey));

        // Delete from disk
        Files.deleteIfExists(Paths.get(obj.getStoragePath()));

        // Delete metadata from DB
        objectRepository.deleteByBucketNameAndObjectKey(bucketName, objectKey);
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
