package mini_s3.krish.metrics;


import io.micrometer.core.instrument.*;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@Getter
public class StorageMetrics {

    // ── Counters ──────────────────────────────────────────────────────────────
    private final Counter uploadCounter;
    private final Counter downloadCounter;
    private final Counter deleteCounter;
    private final Counter replicationSuccessCounter;
    private final Counter replicationFailureCounter;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter presignedUrlGeneratedCounter;
    private final Counter presignedUrlAccessedCounter;

    // ── Timers ────────────────────────────────────────────────────────────────
    private final Timer uploadTimer;
    private final Timer downloadTimer;
    private final Timer metadataLookupTimer;
    private final Timer replicationTimer;

    // ── Gauges ────────────────────────────────────────────────────────────────
    private final AtomicLong totalBytesStored    = new AtomicLong(0);
    private final AtomicLong totalObjectCount    = new AtomicLong(0);
    private final AtomicLong activeUploadCount   = new AtomicLong(0);
    private final AtomicLong healthyNodeCount    = new AtomicLong(3);

    public StorageMetrics(MeterRegistry registry) {

        // Counters
        uploadCounter = Counter.builder("minis3.objects.uploaded")
                .description("Total objects uploaded")
                .register(registry);

        downloadCounter = Counter.builder("minis3.objects.downloaded")
                .description("Total objects downloaded")
                .register(registry);

        deleteCounter = Counter.builder("minis3.objects.deleted")
                .description("Total objects deleted")
                .register(registry);

        replicationSuccessCounter = Counter.builder("minis3.replication.success")
                .description("Successful replication events")
                .register(registry);

        replicationFailureCounter = Counter.builder("minis3.replication.failure")
                .description("Failed replication events")
                .register(registry);

        cacheHitCounter = Counter.builder("minis3.cache.hits")
                .description("Redis metadata cache hits")
                .register(registry);

        cacheMissCounter = Counter.builder("minis3.cache.misses")
                .description("Redis metadata cache misses")
                .register(registry);

        presignedUrlGeneratedCounter = Counter.builder("minis3.presign.generated")
                .description("Presigned URLs generated")
                .register(registry);

        presignedUrlAccessedCounter = Counter.builder("minis3.presign.accessed")
                .description("Presigned URLs accessed")
                .register(registry);

        // Timers — automatically tracks P50, P95, P99
        uploadTimer = Timer.builder("minis3.upload.duration")
                .description("Object upload duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        downloadTimer = Timer.builder("minis3.download.duration")
                .description("Object download duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        metadataLookupTimer = Timer.builder("minis3.metadata.lookup.duration")
                .description("Metadata lookup duration (cache vs DB)")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        replicationTimer = Timer.builder("minis3.replication.duration")
                .description("Replication event processing duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // Gauges — track current state
        Gauge.builder("minis3.storage.bytes.total",
                        totalBytesStored, AtomicLong::get)
                .description("Total bytes stored")
                .register(registry);

        Gauge.builder("minis3.objects.total",
                        totalObjectCount, AtomicLong::get)
                .description("Total object count")
                .register(registry);

        Gauge.builder("minis3.uploads.active",
                        activeUploadCount, AtomicLong::get)
                .description("Currently active uploads")
                .register(registry);

        Gauge.builder("minis3.nodes.healthy",
                        healthyNodeCount, AtomicLong::get)
                .description("Number of healthy storage nodes")
                .register(registry);
    }
}
