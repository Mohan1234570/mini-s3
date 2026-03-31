package mini_s3.krish.restController;

import lombok.RequiredArgsConstructor;
import mini_s3.krish.dto.ApiResponse;
import mini_s3.krish.entity.Bucket;
import mini_s3.krish.service.BucketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/buckets")
@RequiredArgsConstructor
public class BucketController {

    private final BucketService bucketService;

    @PutMapping("/{name}")
    public ResponseEntity<ApiResponse<Bucket>> createBucket(
            @PathVariable String name,
            @RequestHeader(value = "X-Owner", defaultValue = "mohan") String owner) {

        Bucket bucket = bucketService.createBucket(name, owner);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Bucket created successfully", bucket)
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Bucket>>> listBuckets() {

        List<Bucket> buckets = bucketService.listBuckets();

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Buckets fetched successfully", buckets)
        );
    }

    @GetMapping("/{name}")
    public ResponseEntity<ApiResponse<Bucket>> getBucket(@PathVariable String name) {

        Bucket bucket = bucketService.getBucket(name);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Bucket fetched successfully", bucket)
        );
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<ApiResponse<Void>> deleteBucket(@PathVariable String name) {

        bucketService.deleteBucket(name);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Bucket deleted successfully", null)
        );
    }
}