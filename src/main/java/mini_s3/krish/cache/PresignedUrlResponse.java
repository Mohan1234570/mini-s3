package mini_s3.krish.cache;


import lombok.*;

@Getter @AllArgsConstructor
public class PresignedUrlResponse {
    private String presignedUrl;
    private String token;
    private long expiresAt;
    private String operation;
    private long ttlSeconds;
}
