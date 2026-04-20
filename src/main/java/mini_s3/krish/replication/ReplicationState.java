package mini_s3.krish.replication;


import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "replication_states")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReplicationState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String eventId;
    private String bucketName;
    private String objectKey;
    private String primaryNodeId;
    private String targetNodeId;

    @Enumerated(EnumType.STRING)
    private Status status;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public enum Status {
        PENDING, COMPLETED, FAILED
    }
}
