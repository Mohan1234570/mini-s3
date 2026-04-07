package mini_s3.krish.object.repo;


import mini_s3.krish.object.entity.StorageObject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StorageObjectRepository extends JpaRepository<StorageObject, String> {

    Optional<StorageObject> findByBucketNameAndObjectKey(String bucketName, String objectKey);

    List<StorageObject> findAllByBucketName(String bucketName);

    //boolean existsByBucketNameAndObjectKey(String bucketName, String objectKey);

    void deleteByBucketNameAndObjectKey(String bucketName, String objectKey);
}