package mini_s3.krish.router;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "storage.nodes")
@Getter
@Setter
public class NodeConfig {

    private List<NodeDefinition> list;

    @Getter
    @Setter
    public static class NodeDefinition {
        private String id;
        private String host;
        private int port;
    }
}
