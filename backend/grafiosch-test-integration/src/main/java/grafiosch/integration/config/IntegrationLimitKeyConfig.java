package grafiosch.integration.config;

import org.springframework.context.annotation.Configuration;

import grafiosch.config.LimitKeyBaseConfig;
import jakarta.annotation.PostConstruct;

/**
 * Registers the lifetime limit keys of the reusable library with this host.
 *
 * <p>
 * It is another piece an application built on {@code grafiosch-base} and {@code grafiosch-server-base} has to supply
 * itself, which is what this module exists to demonstrate: the registry is filled by the application layer, so without
 * this class {@code POST /api/tenant/share} would resolve its cap as unlimited here while the same code is bounded in
 * Grafioschtrader. The library keys have no seed of their own in this host, so they still resolve as unlimited until a
 * row is written - but the key is at least listed and editable.
 * </p>
 */
@Configuration
public class IntegrationLimitKeyConfig {

  @PostConstruct
  void started() {
    LimitKeyBaseConfig.initialize();
  }
}
