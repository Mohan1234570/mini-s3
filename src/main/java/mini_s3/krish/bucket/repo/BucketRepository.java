package mini_s3.krish.bucket.repo;

import mini_s3.krish.bucket.entity.Bucket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BucketRepository extends JpaRepository<Bucket, String> {
    Optional<Bucket> findByName(String name);
    boolean existsByName(String name);
}
