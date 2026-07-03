package mini_s3.krish.cache;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VersioningService {

    private final ObjectVersionRepository versionRepository;

    @Value("${versioning.lifecycle.retention-days:30}")
    private int retentionDays;

    // ── Create new version on upload ─────────────────────────────────────────

    @Transactional
    public ObjectVersion createVersion(String bucketName,
                                       String objectKey,
                                       String storagePath,
                                       long size,
                                       String etag,
                                       String contentType) {
        // Unmark all previous versions as non-latest
        versionRepository.unmarkAllLatest(bucketName, objectKey);

        // Get next version number
        int nextVersion = versionRepository
                .findMaxVersionNumber(bucketName, objectKey)
                .map(v -> v + 1)
                .orElse(1);

        // Copy file to versioned path: storagePath + ".v" + version
        String versionedPath = storagePath + ".v" + nextVersion;
        try {
            Path source = Paths.get(storagePath);
            Path dest   = Paths.get(versionedPath);
            Files.createDirectories(dest.getParent());
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("Could not copy versioned file: {}", e.getMessage());
            versionedPath = storagePath; // fallback to original path
        }

        ObjectVersion version = ObjectVersion.builder()
                .bucketName(bucketName)
                .objectKey(objectKey)
                .versionNumber(nextVersion)
                .storagePath(versionedPath)
                .size(size)
                .etag(etag)
                .contentType(contentType)
                .isLatest(true)
                .expiresAt(LocalDateTime.now()
                        .plusDays(retentionDays))
                .build();

        ObjectVersion saved = versionRepository.save(version);
        log.info("Created version {} for {}/{}", nextVersion,
                bucketName, objectKey);
        return saved;
    }

    // ── Get specific version ──────────────────────────────────────────────────

    public ObjectVersion getVersion(String bucketName,
                                    String objectKey,
                                    Integer versionNumber) {
        return versionRepository
                .findByBucketNameAndObjectKeyAndVersionNumber(
                        bucketName, objectKey, versionNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Version " + versionNumber + " not found for "
                                + bucketName + "/" + objectKey));
    }

    // ── Get latest version ────────────────────────────────────────────────────

    public ObjectVersion getLatestVersion(String bucketName,
                                          String objectKey) {
        return versionRepository
                .findByBucketNameAndObjectKeyAndIsLatestTrue(
                        bucketName, objectKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No versions found for "
                                + bucketName + "/" + objectKey));
    }

    // ── List all versions ─────────────────────────────────────────────────────

    public List<ObjectVersion> listVersions(String bucketName,
                                            String objectKey) {
        return versionRepository
                .findByBucketNameAndObjectKeyOrderByVersionNumberDesc(
                        bucketName, objectKey);
    }

    // ── Lifecycle — auto delete expired versions ──────────────────────────────

    @Scheduled(cron = "0 0 2 * * *") // runs at 2 AM every day
    @Transactional
    public void deleteExpiredVersions() {
        List<ObjectVersion> expired = versionRepository
                .findByExpiresAtBeforeAndIsLatestFalse(LocalDateTime.now());

        int deleted = 0;
        for (ObjectVersion version : expired) {
            try {
                Files.deleteIfExists(Paths.get(version.getStoragePath()));
                versionRepository.delete(version);
                deleted++;
            } catch (IOException e) {
                log.warn("Could not delete expired version file: {}",
                        version.getStoragePath());
            }
        }
        if (deleted > 0) {
            log.info("Lifecycle: deleted {} expired object versions", deleted);
        }
    }
}
