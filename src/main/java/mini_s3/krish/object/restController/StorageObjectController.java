package mini_s3.krish.object.restController;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import mini_s3.krish.bucket.dto.ApiResponse;
import mini_s3.krish.cache.ObjectVersion;
import mini_s3.krish.cache.VersioningService;
import mini_s3.krish.object.entity.StorageObject;
import mini_s3.krish.object.service.StorageObjectService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/objects")
public class StorageObjectController {

    private final StorageObjectService objectService;
    // Inject versioning service
    private final VersioningService versioningService;

    // 🔹 Utility method to extract key (VERY IMPORTANT)
    private String extractKey(HttpServletRequest request, String bucket) {

        String prefix = "/objects/" + bucket + "/";

        String uri = request.getRequestURI();

        if (!uri.startsWith(prefix)) {
            throw new RuntimeException("Invalid object path");
        }

        return uri.substring(prefix.length());
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


    @GetMapping(value = "/{bucket}/**", params = "version")
    public ResponseEntity<Resource> downloadVersion(
            @PathVariable String bucket,
            @RequestParam Integer version,
            HttpServletRequest request) throws IOException {

        String key = extractKey(request, bucket);

        ObjectVersion v =
                versioningService.getVersion(bucket, key, version);

        Path filePath = Paths.get(v.getStoragePath());

        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(filePath.toUri());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(v.getContentType()))
                .contentLength(v.getSize())
                .header("ETag", v.getEtag())
                .header("X-Version",
                        String.valueOf(v.getVersionNumber()))
                .body(resource);
    }

    @GetMapping(value = "/{bucket}/**", params = "versions")
    public ResponseEntity<List<ObjectVersion>> listVersions(
            @PathVariable String bucket,
            HttpServletRequest request) {

        String key = extractKey(request, bucket);

        return ResponseEntity.ok(
                versioningService.listVersions(bucket, key));
    }
}