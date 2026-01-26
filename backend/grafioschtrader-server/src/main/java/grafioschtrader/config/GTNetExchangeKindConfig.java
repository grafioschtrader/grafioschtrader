package grafioschtrader.config;

import org.springframework.context.annotation.Configuration;

import grafiosch.entities.GTNetEntity;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import jakarta.annotation.PostConstruct;

/**
 * Configuration class that registers GTNet exchange kind types during application startup.
 * This enables JSON deserialization of entity kind string values (e.g., "HISTORICAL_PRICES")
 * to their corresponding byte values in GTNetEntity.
 */
@Configuration
public class GTNetExchangeKindConfig {

  @PostConstruct
  public void registerExchangeKindTypes() {
    GTNetEntity.registerExchangeKindTypes(GTNetExchangeKindType.values());
  }
}
