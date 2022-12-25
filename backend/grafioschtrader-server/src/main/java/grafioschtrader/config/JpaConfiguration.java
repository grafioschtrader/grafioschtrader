package grafioschtrader.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = { "grafioschtrader.repository" })
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaConfiguration {

  @Bean
  AuditorAware<Integer> auditorAware() {
    return new AuditorAwareImpl();
  }

}
