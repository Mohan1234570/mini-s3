package mini_s3.krish.repo;

import mini_s3.krish.entity.Bucket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BucketRepository extends JpaRepository<Bucket, String> {
    Optional<Bucket> findByName(String name);
    boolean existsByName(String name);
}
