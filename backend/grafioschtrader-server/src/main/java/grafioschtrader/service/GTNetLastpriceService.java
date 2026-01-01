package grafioschtrader.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetConfig;
import grafioschtrader.entities.GTNetEntity;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.AcceptRequestTypes;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.m2m.model.GTNetPublicDTO;
import grafioschtrader.gtnet.m2m.model.InstrumentPriceDTO;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.model.msg.LastpriceExchangeMsg;
import grafioschtrader.m2m.GTNetMessageHelper;
import grafioschtrader.m2m.client.BaseDataClient;
import grafioschtrader.m2m.client.BaseDataClient.SendResult;
import grafioschtrader.gtnet.handler.impl.lastprice.PushOpenLastpriceQueryStrategy;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.GTNetExchangeJpaRepository;
import grafioschtrader.repository.GTNetJpaRepository;
import grafioschtrader.repository.GTNetLastpriceCurrencypairJpaRepository;
import grafioschtrader.repository.GTNetLastpriceSecurityJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Service for orchestrating intraday price exchange with GTNet providers.
 *
 * This service implements the consumer-side flow for intraday price sharing:
 * <ol>
 *   <li>Filter instruments by GTNetExchange.lastpriceRecv configuration</li>
 *   <li>Query push-open servers first (by priority with random selection for same priority)</li>
 *   <li>Query open servers for remaining unfilled instruments</li>
 *   <li>Fall back to connectors (IFeedConnector) for any still-unfilled instruments</li>
 *   <li>If own mode is push-open: push connector-fetched prices back to remote servers</li>
 * </ol>
 *
 * @see InstrumentExchangeSet for tracking which instruments have been filled
 */
@Service
public class GTNetLastpriceService {

  private static final Logger log = LoggerFactory.getLogger(GTNetLastpriceService.class);

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GTNetExchangeJpaRepository gtNetExchangeJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private GTNetLastpriceSecurityJpaRepository gtNetLastpriceSecurityJpaRepository;

  @Autowired
  private GTNetLastpriceCurrencypairJpaRepository gtNetLastpriceCurrencypairJpaRepository;

  @Autowired
  private PushOpenLastpriceQueryStrategy pushOpenLastpriceQueryStrategy;

  @Autowired
  private BaseDataClient baseDataClient;

  @Autowired
  private GlobalparametersService globalparametersService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Main entry point for intraday price update with GTNet integration.
   * Called from WatchlistReport.executeLastPriceUpdate().
   *
   * @param securities list of securities from the watchlist
   * @param currencypairs list of currency pairs from the watchlist
   * @param currenciesNotInList additional currency pairs needed for calculations
   * @return updated securities and currency pairs
   */
  public SecurityCurrency updateLastpriceIncludeSupplier(List<Security> securities,
      List<Currencypair> currencypairs, List<Currencypair> currenciesNotInList) {

    // Check if GTNet is enabled
    if (!globalparametersService.isGTNetEnabled()) {
      // GTNet disabled - fall back to connectors only
      currencypairJpaRepository.updateLastPriceByList(currenciesNotInList);
      return new SecurityCurrency(
          securityJpaRepository.updateLastPriceByList(securities),
          currencypairJpaRepository.updateLastPriceByList(currencypairs));
    }

    try {
      return executeGTNetExchange(securities, currencypairs, currenciesNotInList);
    } catch (Exception e) {
      log.error("GTNet exchange failed, falling back to connectors only", e);
      currencypairJpaRepository.updateLastPriceByList(currenciesNotInList);
      return new SecurityCurrency(
          securityJpaRepository.updateLastPriceByList(securities),
          currencypairJpaRepository.updateLastPriceByList(currencypairs));
    }
  }

  private SecurityCurrency executeGTNetExchange(List<Security> securities,
      List<Currencypair> currencypairs, List<Currencypair> currenciesNotInList) {

    // 1. Get IDs of instruments configured for GTNet receive
    Set<Integer> gtNetEnabledIds = gtNetExchangeJpaRepository.findIdsWithLastpriceRecv();

    // 2. Build instrument sets - separate GTNet-enabled from connector-only
    InstrumentExchangeSet gtNetInstruments = new InstrumentExchangeSet();
    List<Security> connectorOnlySecurities = new ArrayList<>();
    List<Currencypair> connectorOnlyCurrencypairs = new ArrayList<>();

    for (Security security : securities) {
      if (gtNetEnabledIds.contains(security.getIdSecuritycurrency())) {
        gtNetInstruments.addSecurity(security);
      } else {
        connectorOnlySecurities.add(security);
      }
    }

    for (Currencypair currencypair : currencypairs) {
      if (gtNetEnabledIds.contains(currencypair.getIdSecuritycurrency())) {
        gtNetInstruments.addCurrencypair(currencypair);
      } else {
        connectorOnlyCurrencypairs.add(currencypair);
      }
    }

    log.info("GTNet exchange: {} instruments enabled, {} securities connector-only, {} pairs connector-only",
        gtNetInstruments.getTotalCount(), connectorOnlySecurities.size(), connectorOnlyCurrencypairs.size());

    // 3. If local server is AC_PUSH_OPEN, query own push pool first (before contacting remote servers)
    if (!gtNetInstruments.isEmpty() && !gtNetInstruments.allFilled()) {
      queryLocalPushPoolIfPushOpen(gtNetInstruments);
    }

    // 4. Query push-open servers by priority
    if (!gtNetInstruments.isEmpty() && !gtNetInstruments.allFilled()) {
      List<GTNet> pushOpenSuppliers = getSuppliersByPriorityWithRandomization(
          gtNetJpaRepository.findPushOpenSuppliers());
      queryRemoteServers(pushOpenSuppliers, gtNetInstruments);
    }

    // 5. Query open servers for remaining
    if (!gtNetInstruments.isEmpty() && !gtNetInstruments.allFilled()) {
      List<GTNet> openSuppliers = getSuppliersByPriorityWithRandomization(
          gtNetJpaRepository.findOpenSuppliers());
      queryRemoteServers(openSuppliers, gtNetInstruments);
    }

    // 6. Collect unfilled instruments for connector fallback
    List<Security> remainingSecurities = gtNetInstruments.getUnfilledSecurities();
    remainingSecurities.addAll(connectorOnlySecurities);

    List<Currencypair> remainingPairs = gtNetInstruments.getUnfilledCurrencypairs();
    remainingPairs.addAll(connectorOnlyCurrencypairs);

    log.info("GTNet exchange complete: {} securities remaining for connector, {} pairs remaining",
        remainingSecurities.size(), remainingPairs.size());

    // 7. Fall back to connectors for remaining instruments
    currencypairJpaRepository.updateLastPriceByList(currenciesNotInList);
    List<Security> updatedSecurities = securityJpaRepository.updateLastPriceByList(remainingSecurities);
    List<Currencypair> updatedPairs = currencypairJpaRepository.updateLastPriceByList(remainingPairs);

    // Combine with already-filled instruments
    List<Security> allSecurities = new ArrayList<>(gtNetInstruments.getAllSecurities());
    for (Security s : updatedSecurities) {
      if (!containsSecurity(allSecurities, s)) {
        allSecurities.add(s);
      }
    }

    List<Currencypair> allPairs = new ArrayList<>(gtNetInstruments.getAllCurrencypairs());
    for (Currencypair cp : updatedPairs) {
      if (!containsCurrencypair(allPairs, cp)) {
        allPairs.add(cp);
      }
    }

    return new SecurityCurrency(allSecurities, allPairs);
  }

  /**
   * Randomizes suppliers within the same priority level.
   * Suppliers are already ordered by priority ASC, so we need to shuffle within groups.
   */
  private List<GTNet> getSuppliersByPriorityWithRandomization(List<GTNet> suppliers) {
    if (suppliers == null || suppliers.size() <= 1) {
      return suppliers != null ? suppliers : new ArrayList<>();
    }

    // Group by priority (consumerUsage value from GTNetConfigEntity)
    Map<Byte, List<GTNet>> byPriority = suppliers.stream()
        .collect(Collectors.groupingBy(gtNet -> {
          // Get the LAST_PRICE entity's consumerUsage
          return gtNet.getGtNetEntities().stream()
              .filter(e -> e.getEntityKind() == GTNetExchangeKindType.LAST_PRICE)
              .findFirst()
              .map(e -> e.getGtNetConfigEntity().getConsumerUsage())
              .orElse((byte) 0);
        }));

    // Shuffle within each priority group and flatten
    List<GTNet> result = new ArrayList<>();
    byPriority.keySet().stream()
        .sorted()
        .forEach(priority -> {
          List<GTNet> group = byPriority.get(priority);
          Collections.shuffle(group);
          result.addAll(group);
        });

    return result;
  }

  /**
   * Queries remote servers for price data.
   */
  private void queryRemoteServers(List<GTNet> suppliers, InstrumentExchangeSet instruments) {
    for (GTNet supplier : suppliers) {
      if (instruments.allFilled()) {
        break;
      }

      try {
        queryRemoteServer(supplier, instruments);
      } catch (Exception e) {
        e.printStackTrace();
        log.warn("Failed to query GTNet server {}: {}", supplier.getDomainRemoteName(), e.getMessage());
      }
    }
  }

  private void queryRemoteServer(GTNet supplier, InstrumentExchangeSet instruments) {
    GTNetConfig config = supplier.getGtNetConfig();
    if (config == null || !config.isAuthorizedRemoteEntry()) {
      log.debug("Skipping unauthorized server: {}", supplier.getDomainRemoteName());
      return;
    }

    // Build request with unfilled instruments and their current timestamps
    List<InstrumentPriceDTO> securityDTOs = instruments.getUnfilledSecurityDTOs();
    List<InstrumentPriceDTO> currencypairDTOs = instruments.getUnfilledCurrencypairDTOs();

    if (securityDTOs.isEmpty() && currencypairDTOs.isEmpty()) {
      return;
    }

    LastpriceExchangeMsg requestPayload = LastpriceExchangeMsg.forRequest(securityDTOs, currencypairDTOs);

    // Get local GTNet entry for source identification (use findById to eagerly load - async context has no session)
    Integer myGTNetId = GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersService);
    GTNet myGTNet = gtNetJpaRepository.findById(myGTNetId)
        .orElseThrow(() -> new IllegalStateException("Local GTNet entry not found: " + myGTNetId));

    // Build MessageEnvelope with source identification
    MessageEnvelope requestEnvelope = new MessageEnvelope();
    requestEnvelope.sourceDomain = myGTNet.getDomainRemoteName();
    requestEnvelope.sourceGtNet = new GTNetPublicDTO(myGTNet);
    requestEnvelope.serverBusy = myGTNet.isServerBusy();
    requestEnvelope.messageCode = GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_SEL_C.getValue();
    requestEnvelope.timestamp = new Date();
    requestEnvelope.payload = objectMapper.valueToTree(requestPayload);

    log.debug("Sending lastprice request to {} with {} securities, {} pairs",
        supplier.getDomainRemoteName(), securityDTOs.size(), currencypairDTOs.size());

    // Send request
    SendResult result = baseDataClient.sendToMsgWithStatus(
        config.getTokenRemote(),
        supplier.getDomainRemoteName(),
        requestEnvelope);

    if (!result.serverReachable()) {
      log.warn("GTNet server {} is unreachable", supplier.getDomainRemoteName());
      return;
    }

    MessageEnvelope response = result.response();
    if (response == null || response.payload == null) {
      log.debug("No price data received from {}", supplier.getDomainRemoteName());
      return;
    }

    // Parse response
    try {
      LastpriceExchangeMsg responsePayload = objectMapper.treeToValue(response.payload, LastpriceExchangeMsg.class);

      int updatedCount = 0;
      if (responsePayload.securities != null) {
        updatedCount += responsePayload.securities.size();
      }
      if (responsePayload.currencypairs != null) {
        updatedCount += responsePayload.currencypairs.size();
      }

      log.info("Received {} price updates from {}", updatedCount, supplier.getDomainRemoteName());

      // Process response - update instruments and mark as filled
      instruments.processResponse(responsePayload.securities, responsePayload.currencypairs);

    } catch (JsonProcessingException e) {
      log.error("Failed to parse lastprice response from {}", supplier.getDomainRemoteName(), e);
    }
  }

  /**
   * Queries the local push pool if this server is configured as AC_PUSH_OPEN.
   * This allows the server to use its own cached prices before contacting remote servers.
   *
   * @param instruments the instrument set to query and update
   */
  private void queryLocalPushPoolIfPushOpen(InstrumentExchangeSet instruments) {
    // Get local GTNet entry
    Integer myGTNetId = globalparametersService.getGTNetMyEntryID();
    if (myGTNetId == null) {
      return;
    }

    Optional<GTNet> myGTNetOpt = gtNetJpaRepository.findById(myGTNetId);
    if (myGTNetOpt.isEmpty()) {
      return;
    }

    GTNet myGTNet = myGTNetOpt.get();

    // Check if we have an AC_PUSH_OPEN LAST_PRICE entity
    Optional<GTNetEntity> lastpriceEntity = myGTNet.getEntity(GTNetExchangeKindType.LAST_PRICE);
    if (lastpriceEntity.isEmpty()) {
      return;
    }

    AcceptRequestTypes acceptMode = lastpriceEntity.get().getAcceptRequest();
    if (acceptMode != AcceptRequestTypes.AC_PUSH_OPEN) {
      return;
    }

    log.debug("Querying local push pool (AC_PUSH_OPEN mode)");

    // Query local push pool for securities
    List<InstrumentPriceDTO> securityDTOs = instruments.getUnfilledSecurityDTOs();
    if (!securityDTOs.isEmpty()) {
      List<InstrumentPriceDTO> securityPrices = pushOpenLastpriceQueryStrategy.querySecurities(
          securityDTOs, Collections.emptySet());
      if (!securityPrices.isEmpty()) {
        instruments.processResponse(securityPrices, null);
        log.debug("Local push pool: found {} security prices", securityPrices.size());
      }
    }

    // Query local push pool for currency pairs
    List<InstrumentPriceDTO> currencypairDTOs = instruments.getUnfilledCurrencypairDTOs();
    if (!currencypairDTOs.isEmpty()) {
      List<InstrumentPriceDTO> currencypairPrices = pushOpenLastpriceQueryStrategy.queryCurrencypairs(
          currencypairDTOs, Collections.emptySet());
      if (!currencypairPrices.isEmpty()) {
        instruments.processResponse(null, currencypairPrices);
        log.debug("Local push pool: found {} currencypair prices", currencypairPrices.size());
      }
    }
  }

  private boolean containsSecurity(List<Security> list, Security security) {
    return list.stream().anyMatch(s -> s.getIdSecuritycurrency().equals(security.getIdSecuritycurrency()));
  }

  private boolean containsCurrencypair(List<Currencypair> list, Currencypair currencypair) {
    return list.stream().anyMatch(cp -> cp.getIdSecuritycurrency().equals(currencypair.getIdSecuritycurrency()));
  }

  /**
   * Result container for security and currency pair lists.
   */
  public static class SecurityCurrency {
    public List<Security> securities;
    public List<Currencypair> currencypairs;

    public SecurityCurrency(final List<Security> securities, final List<Currencypair> currencypairs) {
      this.securities = securities;
      this.currencypairs = currencypairs;
    }
  }
}
