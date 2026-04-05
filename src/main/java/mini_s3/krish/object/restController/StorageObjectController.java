package mini_s3.krish.object.restController;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import mini_s3.krish.bucket.dto.ApiResponse;
import mini_s3.krish.object.entity.StorageObject;
import mini_s3.krish.object.service.StorageObjectService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/objects")
public class StorageObjectController {

    private final StorageObjectService objectService;

    // 🔹 Utility method to extract key (VERY IMPORTANT)
    private String extractKey(HttpServletRequest request, String bucket) {
        String path = request.getRequestURI();
        return path.substring(path.indexOf(bucket) + bucket.length() + 1);
    }

    @PostMapping("/{bucket}/**")
    public ResponseEntity<ApiResponse<StorageObject>> uploadObject(
            @PathVariable String bucket,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) throws IOException {

        String key = extractKey(request, bucket);

        StorageObject obj = objectService.uploadObject(bucket, key, file);

        return ResponseEntity.ok()
                .header("ETag", obj.getEtag())
                .body(new ApiResponse<>(true, "Object uploaded successfully", obj));
    }

    // GET /{bucket}/** — download object
    @GetMapping("/{bucket}/**")
    public ResponseEntity<Resource> downloadObject(
            @PathVariable String bucket,
            HttpServletRequest request) throws IOException {

        String key = extractKey(request, bucket);

        StorageObjectService.ObjectDownload download =
                objectService.downloadObject(bucket, key);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + key + "\"")
                .header("ETag", download.etag())
                .body(download.resource());
    }

    // GET /{bucket} — list objects
    @GetMapping("/{bucket}")
    public ResponseEntity<ApiResponse<List<StorageObject>>> listObjects(
            @PathVariable String bucket) {

        List<StorageObject> objects = objectService.listObjects(bucket);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Objects fetched successfully", objects)
        );
    }

    // DELETE /{bucket}/** — delete object
    @DeleteMapping("/{bucket}/**")
    public ResponseEntity<ApiResponse<Void>> deleteObject(
            @PathVariable String bucket,
            HttpServletRequest request) throws IOException {

        String key = extractKey(request, bucket);

        objectService.deleteObject(bucket, key);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Object deleted successfully", null)
        );
    }
}