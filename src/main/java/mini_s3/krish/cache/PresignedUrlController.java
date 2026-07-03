package mini_s3.krish.cache;


import lombok.RequiredArgsConstructor;
import mini_s3.krish.object.service.StorageObjectService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PresignedUrlController {

    private final PresignedUrlService presignedUrlService;
    private final StorageObjectService objectService;

    // POST /presign/{bucket}/{key} — generate presigned URL
    @PostMapping("/presign/{bucket}/{key}")
    public ResponseEntity<PresignedUrlResponse> generatePresignedUrl(
            @PathVariable String bucket,
            @PathVariable String key,
            @RequestParam(defaultValue = "GET") String operation,
            @RequestParam(defaultValue = "900") long expirySeconds) {

        PresignedUrlResponse response = presignedUrlService.generate(
                bucket, key, operation, expirySeconds);
        return ResponseEntity.ok(response);
    }

    // GET /presigned/access/{token} — access object via presigned URL
    @GetMapping("/presigned/access/{token}")
    public ResponseEntity<Resource> accessViaPresignedUrl(
            @PathVariable String token) throws IOException {

        PresignedToken validated = presignedUrlService.validate(token);

        if (!"GET".equals(validated.getOperation())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        StorageObjectService.ObjectDownload download =
                objectService.downloadObject(
                        validated.getBucketName(),
                        validated.getObjectKey());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        download.contentType()))
                .contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + validated.getObjectKey() + "\"")
                .header("ETag", download.etag())
                .body(download.resource());
    }

    // GET /presign/validate/{token} — check if token is valid
    @GetMapping("/presign/validate/{token}")
    public ResponseEntity<Map<String, Object>> validateToken(
            @PathVariable String token) {
        try {
            PresignedToken validated = presignedUrlService.validate(token);
            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "bucket", validated.getBucketName(),
                    "objectKey", validated.getObjectKey(),
                    "operation", validated.getOperation(),
                    "expiresAt", validated.getExpiresAt()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(Map.of(
                    "valid", false,
                    "reason", e.getMessage()
            ));
        }
    }
}
