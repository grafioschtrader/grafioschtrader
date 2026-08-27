package grafiosch.integration.gtnet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import grafiosch.entities.GTNetEntity;
import grafiosch.gtnet.ExchangeKindTypeRegistry;
import jakarta.annotation.PostConstruct;

/**
 * Registers this host's {@link IntegrationExchangeKindType} constants with both places that need them.
 *
 * <p>
 * The two registries are independent and a host that fills only one is subtly broken. The Spring bean
 * {@link ExchangeKindTypeRegistry} drives lookups, validation, the exchange-log query parameter and the
 * {@code exchangeKindTypes} list the setup table builds its columns from. The static array inside {@link GTNetEntity}
 * drives the other direction: it resolves an inbound {@code entityKind} JSON string to its byte value, and without it
 * {@code GTNetEntity.setEntityKind} throws "Unknown entityKind".
 * </p>
 *
 * <p>
 * Grafioschtrader does the same in two places - {@code GTStartUp.registerExchangeKindTypes()} and
 * {@code GTNetExchangeKindConfig} - which is why both calls appear together here.
 * </p>
 */
@Configuration
public class IntegrationExchangeKindConfig {

  @Autowired
  private ExchangeKindTypeRegistry exchangeKindTypeRegistry;

  @PostConstruct
  public void registerExchangeKindTypes() {
    for (IntegrationExchangeKindType kind : IntegrationExchangeKindType.values()) {
      exchangeKindTypeRegistry.registerExchangeKind(kind);
    }
    GTNetEntity.registerExchangeKindTypes(IntegrationExchangeKindType.values());
  }
}
