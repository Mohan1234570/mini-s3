package mini_s3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class MiniS3Application {

	public static void main(String[] args) {
		SpringApplication.run(MiniS3Application.class, args);
	}

}
