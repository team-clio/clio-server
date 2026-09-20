package ax.clio.agent.client;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ClioAgentProperties.class)
public class ClioAgentClientConfiguration {
	@Bean
	RestClient.Builder restClientBuilder(ObservationRegistry observationRegistry) {
		return RestClient.builder().observationRegistry(observationRegistry);
	}
}
