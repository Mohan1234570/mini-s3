package mini_s3.krish.object.entity;


import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "storage_objects")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StorageObject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String bucketName;

    @Column(nullable = false)
    private String objectKey;

    @Column(nullable = false)
    private Long size;           // file size in bytes

    @Column(nullable = false)
    private String contentType;  // e.g. image/jpeg, application/pdf

    @Column(nullable = false)
    private String etag;         // MD5 checksum of the file

    @Column(nullable = false)
    private String storagePath;  // where the file lives on disk

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
