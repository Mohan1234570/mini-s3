package mini_s3.object;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import mini_s3.krish.bucket.entity.Bucket;
import mini_s3.krish.bucket.repo.BucketRepository;
import mini_s3.krish.cache.MetadataCacheService;
import mini_s3.krish.cache.VersioningService;
import mini_s3.krish.metrics.StorageMetrics;
import mini_s3.krish.object.config.StorageProperties;
import mini_s3.krish.object.entity.StorageObject;
import mini_s3.krish.object.repo.StorageObjectRepository;
import mini_s3.krish.object.service.StorageObjectService;
import mini_s3.krish.replication.ReplicationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageObjectServiceTest {

    @Mock BucketRepository bucketRepository;
    @Mock StorageObjectRepository objectRepository;
    @Mock ReplicationManager replicationManager;
    @Mock MetadataCacheService cacheService;
    @Mock VersioningService versioningService;
    @Mock StorageMetrics metrics;

    // Timer and Counter mocks — needed because uploadObject calls these
    @Mock Timer uploadTimer;
    @Mock Timer downloadTimer;
    @Mock Timer metadataLookupTimer;
    @Mock Counter uploadCounter;
    @Mock Counter downloadCounter;
    @Mock Counter deletionCounter;

    StorageProperties storageProperties;
    StorageObjectService objectService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        storageProperties = new StorageProperties();
        storageProperties.setBasePath(tempDir.toString());

        // Wire up timer mocks — recordCallable must actually execute the lambda
        when(metrics.getUploadTimer()).thenReturn(uploadTimer);
        when(metrics.getDownloadTimer()).thenReturn(downloadTimer);
        when(metrics.getMetadataLookupTimer()).thenReturn(metadataLookupTimer);

        // Make timers execute the lambda they receive
        when(uploadTimer.recordCallable(any())).thenAnswer(inv -> {
            java.util.concurrent.Callable<?> callable = inv.getArgument(0);
            return callable.call();
        });
        when(downloadTimer.recordCallable(any())).thenAnswer(inv -> {
            java.util.concurrent.Callable<?> callable = inv.getArgument(0);
            return callable.call();
        });
        when(metadataLookupTimer.recordCallable(any())).thenAnswer(inv -> {
            java.util.concurrent.Callable<?> callable = inv.getArgument(0);
            return callable.call();
        });

        // Wire up counter mocks
        when(metrics.getUploadCounter()).thenReturn(uploadCounter);
        when(metrics.getDownloadCounter()).thenReturn(downloadCounter);
        when(metrics.getDeleteCounter()).thenReturn(deletionCounter);
        when(metrics.getCacheHitCounter()).thenReturn(mock(Counter.class));
        when(metrics.getCacheMissCounter()).thenReturn(mock(Counter.class));

        // Wire up AtomicLong gauges
        when(metrics.getActiveUploadCount())
                .thenReturn(new AtomicLong(0));
        when(metrics.getTotalBytesStored())
                .thenReturn(new AtomicLong(0));
        when(metrics.getTotalObjectCount())
                .thenReturn(new AtomicLong(0));

        // Build service with all 7 dependencies
        objectService = new StorageObjectService(
                objectRepository,
                bucketRepository,
                storageProperties,
                replicationManager,
                cacheService,
                versioningService,
                metrics
        );
    }

    // ── Upload tests ──────────────────────────────────────────────────────────

    @Test
    void uploadObject_success_savesMetadataAndReturnsEtag() throws IOException {
        when(bucketRepository.findByName("photos"))
                .thenReturn(Optional.of(new Bucket()));
        when(objectRepository.findByBucketNameAndObjectKey(any(), any()))
                .thenReturn(Optional.empty());
        when(objectRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "hello".getBytes());

        StorageObject result = objectService.uploadObject(
                "photos", "photo.jpg", file);

        assertThat(result.getEtag()).isNotBlank();
        assertThat(result.getSize()).isEqualTo(5L);
        assertThat(result.getContentType()).isEqualTo("image/jpeg");
        verify(objectRepository).save(any());
    }

    @Test
    void uploadObject_success_triggersReplication() throws IOException {
        when(bucketRepository.findByName("photos"))
                .thenReturn(Optional.of(new Bucket()));
        when(objectRepository.findByBucketNameAndObjectKey(any(), any()))
                .thenReturn(Optional.empty());
        when(objectRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "hello".getBytes());

        objectService.uploadObject("photos", "photo.jpg", file);

        // Replication must be triggered after every successful upload
        verify(replicationManager).replicateObject(
                eq("photos"), eq("photo.jpg"),
                anyString(), eq("node-1"),
                anyLong(), anyString());
    }

    @Test
    void uploadObject_success_cachesMetadata() throws IOException {
        when(bucketRepository.findByName("photos"))
                .thenReturn(Optional.of(new Bucket()));
        when(objectRepository.findByBucketNameAndObjectKey(any(), any()))
                .thenReturn(Optional.empty());
        when(objectRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "hello".getBytes());

        objectService.uploadObject("photos", "photo.jpg", file);

        // Redis cache must be populated after upload
        verify(cacheService).put(any());
    }

    @Test
    void uploadObject_success_createsVersion() throws IOException {
        when(bucketRepository.findByName("photos"))
                .thenReturn(Optional.of(new Bucket()));
        when(objectRepository.findByBucketNameAndObjectKey(any(), any()))
                .thenReturn(Optional.empty());
        when(objectRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "hello".getBytes());

        objectService.uploadObject("photos", "photo.jpg", file);

        // Versioning must create a new version on every upload
        verify(versioningService).createVersion(
                eq("photos"), eq("photo.jpg"),
                anyString(), anyLong(),
                anyString(), anyString());
    }

    @Test
    void uploadObject_success_incrementsMetricsCounters() throws IOException {
        when(bucketRepository.findByName("photos"))
                .thenReturn(Optional.of(new Bucket()));
        when(objectRepository.findByBucketNameAndObjectKey(any(), any()))
                .thenReturn(Optional.empty());
        when(objectRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "hello".getBytes());

        objectService.uploadObject("photos", "photo.jpg", file);

        verify(uploadCounter).increment();
    }

    @Test
    void uploadObject_bucketNotFound_throwsException() {
        when(bucketRepository.findByName("nonexistent"))
                .thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "data".getBytes());

        assertThatThrownBy(() ->
                objectService.uploadObject("nonexistent", "test.txt", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bucket not found");
    }

    // ── Delete tests ──────────────────────────────────────────────────────────

    @Test
    void deleteObject_objectNotFound_throwsException() {
        when(objectRepository.findByBucketNameAndObjectKey(any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                objectService.deleteObject("photos", "missing.jpg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Object not found");
    }

    @Test
    void deleteObject_success_evictsCacheAndIncrementsCounter()
            throws IOException {
        StorageObject obj = StorageObject.builder()
                .bucketName("photos")
                .objectKey("photo.jpg")
                .storagePath(tempDir.resolve("photo.jpg").toString())
                .size(100L)
                .etag("abc")
                .contentType("image/jpeg")
                .build();

        when(objectRepository.findByBucketNameAndObjectKey(
                "photos", "photo.jpg"))
                .thenReturn(Optional.of(obj));

        objectService.deleteObject("photos", "photo.jpg");

        verify(cacheService).evict("photos", "photo.jpg");
        verify(deletionCounter).increment();
    }
}