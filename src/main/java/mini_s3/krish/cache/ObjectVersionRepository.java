package mini_s3.krish.cache;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ObjectVersionRepository
        extends JpaRepository<ObjectVersion, String> {

    List<ObjectVersion> findByBucketNameAndObjectKeyOrderByVersionNumberDesc(
            String bucketName, String objectKey);

    Optional<ObjectVersion> findByBucketNameAndObjectKeyAndVersionNumber(
            String bucketName, String objectKey, Integer versionNumber);

    Optional<ObjectVersion> findByBucketNameAndObjectKeyAndIsLatestTrue(
            String bucketName, String objectKey);

    @Query("SELECT MAX(v.versionNumber) FROM ObjectVersion v " +
            "WHERE v.bucketName = :bucket AND v.objectKey = :key")
    Optional<Integer> findMaxVersionNumber(
            @Param("bucket") String bucket,
            @Param("key") String key);

    @Modifying
    @Query("UPDATE ObjectVersion v SET v.isLatest = false " +
            "WHERE v.bucketName = :bucket AND v.objectKey = :key")
    void unmarkAllLatest(
            @Param("bucket") String bucket,
            @Param("key") String key);

    // Lifecycle — find expired versions
    List<ObjectVersion> findByExpiresAtBeforeAndIsLatestFalse(
            LocalDateTime now);

    long countByBucketNameAndObjectKey(String bucketName, String objectKey);
}
