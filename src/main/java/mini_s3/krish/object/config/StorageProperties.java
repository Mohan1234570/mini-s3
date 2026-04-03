package mini_s3.krish.object.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "storage")
@Getter @Setter
public class StorageProperties {
    private String basePath = "./storage-data";
}
