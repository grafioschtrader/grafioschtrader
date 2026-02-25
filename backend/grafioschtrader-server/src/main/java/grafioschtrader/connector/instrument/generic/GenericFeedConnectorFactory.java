package grafioschtrader.connector.instrument.generic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import grafiosch.entities.ConnectorApiKey;
import grafiosch.repository.ConnectorApiKeyJpaRepository;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.GenericConnectorDef;
import grafioschtrader.repository.GenericConnectorDefJpaRepository;
import grafioschtrader.repository.SecuritycurrencyService;

/**
 * Factory service that loads all active GenericConnectorDef rows from the database at application startup, creates
 * GenericFeedConnector instances, and registers them in the SecuritycurrencyService feedConnectorbeans list so they
 * appear alongside programmed connectors. Provides a reload() method for runtime re-registration after admin changes.
 */
@Service
public class GenericFeedConnectorFactory {

  private static final Logger log = LoggerFactory.getLogger(GenericFeedConnectorFactory.class);

  @Autowired
  private GenericConnectorDefJpaRepository genericConnectorDefJpaRepository;

  @Autowired
  private ConnectorApiKeyJpaRepository connectorApiKeyJpaRepository;

  @Autowired
  private List<SecuritycurrencyService<?, ?>> securitycurrencyServices;

  private final List<GenericFeedConnector> registeredConnectors = new ArrayList<>();

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    registerAll();
  }

  /**
   * Reloads all generic connectors from the database, removing previously registered instances and adding fresh ones.
   * Called by the admin REST endpoint after connector configurations are changed.
   */
  public synchronized void reload() {
    unregisterAll();
    registerAll();
  }

  private void registerAll() {
    List<GenericConnectorDef> defs = genericConnectorDefJpaRepository.findByActivatedTrue();
    log.info("Loading {} active generic feed connector definitions", defs.size());

    for (GenericConnectorDef def : defs) {
      try {
        String apiKey = null;
        if (def.isNeedsApiKey() && !def.hasAutoToken()) {
          Optional<ConnectorApiKey> keyOpt = connectorApiKeyJpaRepository.findById(def.getShortId());
          if (keyOpt.isPresent()) {
            apiKey = keyOpt.get().getApiKey();
          }
        }

        GenericFeedConnector connector = new GenericFeedConnector(def, apiKey);
        registeredConnectors.add(connector);

        for (SecuritycurrencyService<?, ?> service : securitycurrencyServices) {
          service.getFeedConnectors().add(connector);
        }
        log.info("Registered generic connector: {} ({})", def.getShortId(), def.getReadableName());
      } catch (Exception e) {
        log.error("Failed to register generic connector '{}': {}", def.getShortId(), e.getMessage(), e);
      }
    }
  }

  private void unregisterAll() {
    for (GenericFeedConnector connector : registeredConnectors) {
      for (SecuritycurrencyService<?, ?> service : securitycurrencyServices) {
        service.getFeedConnectors().remove(connector);
      }
    }
    registeredConnectors.clear();
  }
}
