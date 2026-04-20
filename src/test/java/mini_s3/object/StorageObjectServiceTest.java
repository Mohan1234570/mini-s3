package mini_s3.object;


import mini_s3.krish.bucket.entity.Bucket;
import mini_s3.krish.bucket.repo.BucketRepository;
import mini_s3.krish.object.config.StorageProperties;
import mini_s3.krish.object.entity.StorageObject;
import mini_s3.krish.object.repo.StorageObjectRepository;
import mini_s3.krish.object.service.StorageObjectService;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageObjectServiceTest {

    @Mock
    BucketRepository bucketRepository;

    @Mock
    StorageObjectRepository objectRepository;

    @Mock
    mini_s3.krish.replication.ReplicationManager replicationManager; // ✅ NEW

    StorageProperties storageProperties;
    StorageObjectService objectService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        storageProperties = new StorageProperties();
        storageProperties.setBasePath(tempDir.toString());

        objectService = new StorageObjectService(
                objectRepository,
                bucketRepository,
                storageProperties,
                replicationManager   // ✅ FIXED
        );
    }

    @Test
    void uploadObject_success_savesMetadataAndReturnsEtag() throws IOException {
        when(bucketRepository.findByName("photos"))
                .thenReturn(Optional.of(new Bucket()));
        when(objectRepository.findByBucketNameAndObjectKey(any(), any()))
                .thenReturn(Optional.empty());
        when(objectRepository.save(any())).thenAnswer(i -> i.getArgument(0));

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

    @Test
    void deleteObject_objectNotFound_throwsException() {
        when(objectRepository.findByBucketNameAndObjectKey(any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                objectService.deleteObject("photos", "missing.jpg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Object not found");
    }
}