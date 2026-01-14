package grafioschtrader.gtnet;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.model.ConnectorHint;
import grafioschtrader.gtnet.model.ConnectorHint.ConnectorCapability;
import grafioschtrader.gtnet.model.SecurityGtnetLookupDTO;
import grafioschtrader.gtnet.model.SecurityGtnetLookupRequest;
import grafioschtrader.gtnet.model.SecurityGtnetLookupResponse;
import grafioschtrader.repository.GTNetJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Service for looking up security metadata from local database and GTNet peers.
 * Provides functionality to search for securities by ISIN, currency, and ticker symbol,
 * and converts them to instance-agnostic DTOs suitable for GTNet exchange.
 */
@Service
public class GTNetSecurityLookupService {

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private Map<String, IFeedConnector> feedConnectorMap;

  /**
   * Looks up security metadata matching the given search criteria.
   * Currently searches only the local database. GTNet peer querying will be added in Phase 3.
   *
   * @param request the search criteria
   * @return response containing matching securities and query statistics
   */
  public SecurityGtnetLookupResponse lookupSecurity(SecurityGtnetLookupRequest request) {
    List<SecurityGtnetLookupDTO> results = new ArrayList<>();

    // Validate request
    if (!request.isValid()) {
      SecurityGtnetLookupResponse response = new SecurityGtnetLookupResponse(results, 0, 0);
      response.addError("Invalid request: (ISIN or ticker) and currency are required");
      return response;
    }

    // Phase 1: Search local database
    results.addAll(findLocalSecurities(request));

    // TODO Phase 3: Query GTNet peers that support SECURITY_METADATA exchange
    int peersQueried = 0;
    int peersResponded = 0;

    return new SecurityGtnetLookupResponse(results, peersQueried, peersResponded);
  }

  /**
   * Searches the local database for securities matching the criteria.
   */
  private List<SecurityGtnetLookupDTO> findLocalSecurities(SecurityGtnetLookupRequest request) {
    List<Security> securities = new ArrayList<>();

    // Search by ISIN and currency (primary)
    if (request.getIsin() != null && !request.getIsin().isBlank()) {
      if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
        Security security = securityJpaRepository.findByIsinAndCurrency(
            request.getIsin(), request.getCurrency());
        if (security != null) {
          securities.add(security);
        }
      } else {
        securities.addAll(securityJpaRepository.findByIsin(request.getIsin()));
      }
    }

    // Search by ticker symbol if ISIN not provided or no results
    if (securities.isEmpty() && request.getTickerSymbol() != null && !request.getTickerSymbol().isBlank()) {
      List<Security> byTicker = securityJpaRepository.findByTickerSymbol(request.getTickerSymbol());
      if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
        byTicker.stream()
            .filter(s -> request.getCurrency().equals(s.getCurrency()))
            .forEach(securities::add);
      } else {
        securities.addAll(byTicker);
      }
    }

    // Convert to DTOs
    return securities.stream()
        .filter(s -> s.getIdTenantPrivate() == null) // Exclude private securities
        .map(this::toDTO)
        .toList();
  }

  /**
   * Converts a Security entity to an instance-agnostic DTO.
   */
  private SecurityGtnetLookupDTO toDTO(Security security) {
    SecurityGtnetLookupDTO dto = new SecurityGtnetLookupDTO();

    // Identification
    dto.setIsin(security.getIsin());
    dto.setCurrency(security.getCurrency());
    dto.setName(security.getName());
    dto.setTickerSymbol(security.getTickerSymbol());

    // Asset class (enum values, not IDs)
    if (security.getAssetClass() != null) {
      dto.setCategoryType(security.getAssetClass().getCategoryType());
      dto.setSpecialInvestmentInstrument(security.getAssetClass().getSpecialInvestmentInstrument());
    }

    // Stock exchange (MIC code for cross-instance mapping)
    if (security.getStockexchange() != null) {
      dto.setStockexchangeMic(security.getStockexchange().getMic());
      dto.setStockexchangeName(security.getStockexchange().getName());
    }

    // Security properties
    dto.setDenomination(security.getDenomination());
    dto.setDistributionFrequency(security.getDistributionFrequency());
    dto.setLeverageFactor(security.getLeverageFactor());
    dto.setProductLink(security.getProductLink());
    dto.setActiveFromDate(security.getActiveFromDate());
    dto.setActiveToDate(security.getActiveToDate());

    // Build connector hints
    dto.setConnectorHints(buildConnectorHints(security));

    // Source tracking (null for local, populated for peer responses)
    dto.setSourceDomain(null);

    return dto;
  }

  /**
   * Builds connector hints from a security's connector configuration.
   * Extracts the connector family from the ID and determines capabilities.
   */
  private List<ConnectorHint> buildConnectorHints(Security security) {
    List<ConnectorHint> hints = new ArrayList<>();

    // History connector
    if (security.getIdConnectorHistory() != null) {
      ConnectorHint hint = createConnectorHint(
          security.getIdConnectorHistory(),
          security.getUrlHistoryExtend(),
          ConnectorCapability.HISTORY);
      if (hint != null) {
        hints.add(hint);
      }
    }

    // Intraday connector
    if (security.getIdConnectorIntra() != null) {
      ConnectorHint hint = createConnectorHint(
          security.getIdConnectorIntra(),
          security.getUrlIntraExtend(),
          ConnectorCapability.INTRADAY);
      if (hint != null) {
        // Check if already have hint for this connector family
        hints.stream()
            .filter(h -> h.getConnectorFamily().equals(hint.getConnectorFamily()))
            .findFirst()
            .ifPresentOrElse(
                existing -> existing.getCapabilities().add(ConnectorCapability.INTRADAY),
                () -> hints.add(hint));
      }
    }

    // Dividend connector
    if (security.getIdConnectorDividend() != null) {
      ConnectorHint hint = createConnectorHint(
          security.getIdConnectorDividend(),
          security.getUrlDividendExtend(),
          ConnectorCapability.DIVIDEND);
      if (hint != null) {
        hints.stream()
            .filter(h -> h.getConnectorFamily().equals(hint.getConnectorFamily()))
            .findFirst()
            .ifPresentOrElse(
                existing -> existing.getCapabilities().add(ConnectorCapability.DIVIDEND),
                () -> hints.add(hint));
      }
    }

    // Split connector
    if (security.getIdConnectorSplit() != null) {
      ConnectorHint hint = createConnectorHint(
          security.getIdConnectorSplit(),
          security.getUrlSplitExtend(),
          ConnectorCapability.SPLIT);
      if (hint != null) {
        hints.stream()
            .filter(h -> h.getConnectorFamily().equals(hint.getConnectorFamily()))
            .findFirst()
            .ifPresentOrElse(
                existing -> existing.getCapabilities().add(ConnectorCapability.SPLIT),
                () -> hints.add(hint));
      }
    }

    return hints;
  }

  /**
   * Creates a connector hint from a connector ID.
   * Extracts the family name and checks if API key is required.
   */
  private ConnectorHint createConnectorHint(String connectorId, String urlExtension,
                                             ConnectorCapability capability) {
    if (connectorId == null || connectorId.isBlank()) {
      return null;
    }

    // Extract family from connector ID (e.g., "gt.datafeed.yahoo" -> "yahoo")
    String family = extractConnectorFamily(connectorId);
    if (family == null) {
      return null;
    }

    // Check if connector requires API key
    boolean requiresApiKey = checkRequiresApiKey(connectorId);

    Set<ConnectorCapability> capabilities = EnumSet.of(capability);

    return new ConnectorHint(family, capabilities, urlExtension, requiresApiKey);
  }

  /**
   * Extracts the connector family name from a full connector ID.
   */
  private String extractConnectorFamily(String connectorId) {
    if (connectorId.startsWith(BaseFeedConnector.ID_PREFIX)) {
      return connectorId.substring(BaseFeedConnector.ID_PREFIX.length());
    }
    return connectorId;
  }

  /**
   * Checks if a connector requires an API key.
   */
  private boolean checkRequiresApiKey(String connectorId) {
    IFeedConnector connector = feedConnectorMap.get(connectorId);
    if (connector != null) {
      // Check if connector has API key field (extends BaseFeedApiKeyConnector)
      return connector.getClass().getName().contains("ApiKey");
    }
    return false;
  }

  /**
   * Checks if there are accessible GTNet peers that support SECURITY_METADATA exchange.
   * A peer is considered accessible if:
   * <ul>
   *   <li>It has a SECURITY_METADATA entity with acceptRequest > 0</li>
   *   <li>The server is online (serverOnline = SOS_ONLINE = 1)</li>
   * </ul>
   *
   * @return true if at least one accessible peer supports security metadata exchange
   */
  public boolean hasAccessibleSecurityMetadataPeers() {
    return gtNetJpaRepository.countBySecurityMetadataAcceptingAndOnline() > 0;
  }
}
