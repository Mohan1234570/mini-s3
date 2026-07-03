package mini_s3.krish.metrics;

import lombok.RequiredArgsConstructor;
import mini_s3.krish.router.ConsistentHashRouter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final StorageMetrics metrics;
    private final ConsistentHashRouter router;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(Map.of(

                "throughput", Map.of(
                        "uploads",   metrics.getUploadCounter().count(),
                        "downloads", metrics.getDownloadCounter().count(),
                        "deletes",   metrics.getDeleteCounter().count()
                ),

                "storage", Map.of(
                        "totalBytesStored", metrics.getTotalBytesStored().get(),
                        "totalObjects",     metrics.getTotalObjectCount().get(),
                        "activeUploads",    metrics.getActiveUploadCount().get()
                ),

                "cache", Map.of(
                        "hits",    metrics.getCacheHitCounter().count(),
                        "misses",  metrics.getCacheMissCounter().count(),
                        "hitRate", calculateHitRate()
                ),

                "replication", Map.of(
                        "success",      metrics.getReplicationSuccessCounter().count(),
                        "failures",     metrics.getReplicationFailureCounter().count(),
                        "healthyNodes", router.getHealthyNodes().size()
                ),

                "presignedUrls", Map.of(
                        "generated", metrics.getPresignedUrlGeneratedCounter().count(),
                        "accessed",  metrics.getPresignedUrlAccessedCounter().count()
                ),

                "latency", Map.of(
                        "uploadP99Ms",        getP99Ms(metrics.getUploadTimer()),
                        "downloadP99Ms",      getP99Ms(metrics.getDownloadTimer()),
                        "metadataCacheP99Ms", getP99Ms(metrics.getMetadataLookupTimer()),
                        "replicationP99Ms",   getP99Ms(metrics.getReplicationTimer())
                )
        ));
    }

    private double calculateHitRate() {
        double hits   = metrics.getCacheHitCounter().count();
        double misses = metrics.getCacheMissCounter().count();
        double total  = hits + misses;
        return total == 0 ? 0.0 :
                Math.round((hits / total) * 10000.0) / 100.0;
    }

    private double getP99Ms(io.micrometer.core.instrument.Timer timer) {
        return Math.round(
                timer.percentile(0.99,
                        java.util.concurrent.TimeUnit.MILLISECONDS)
                        * 100.0) / 100.0;
    }
}