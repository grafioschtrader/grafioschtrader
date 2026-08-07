package grafiosch.integration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import grafiosch.config.AuditorAwareImpl;

/**
 * Enables Spring Data JPA auditing, which every {@code Auditable} entity of {@code grafiosch-base} depends on: without
 * it an insert into an audited table such as {@code user_entity_change_limit} or {@code mail_setting_forward} fails
 * with {@code Column 'created_by' cannot be null}.
 *
 * <p>
 * This is one of the pieces an application built on the reusable libraries has to supply itself, which is what this
 * module exists to demonstrate. Repository scanning is deliberately left to Spring Boot's auto-configuration — adding
 * {@code @EnableJpaRepositories} here would replace that scan and the {@code grafiosch.integration.repository} package
 * would have to be listed explicitly.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class IntegrationJpaConfig {

  @Bean
  AuditorAware<Integer> auditorAware() {
    return new AuditorAwareImpl();
  }
}
