package grafioschtrader.gtnet.handler.impl;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetEntity;
import grafiosch.entities.GTNetMessage;
import grafiosch.entities.MultilanguageString;
import grafiosch.gtnet.MessageCategory;
import grafiosch.gtnet.handler.AbstractGTNetMessageHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.gtnet.handler.HandlerResult;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.model.ConnectorHint;
import grafioschtrader.gtnet.model.ConnectorHint.ConnectorCapability;
import grafioschtrader.gtnet.model.SecurityGtnetLookupDTO;
import grafioschtrader.gtnet.model.SubCategoryDetector;
import grafioschtrader.gtnet.model.msg.SecurityLookupMsg;
import grafioschtrader.gtnet.model.msg.SecurityLookupResponseMsg;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Handler for GT_NET_SECURITY_LOOKUP_SEL_C requests from remote instances.
 *
 * Processes security metadata lookup requests by searching the local database
 * for matching securities and returning them in an instance-agnostic format.
 *
 * @see GTNetMessageCodeType#GT_NET_SECURITY_LOOKUP_SEL_C
 */
@Component
public class SecurityLookupHandler extends AbstractGTNetMessageHandler {

  private static final Logger log = LoggerFactory.getLogger(SecurityLookupHandler.class);

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private Map<String, IFeedConnector> feedConnectorMap;

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_SEL_C;
  }

  @Override
  public MessageCategory getCategory() {
    return MessageCategory.REQUEST;
  }

  @Override
  public HandlerResult<GTNetMessage, MessageEnvelope> handle(GTNetMessageContext context) throws Exception {
    // Validate local GTNet configuration
    GTNet myGTNet = context.getMyGTNet();
    if (myGTNet == null) {
      return new HandlerResult.ProcessingError<>("NO_LOCAL_GTNET", "Local GTNet configuration not found");
    }

    // Check if this server accepts security metadata requests
    Optional<GTNetEntity> metadataEntity = myGTNet.getEntityByKind(GTNetExchangeKindType.SECURITY_METADATA.getValue());
    if (metadataEntity.isEmpty() || !metadataEntity.get().isAccepting()) {
      log.debug("Server not accepting security metadata requests");
      return createRejectedResponse(context, "NOT_ACCEPTING", "This server is not accepting security metadata requests");
    }

    // Store incoming message for logging
    GTNetMessage storedRequest = storeIncomingMessage(context);

    // Parse request payload
    if (!context.hasPayload()) {
      return createNotFoundResponse(context, storedRequest);
    }

    SecurityLookupMsg request = context.getPayloadAs(SecurityLookupMsg.class);
    if (request == null || !isValidRequest(request)) {
      return createNotFoundResponse(context, storedRequest);
    }

    log.debug("Received security lookup request: isin={}, currency={}, ticker={}",
        request.isin, request.currency, request.tickerSymbol);

    // Search local database
    List<SecurityGtnetLookupDTO> results = findLocalSecurities(request, context.getRemoteGTNet());

    if (results.isEmpty()) {
      return createNotFoundResponse(context, storedRequest);
    }

    // Build and return response
    return createSuccessResponse(context, storedRequest, results);
  }

  protected boolean isValidRequest(SecurityLookupMsg request) {
    boolean hasIdentifier = (request.isin != null && !request.isin.isBlank())
        || (request.tickerSymbol != null && !request.tickerSymbol.isBlank());
    return hasIdentifier;
  }

  protected List<SecurityGtnetLookupDTO> findLocalSecurities(SecurityLookupMsg request, GTNet remoteGTNet) {
    List<Security> securities = new ArrayList<>();

    // Search by ISIN and currency (primary)
    if (request.isin != null && !request.isin.isBlank()) {
      if (request.currency != null && !request.currency.isBlank()) {
        Security security = securityJpaRepository.findByIsinAndCurrency(request.isin, request.currency);
        if (security != null) {
          securities.add(security);
        }
      } else {
        securities.addAll(securityJpaRepository.findByIsin(request.isin));
      }
    }

    // Search by ticker symbol if ISIN not provided or no results
    if (securities.isEmpty() && request.tickerSymbol != null && !request.tickerSymbol.isBlank()) {
      List<Security> byTicker = securityJpaRepository.findByTickerSymbol(request.tickerSymbol);
      if (request.currency != null && !request.currency.isBlank()) {
        byTicker.stream()
            .filter(s -> request.currency.equals(s.getCurrency()))
            .forEach(securities::add);
      } else {
        securities.addAll(byTicker);
      }
    }

    // Convert to DTOs, excluding private securities
    return securities.stream()
        .filter(s -> s.getIdTenantPrivate() == null)
        .map(this::toDTO)
        .toList();
  }

  protected SecurityGtnetLookupDTO toDTO(Security security) {
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

      // Sub-category (multilingual text for geographical/sectoral grouping)
      MultilanguageString subCat = security.getAssetClass().getSubCategoryNLS();
      if (subCat != null && subCat.getMap() != null && !subCat.getMap().isEmpty()) {
        Map<String, String> subCatMap = new HashMap<>(subCat.getMap());
        dto.setSubCategoryNLS(subCatMap);
        // Detect categorization scheme (regional vs sector)
        dto.setSubCategoryScheme(SubCategoryDetector.detect(subCatMap));
      }
    }

    // Stock exchange (MIC code for cross-instance mapping)
    if (security.getStockexchange() != null) {
      dto.setStockexchangeMic(security.getStockexchange().getMic());
      dto.setStockexchangeName(security.getStockexchange().getName());
    }
    dto.setStockexchangeLink(security.getStockexchangeLink());

    // Security properties
    dto.setDenomination(security.getDenomination());
    dto.setDistributionFrequency(security.getDistributionFrequency());
    dto.setLeverageFactor(security.getLeverageFactor());
    dto.setProductLink(security.getProductLink());
    dto.setActiveFromDate(security.getActiveFromDate());
    dto.setActiveToDate(security.getActiveToDate());

    // Build connector hints
    dto.setConnectorHints(buildConnectorHints(security));

    return dto;
  }

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
        mergeOrAddHint(hints, hint, ConnectorCapability.INTRADAY);
      }
    }

    // Dividend connector
    if (security.getIdConnectorDividend() != null) {
      ConnectorHint hint = createConnectorHint(
          security.getIdConnectorDividend(),
          security.getUrlDividendExtend(),
          ConnectorCapability.DIVIDEND);
      if (hint != null) {
        mergeOrAddHint(hints, hint, ConnectorCapability.DIVIDEND);
      }
    }

    // Split connector
    if (security.getIdConnectorSplit() != null) {
      ConnectorHint hint = createConnectorHint(
          security.getIdConnectorSplit(),
          security.getUrlSplitExtend(),
          ConnectorCapability.SPLIT);
      if (hint != null) {
        mergeOrAddHint(hints, hint, ConnectorCapability.SPLIT);
      }
    }

    return hints;
  }

  private void mergeOrAddHint(List<ConnectorHint> hints, ConnectorHint newHint, ConnectorCapability capability) {
    hints.stream()
        .filter(h -> h.getConnectorFamily().equals(newHint.getConnectorFamily()))
        .findFirst()
        .ifPresentOrElse(
            existing -> existing.getCapabilities().add(capability),
            () -> hints.add(newHint));
  }

  private ConnectorHint createConnectorHint(String connectorId, String urlExtension, ConnectorCapability capability) {
    if (connectorId == null || connectorId.isBlank()) {
      return null;
    }

    String family = extractConnectorFamily(connectorId);
    if (family == null) {
      return null;
    }

    boolean requiresApiKey = checkRequiresApiKey(connectorId);
    Set<ConnectorCapability> capabilities = EnumSet.of(capability);

    return new ConnectorHint(family, capabilities, urlExtension, requiresApiKey);
  }

  private String extractConnectorFamily(String connectorId) {
    if (connectorId.startsWith(BaseFeedConnector.ID_PREFIX)) {
      return connectorId.substring(BaseFeedConnector.ID_PREFIX.length());
    }
    return connectorId;
  }

  private boolean checkRequiresApiKey(String connectorId) {
    IFeedConnector connector = feedConnectorMap.get(connectorId);
    if (connector != null) {
      return connector.getClass().getName().contains("ApiKey");
    }
    return false;
  }

  private HandlerResult<GTNetMessage, MessageEnvelope> createSuccessResponse(GTNetMessageContext context, GTNetMessage storedRequest,
      List<SecurityGtnetLookupDTO> results) {
    GTNetMessage responseMsg = storeResponseMessage(context, GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_RESPONSE_S,
        null, null, storedRequest);

    SecurityLookupResponseMsg responsePayload = new SecurityLookupResponseMsg(results);

    MessageEnvelope envelope = createResponseEnvelopeWithPayload(context, responseMsg, responsePayload);
    return new HandlerResult.ImmediateResponse<>(envelope);
  }

  private HandlerResult<GTNetMessage, MessageEnvelope> createNotFoundResponse(GTNetMessageContext context, GTNetMessage storedRequest) {
    GTNetMessage responseMsg = storeResponseMessage(context, GTNetMessageCodeType.GT_NET_SECURITY_LOOKUP_NOT_FOUND_S,
        null, null, storedRequest);

    MessageEnvelope envelope = createResponseEnvelope(context, responseMsg);
    return new HandlerResult.ImmediateResponse<>(envelope);
  }

  private HandlerResult<GTNetMessage, MessageEnvelope> createRejectedResponse(GTNetMessageContext context, String errorCode, String message) {
    return new HandlerResult.ProcessingError<>(errorCode, message);
  }
}
