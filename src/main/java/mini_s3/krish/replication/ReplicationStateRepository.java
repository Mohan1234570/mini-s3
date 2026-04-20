package mini_s3.krish.replication;


import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReplicationStateRepository
        extends JpaRepository<ReplicationState, String> {

    List<ReplicationState> findByBucketNameAndObjectKey(
            String bucketName, String objectKey);

    List<ReplicationState> findByStatus(ReplicationState.Status status);

    long countByBucketNameAndObjectKeyAndStatus(
            String bucketName, String objectKey, ReplicationState.Status status);
}
