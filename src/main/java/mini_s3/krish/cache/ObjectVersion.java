package mini_s3.krish.cache;


import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "object_versions",
        indexes = {
                @Index(name = "idx_version_bucket_key",
                        columnList = "bucketName,objectKey"),
                @Index(name = "idx_version_created",
                        columnList = "createdAt")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ObjectVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String bucketName;

    @Column(nullable = false)
    private String objectKey;

    @Column(nullable = false)
    private Integer versionNumber;   // 1, 2, 3, ...

    @Column(nullable = false)
    private String storagePath;      // path for this specific version

    @Column(nullable = false)
    private Long size;

    @Column(nullable = false)
    private String etag;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private Boolean isLatest;        // only one version is latest at a time

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt; // for lifecycle policy

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
