package mini_s3.krish.bucket.service;


import lombok.RequiredArgsConstructor;
import mini_s3.krish.bucket.entity.Bucket;
import mini_s3.krish.bucket.repo.BucketRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BucketService {

    private final BucketRepository bucketRepository;

    public Bucket createBucket(String name, String owner) {
        if (bucketRepository.existsByName(name)) {
            throw new IllegalArgumentException("Bucket already exists: " + name);
        }
        Bucket bucket = Bucket.builder()
                .name(name)
                .owner(owner)
                .build();
        return bucketRepository.save(bucket);
    }

    public List<Bucket> listBuckets() {
        return bucketRepository.findAll();
    }

    public void deleteBucket(String name) {
        Bucket bucket = bucketRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + name));
        bucketRepository.delete(bucket);
    }

    public Bucket getBucket(String name) {
        return bucketRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + name));
    }
}
