package grafioschtrader.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
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
import grafioschtrader.entities.GTNetHistoryquote;
import grafioschtrader.entities.GTNetInstrument;
import grafioschtrader.entities.GTNetInstrumentCurrencypair;
import grafioschtrader.entities.GTNetInstrumentSecurity;
import grafioschtrader.entities.GTNetSupplierDetail;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.priceupdate.historyquote.SecurityCurrencyMaxHistoryquoteData;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.m2m.model.GTNetPublicDTO;
import grafioschtrader.gtnet.m2m.model.HistoryquoteRecordDTO;
import grafioschtrader.gtnet.m2m.model.InstrumentHistoryquoteDTO;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.model.msg.HistoryquoteExchangeMsg;
import grafioschtrader.m2m.GTNetMessageHelper;
import grafioschtrader.m2m.client.BaseDataClient;
import grafioschtrader.m2m.client.BaseDataClient.SendResult;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.GTNetExchangeLogJpaRepository;
import grafioschtrader.repository.GTNetHistoryquoteJpaRepository;
import grafioschtrader.repository.GTNetInstrumentCurrencypairJpaRepository;
import grafioschtrader.repository.GTNetInstrumentSecurityJpaRepository;
import grafioschtrader.repository.GTNetJpaRepository;
import grafioschtrader.repository.GTNetSupplierDetailJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Service for orchestrating historical price exchange with GTNet providers.
 *
 * This service implements bidirectional consumer and supplier flows for historical price sharing:
 *
 * <h3>Consumer Side Flow (requesting data from remote servers)</h3>
 * <ol>
 *   <li>Build request with instruments and date ranges (fromDate = most recent local data + 1 day)</li>
 *   <li>Query push-open servers first (prioritized by consumerUsage)</li>
 *   <li>Query open servers for remaining unfilled instruments</li>
 *   <li>Store received data: local historyquote for local instruments, gt_net_historyquote for foreign</li>
 *   <li>Track "want to receive" responses from suppliers</li>
 *   <li>Push local historical data back to interested suppliers</li>
 * </ol>
 *
 * <h3>"Want to Receive" Mechanism</h3>
 * When AC_OPEN suppliers cannot provide data but are configured to receive it, they return
 * "want to receive" markers with the date from which they need data. This consumer then
 * pushes local historical data back to those suppliers.
 *
 * <h3>Storage Decision</h3>
 * When storing received historical quotes:
 * <ul>
 *   <li>If instrument exists locally (GTNetInstrument.idSecuritycurrency != null): store in historyquote table</li>
 *   <li>If instrument is foreign (GTNetInstrument.idSecuritycurrency == null): store in gt_net_historyquote table</li>
 * </ul>
 *
 * @see GTNetLastpriceService for intraday price exchange
 * @see BaseGTNetExchangeService for shared supplier prioritization logic
 */
@Service
public class GTNetHistoryquoteService extends BaseGTNetExchangeService {

  private static final Logger log = LoggerFactory.getLogger(GTNetHistoryquoteService.class);

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GTNetSupplierDetailJpaRepository gtNetSupplierDetailJpaRepository;

  @Autowired
  private GTNetInstrumentSecurityJpaRepository gtNetInstrumentSecurityJpaRepository;

  @Autowired
  private GTNetInstrumentCurrencypairJpaRepository gtNetInstrumentCurrencypairJpaRepository;

  @Autowired
  private GTNetHistoryquoteJpaRepository gtNetHistoryquoteJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private BaseDataClient baseDataClient;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private GTNetExchangeLogService gtNetExchangeLogService;

  @Autowired
  private GTNetExchangeLogJpaRepository gtNetExchangeLogJpaRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Requests historical quotes for a security from GTNet suppliers.
   *
   * @param isin the ISIN of the security
   * @param currency the currency of the security
   * @param fromDate the start date (inclusive)
   * @param toDate the end date (inclusive)
   * @return the number of records received and stored
   */
  public int requestHistoryquotesForSecurity(String isin, String currency, Date fromDate, Date toDate) {
    if (!globalparametersService.isGTNetEnabled()) {
      return 0;
    }

    List<InstrumentHistoryquoteDTO> securities = new ArrayList<>();
    securities.add(InstrumentHistoryquoteDTO.forSecurityRequest(isin, currency, fromDate, toDate));

    HistoryquoteExchangeMsg request = HistoryquoteExchangeMsg.forRequest(securities, null);
    return executeHistoryquoteExchange(request);
  }

  /**
   * Requests historical quotes for a currency pair from GTNet suppliers.
   *
   * @param fromCurrency the source currency
   * @param toCurrency the target currency
   * @param fromDate the start date (inclusive)
   * @param toDate the end date (inclusive)
   * @return the number of records received and stored
   */
  public int requestHistoryquotesForCurrencypair(String fromCurrency, String toCurrency, Date fromDate, Date toDate) {
    if (!globalparametersService.isGTNetEnabled()) {
      return 0;
    }

    List<InstrumentHistoryquoteDTO> currencypairs = new ArrayList<>();
    currencypairs.add(InstrumentHistoryquoteDTO.forCurrencypairRequest(fromCurrency, toCurrency, fromDate, toDate));

    HistoryquoteExchangeMsg request = HistoryquoteExchangeMsg.forRequest(null, currencypairs);
    return executeHistoryquoteExchange(request);
  }

  /**
   * Requests historical quotes for multiple instruments from GTNet suppliers.
   *
   * @param request the request containing securities and/or currency pairs with date ranges
   * @return the number of records received and stored
   */
  public int requestHistoryquotes(HistoryquoteExchangeMsg request) {
    if (!globalparametersService.isGTNetEnabled()) {
      return 0;
    }
    return executeHistoryquoteExchange(request);
  }

  /**
   * Main integration point for BaseHistoryquoteThru.
   *
   * This method filters the input list by gtNetHistoricalRecv flag, queries GTNet servers in priority order,
   * and returns a result indicating which instruments were filled and which need connector fallback.
   * Also tracks "want to receive" markers for later push-back of connector-fetched data.
   *
   * @param historySecurityCurrencyList the list of instruments from BaseHistoryquoteThru requiring updates
   * @param untilCalendar the target end date for historyquote loading
   * @param <S> Security or Currencypair
   * @return result containing unfilled instruments, filled instruments, and want-to-receive map
   */
  public <S extends Securitycurrency<S>> HistoryquoteExchangeResult<S> requestHistoryquotesFromBaseThru(
      List<SecurityCurrencyMaxHistoryquoteData<S>> historySecurityCurrencyList, Calendar untilCalendar) {

    // Check GTNet enabled
    if (!globalparametersService.isGTNetEnabled() || historySecurityCurrencyList.isEmpty()) {
      return HistoryquoteExchangeResult.passthrough(historySecurityCurrencyList);
    }

    // Get IDs with gtNetHistoricalRecv enabled
    Set<Integer> gtNetEnabledIds = getGtNetEnabledIds(historySecurityCurrencyList);
    if (gtNetEnabledIds.isEmpty()) {
      return HistoryquoteExchangeResult.passthrough(historySecurityCurrencyList);
    }

    // Build the exchange set with GTNet-enabled instruments
    HistoryquoteExchangeSet<S> exchangeSet = new HistoryquoteExchangeSet<>();
    Date toDate = untilCalendar.getTime();

    for (SecurityCurrencyMaxHistoryquoteData<S> data : historySecurityCurrencyList) {
      S instrument = data.getSecurityCurrency();
      if (gtNetEnabledIds.contains(instrument.getIdSecuritycurrency())) {
        // Calculate fromDate: day after most recent historyquote
        Date fromDate = calculateFromDate(data.getDate());
        exchangeSet.addInstrument(data, fromDate, toDate);
      }
    }

    if (exchangeSet.isEmpty()) {
      return HistoryquoteExchangeResult.passthrough(historySecurityCurrencyList);
    }

    log.info("Starting GTNet historyquote exchange for {} instruments", exchangeSet.getTotalCount());

    // Load supplier details for filtering AC_OPEN requests
    SupplierInstrumentFilter filter = null;
    List<Integer> allInstrumentIds = exchangeSet.getAllInstrumentIds();
    if (!allInstrumentIds.isEmpty()) {
      List<GTNetSupplierDetail> supplierDetails = gtNetSupplierDetailJpaRepository
          .findByEntityKindAndInstrumentIds(GTNetExchangeKindType.HISTORICAL_PRICES.getValue(), allInstrumentIds);
      filter = new SupplierInstrumentFilter(supplierDetails);
    }

    // Load success rates for AC_OPEN supplier scoring (past 30 days)
    LocalDate fromDate = LocalDate.now().minusDays(30);
    List<Object[]> successRateData = gtNetExchangeLogJpaRepository
        .getSupplierSuccessRates(GTNetExchangeKindType.HISTORICAL_PRICES.getValue(), fromDate);
    SupplierScoreCalculator scoreCalculator = new SupplierScoreCalculator(successRateData);
    Set<Integer> requestedInstrumentIds = new HashSet<>(allInstrumentIds);

    // Query PUSH_OPEN servers first (excluding own entry to prevent self-communication)
    // PUSH_OPEN uses priority+random algorithm (unchanged)
    List<GTNet> pushOpenSuppliers = getSuppliersByPriorityWithRandomization(
        excludeOwnEntry(gtNetJpaRepository.findHistoryquotePushOpenSuppliers()), GTNetExchangeKindType.HISTORICAL_PRICES);
    queryRemoteServersForExchangeSet(pushOpenSuppliers, exchangeSet, null);

    // Query OPEN servers for remaining unfilled (excluding own entry to prevent self-communication)
    // AC_OPEN uses score-based selection: coverage x success_rate, then priority, then random
    if (!exchangeSet.allFilled()) {
      List<GTNet> openSuppliers = getSuppliersByScoreWithRandomization(
          excludeOwnEntry(gtNetJpaRepository.findHistoryquoteOpenSuppliers()),
          GTNetExchangeKindType.HISTORICAL_PRICES,
          scoreCalculator,
          filter,
          requestedInstrumentIds);
      queryRemoteServersForExchangeSet(openSuppliers, exchangeSet, filter);
    }

    log.info("GTNet historyquote exchange complete: {}/{} instruments filled",
        exchangeSet.getTotalCount() - exchangeSet.getUnfilledCount(), exchangeSet.getTotalCount());

    // Build the remaining list for connector fallback (non-GTNet + unfilled)
    List<SecurityCurrencyMaxHistoryquoteData<S>> remainingForConnector = new ArrayList<>();
    for (SecurityCurrencyMaxHistoryquoteData<S> data : historySecurityCurrencyList) {
      if (!gtNetEnabledIds.contains(data.getSecurityCurrency().getIdSecuritycurrency())) {
        // Not GTNet enabled - pass through to connector
        remainingForConnector.add(data);
      }
    }
    remainingForConnector.addAll(exchangeSet.getUnfilledInstruments());

    return new HistoryquoteExchangeResult<>(
        remainingForConnector,
        exchangeSet.getFilledInstruments(),
        exchangeSet.getWantToReceiveMap(),
        exchangeSet.getAllReceivedData());
  }

  /**
   * Push connector-fetched historical data back to GTNet suppliers that expressed "want to receive".
   *
   * @param filledByConnector instruments that were filled by connectors
   * @param wantToReceiveMap map of suppliers and their requested instruments
   * @param <S> Security or Currencypair
   */
  public <S extends Securitycurrency<S>> void pushConnectorDataToGTNet(
      List<S> filledByConnector,
      Map<GTNet, List<InstrumentHistoryquoteDTO>> wantToReceiveMap) {

    if (wantToReceiveMap == null || wantToReceiveMap.isEmpty() || filledByConnector == null || filledByConnector.isEmpty()) {
      return;
    }

    log.info("Pushing connector-fetched data to {} interested suppliers for {} instruments",
        wantToReceiveMap.size(), filledByConnector.size());

    for (Map.Entry<GTNet, List<InstrumentHistoryquoteDTO>> entry : wantToReceiveMap.entrySet()) {
      GTNet supplier = entry.getKey();
      List<InstrumentHistoryquoteDTO> wantedInstruments = entry.getValue();

      try {
        HistoryquoteExchangeMsg pushPayload = buildPushPayloadFromConnectorData(filledByConnector, wantedInstruments);
        if (!pushPayload.isEmpty()) {
          sendPushToSupplier(supplier, pushPayload);
        }
      } catch (Exception e) {
        log.warn("Failed to push connector data to {}: {}", supplier.getDomainRemoteName(), e.getMessage());
      }
    }
  }

  /**
   * Queries remote servers and populates the exchange set with results.
   *
   * @param suppliers list of suppliers to query
   * @param exchangeSet the exchange set tracking instruments
   * @param filter optional filter for AC_OPEN suppliers (null for AC_PUSH_OPEN)
   */
  private <S extends Securitycurrency<S>> void queryRemoteServersForExchangeSet(
      List<GTNet> suppliers, HistoryquoteExchangeSet<S> exchangeSet, SupplierInstrumentFilter filter) {

    for (GTNet supplier : suppliers) {
      if (exchangeSet.allFilled()) {
        break;
      }

      try {
        // Build request with unfilled instruments
        List<InstrumentHistoryquoteDTO> securityDTOs;
        List<InstrumentHistoryquoteDTO> currencypairDTOs;

        if (filter != null) {
          // AC_OPEN supplier: filter instruments to only those this supplier is known to support
          Set<Integer> allUnfilledIds = new HashSet<>(exchangeSet.getAllInstrumentIds());
          Set<Integer> allowedIds = filter.getInstrumentsForSupplier(supplier.getIdGtNet(), allUnfilledIds, false);

          if (allowedIds.isEmpty()) {
            log.debug("No supported instruments for AC_OPEN supplier {}, skipping", supplier.getDomainRemoteName());
            continue;
          }

          securityDTOs = exchangeSet.getUnfilledSecurityDTOsFiltered(allowedIds);
          currencypairDTOs = exchangeSet.getUnfilledCurrencypairDTOsFiltered(allowedIds);
        } else {
          // AC_PUSH_OPEN supplier: send all unfilled instruments (no filtering)
          securityDTOs = exchangeSet.getUnfilledSecurityDTOs();
          currencypairDTOs = exchangeSet.getUnfilledCurrencypairDTOs();
        }

        HistoryquoteExchangeMsg request = HistoryquoteExchangeMsg.forRequest(securityDTOs, currencypairDTOs);

        if (request.isEmpty()) {
          continue;
        }

        QueryResult result = queryRemoteServerWithWantTracking(supplier, request);

        // Process the response to update exchange set and track want-to-receive
        if (result.responsePayload != null) {
          exchangeSet.processResponse(result.responsePayload, supplier);
        }
      } catch (Exception e) {
        log.warn("Failed to query GTNet server {} for historyquotes: {}",
            supplier.getDomainRemoteName(), e.getMessage());
      }
    }
  }

  /**
   * Gets the set of securitycurrency IDs that have gtNetHistoricalRecv enabled.
   */
  private <S extends Securitycurrency<S>> Set<Integer> getGtNetEnabledIds(
      List<SecurityCurrencyMaxHistoryquoteData<S>> list) {

    if (list.isEmpty()) {
      return Set.of();
    }

    // Determine type from first element
    S first = list.get(0).getSecurityCurrency();
    if (first instanceof Security) {
      return securityJpaRepository.findIdsWithGtNetHistoricalRecv();
    } else if (first instanceof Currencypair) {
      return currencypairJpaRepository.findIdsWithGtNetHistoricalRecv();
    }

    return Set.of();
  }

  /**
   * Calculates the fromDate for historyquote request.
   * Returns the day after the most recent historyquote date, or oldest allowed date if null.
   */
  private Date calculateFromDate(Date mostRecentDate) {
    if (mostRecentDate == null) {
      // Use globalparametersService for oldest allowed date
      try {
        return globalparametersService.getStartFeedDate();
      } catch (Exception e) {
        // Fallback to 10 years ago
        Calendar oldest = Calendar.getInstance();
        oldest.add(Calendar.YEAR, -10);
        return oldest.getTime();
      }
    }

    Calendar cal = Calendar.getInstance();
    cal.setTime(mostRecentDate);
    cal.add(Calendar.DAY_OF_MONTH, 1);
    return cal.getTime();
  }

  /**
   * Builds push payload from connector-fetched data matching wanted instruments.
   */
  private <S extends Securitycurrency<S>> HistoryquoteExchangeMsg buildPushPayloadFromConnectorData(
      List<S> filledByConnector, List<InstrumentHistoryquoteDTO> wantedInstruments) {

    HistoryquoteExchangeMsg pushPayload = new HistoryquoteExchangeMsg();

    for (InstrumentHistoryquoteDTO wanted : wantedInstruments) {
      Date fromDate = wanted.getWantsDataFromDate();
      if (fromDate == null) {
        continue;
      }

      for (S instrument : filledByConnector) {
        if (instrument instanceof Security security) {
          if (matchesSecurity(wanted, security)) {
            InstrumentHistoryquoteDTO localData = queryLocalSecurityHistoryquote(
                security.getIsin(), security.getCurrency(), fromDate, getYesterday());
            if (localData != null && localData.getRecordCount() > 0) {
              pushPayload.securities.add(localData);
            }
          }
        } else if (instrument instanceof Currencypair pair) {
          if (matchesCurrencypair(wanted, pair)) {
            InstrumentHistoryquoteDTO localData = queryLocalCurrencypairHistoryquote(
                pair.getFromCurrency(), pair.getToCurrency(), fromDate, getYesterday());
            if (localData != null && localData.getRecordCount() > 0) {
              pushPayload.currencypairs.add(localData);
            }
          }
        }
      }
    }

    return pushPayload;
  }

  private boolean matchesSecurity(InstrumentHistoryquoteDTO dto, Security security) {
    return security.getIsin() != null
        && security.getIsin().equals(dto.getIsin())
        && security.getCurrency().equals(dto.getCurrency());
  }

  private boolean matchesCurrencypair(InstrumentHistoryquoteDTO dto, Currencypair pair) {
    return pair.getFromCurrency().equals(dto.getCurrency())
        && pair.getToCurrency().equals(dto.getToCurrency());
  }

  /**
   * Sends push payload to a supplier.
   */
  private void sendPushToSupplier(GTNet supplier, HistoryquoteExchangeMsg pushPayload) {
    GTNetConfig config = supplier.getGtNetConfig();
    if (config == null || !config.isAuthorizedRemoteEntry()) {
      log.debug("Skipping push to unauthorized server: {}", supplier.getDomainRemoteName());
      return;
    }

    Integer myGTNetId = GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersService);
    GTNet myGTNet = gtNetJpaRepository.findById(myGTNetId)
        .orElseThrow(() -> new IllegalStateException("Local GTNet entry not found: " + myGTNetId));

    MessageEnvelope pushEnvelope = new MessageEnvelope();
    pushEnvelope.sourceDomain = myGTNet.getDomainRemoteName();
    pushEnvelope.sourceGtNet = new GTNetPublicDTO(myGTNet);
    pushEnvelope.serverBusy = myGTNet.isServerBusy();
    pushEnvelope.messageCode = GTNetMessageCodeType.GT_NET_HISTORYQUOTE_PUSH_SEL_C.getValue();
    pushEnvelope.timestamp = new Date();
    pushEnvelope.payload = objectMapper.valueToTree(pushPayload);

    log.info("Pushing {} securities, {} pairs to {}",
        pushPayload.securities.size(), pushPayload.currencypairs.size(),
        supplier.getDomainRemoteName());

    SendResult result = baseDataClient.sendToMsgWithStatus(
        config.getTokenRemote(),
        supplier.getDomainRemoteName(),
        pushEnvelope);

    if (result.isFailed()) {
      if (result.httpError()) {
        log.warn("GTNet server {} returned HTTP error {} for push", supplier.getDomainRemoteName(), result.httpStatusCode());
      } else {
        log.warn("GTNet server {} is unreachable for push", supplier.getDomainRemoteName());
      }
    }
  }

  /**
   * Executes the historyquote exchange with remote GTNet suppliers.
   * Tracks "want to receive" responses and pushes data back to interested suppliers.
   */
  private int executeHistoryquoteExchange(HistoryquoteExchangeMsg request) {
    int totalReceived = 0;
    Map<GTNet, HistoryquoteExchangeMsg> wantToReceiveMap = new HashMap<>();

    // 1. Query push-open servers by priority (excluding own entry to prevent self-communication)
    List<GTNet> pushOpenSuppliers = getSuppliersByPriorityWithRandomization(
        excludeOwnEntry(gtNetJpaRepository.findHistoryquotePushOpenSuppliers()), GTNetExchangeKindType.HISTORICAL_PRICES);
    totalReceived += queryRemoteServersWithWantTracking(pushOpenSuppliers, request, wantToReceiveMap);

    // 2. Query open servers for remaining (excluding own entry to prevent self-communication)
    List<GTNet> openSuppliers = getSuppliersByPriorityWithRandomization(
        excludeOwnEntry(gtNetJpaRepository.findHistoryquoteOpenSuppliers()), GTNetExchangeKindType.HISTORICAL_PRICES);
    totalReceived += queryRemoteServersWithWantTracking(openSuppliers, request, wantToReceiveMap);

    // 3. Push historical data back to servers that expressed "want to receive"
    if (!wantToReceiveMap.isEmpty()) {
      pushHistoricalDataToInterestedSuppliers(wantToReceiveMap);
    }

    log.info("Historyquote exchange complete: {} total records received", totalReceived);
    return totalReceived;
  }

  /**
   * Queries remote servers for historical price data and tracks "want to receive" responses.
   * This method also stores received data directly (used by direct API calls, not by HistoryquoteThruGTNet).
   */
  private int queryRemoteServersWithWantTracking(List<GTNet> suppliers, HistoryquoteExchangeMsg request,
      Map<GTNet, HistoryquoteExchangeMsg> wantToReceiveMap) {
    int totalStored = 0;
    Integer myGTNetId = GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersService);

    for (GTNet supplier : suppliers) {
      try {
        QueryResult result = queryRemoteServerWithWantTracking(supplier, request);

        // Store received data for direct API calls
        if (result.responsePayload != null) {
          totalStored += storeReceivedHistoryquotes(result.responsePayload, myGTNetId);
        }

        if (result.wantToReceive != null && result.wantToReceive.hasWantToReceiveMarkers()) {
          wantToReceiveMap.put(supplier, result.wantToReceive);
        }
      } catch (Exception e) {
        log.warn("Failed to query GTNet server {} for historyquotes: {}",
            supplier.getDomainRemoteName(), e.getMessage());
      }
    }

    return totalStored;
  }

  /**
   * Queries a remote server and returns stored data count, response payload, and "want to receive" markers.
   */
  private QueryResult queryRemoteServerWithWantTracking(GTNet supplier, HistoryquoteExchangeMsg request) {
    GTNetConfig config = supplier.getGtNetConfig();
    if (config == null || !config.isAuthorizedRemoteEntry()) {
      log.debug("Skipping unauthorized server: {}", supplier.getDomainRemoteName());
      return new QueryResult(0, null, null);
    }

    // Get local GTNet entry for source identification
    Integer myGTNetId = GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersService);
    GTNet myGTNet = gtNetJpaRepository.findById(myGTNetId)
        .orElseThrow(() -> new IllegalStateException("Local GTNet entry not found: " + myGTNetId));

    // Build MessageEnvelope with source identification
    MessageEnvelope requestEnvelope = new MessageEnvelope();
    requestEnvelope.sourceDomain = myGTNet.getDomainRemoteName();
    requestEnvelope.sourceGtNet = new GTNetPublicDTO(myGTNet);
    requestEnvelope.serverBusy = myGTNet.isServerBusy();
    requestEnvelope.messageCode = GTNetMessageCodeType.GT_NET_HISTORYQUOTE_EXCHANGE_SEL_C.getValue();
    requestEnvelope.timestamp = new Date();
    requestEnvelope.payload = objectMapper.valueToTree(request);

    log.debug("Sending historyquote request to {} with {} securities, {} pairs",
        supplier.getDomainRemoteName(),
        request.securities != null ? request.securities.size() : 0,
        request.currencypairs != null ? request.currencypairs.size() : 0);

    // Send request
    SendResult result = baseDataClient.sendToMsgWithStatus(
        config.getTokenRemote(),
        supplier.getDomainRemoteName(),
        requestEnvelope);

    if (result.isFailed()) {
      if (result.httpError()) {
        log.warn("GTNet server {} returned HTTP error {}", supplier.getDomainRemoteName(), result.httpStatusCode());
      } else {
        log.warn("GTNet server {} is unreachable", supplier.getDomainRemoteName());
      }
      return new QueryResult(0, null, null);
    }

    MessageEnvelope response = result.response();
    if (response == null || response.payload == null) {
      log.debug("No historyquote data received from {}", supplier.getDomainRemoteName());
      return new QueryResult(0, null, null);
    }

    // Parse response
    try {
      HistoryquoteExchangeMsg responsePayload = objectMapper.treeToValue(response.payload, HistoryquoteExchangeMsg.class);

      if (responsePayload == null) {
        log.debug("Empty response payload from {}", supplier.getDomainRemoteName());
        return new QueryResult(0, null, null);
      }

      int recordCount = responsePayload.getTotalRecordCount();
      int wantToReceiveCount = responsePayload.getSecuritiesWantingData().size()
          + responsePayload.getCurrencypairsWantingData().size();
      log.info("Received {} historyquote records, {} want-to-receive markers from {}",
          recordCount, wantToReceiveCount, supplier.getDomainRemoteName());

      // Log exchange statistics as consumer (storage happens later in HistoryquoteThruGTNet)
      int instrumentsSent = request.getTotalInstrumentCount();
      gtNetExchangeLogService.logAsConsumer(supplier, GTNetExchangeKindType.HISTORICAL_PRICES,
          instrumentsSent, recordCount, recordCount);

      // Build want-to-receive response for push back
      HistoryquoteExchangeMsg wantToReceive = null;
      if (wantToReceiveCount > 0) {
        wantToReceive = HistoryquoteExchangeMsg.forRequest(
            responsePayload.getSecuritiesWantingData(),
            responsePayload.getCurrencypairsWantingData());
      }

      return new QueryResult(recordCount, responsePayload, wantToReceive);

    } catch (JsonProcessingException e) {
      log.error("Failed to parse historyquote response from {}", supplier.getDomainRemoteName(), e);
      return new QueryResult(0, null, null);
    }
  }

  /**
   * Pushes historical data back to suppliers that expressed interest via "want to receive" markers.
   */
  private void pushHistoricalDataToInterestedSuppliers(Map<GTNet, HistoryquoteExchangeMsg> wantToReceiveMap) {
    log.info("Pushing historical data to {} interested suppliers", wantToReceiveMap.size());

    for (Map.Entry<GTNet, HistoryquoteExchangeMsg> entry : wantToReceiveMap.entrySet()) {
      GTNet supplier = entry.getKey();
      HistoryquoteExchangeMsg wantedInstruments = entry.getValue();

      try {
        pushHistoricalDataToSupplier(supplier, wantedInstruments);
      } catch (Exception e) {
        log.warn("Failed to push historical data to {}: {}",
            supplier.getDomainRemoteName(), e.getMessage());
      }
    }
  }

  /**
   * Pushes historical data to a specific supplier for instruments they requested.
   */
  private void pushHistoricalDataToSupplier(GTNet supplier, HistoryquoteExchangeMsg wantedInstruments) {
    GTNetConfig config = supplier.getGtNetConfig();
    if (config == null || !config.isAuthorizedRemoteEntry()) {
      log.debug("Skipping push to unauthorized server: {}", supplier.getDomainRemoteName());
      return;
    }

    // Build push payload with our local historical data
    HistoryquoteExchangeMsg pushPayload = new HistoryquoteExchangeMsg();

    // Query local data for requested securities
    for (InstrumentHistoryquoteDTO wanted : wantedInstruments.getSecuritiesWantingData()) {
      if (wanted.getWantsDataFromDate() != null) {
        InstrumentHistoryquoteDTO localData = queryLocalSecurityHistoryquote(
            wanted.getIsin(), wanted.getCurrency(), wanted.getWantsDataFromDate(), getYesterday());
        if (localData != null && localData.getRecordCount() > 0) {
          pushPayload.securities.add(localData);
        }
      }
    }

    // Query local data for requested currency pairs
    for (InstrumentHistoryquoteDTO wanted : wantedInstruments.getCurrencypairsWantingData()) {
      if (wanted.getWantsDataFromDate() != null) {
        InstrumentHistoryquoteDTO localData = queryLocalCurrencypairHistoryquote(
            wanted.getCurrency(), wanted.getToCurrency(), wanted.getWantsDataFromDate(), getYesterday());
        if (localData != null && localData.getRecordCount() > 0) {
          pushPayload.currencypairs.add(localData);
        }
      }
    }

    if (pushPayload.isEmpty()) {
      log.debug("No historical data available to push to {}", supplier.getDomainRemoteName());
      return;
    }

    // Get local GTNet entry for source identification
    Integer myGTNetId = GTNetMessageHelper.getGTNetMyEntryIDOrThrow(globalparametersService);
    GTNet myGTNet = gtNetJpaRepository.findById(myGTNetId)
        .orElseThrow(() -> new IllegalStateException("Local GTNet entry not found: " + myGTNetId));

    // Build and send push message
    MessageEnvelope pushEnvelope = new MessageEnvelope();
    pushEnvelope.sourceDomain = myGTNet.getDomainRemoteName();
    pushEnvelope.sourceGtNet = new GTNetPublicDTO(myGTNet);
    pushEnvelope.serverBusy = myGTNet.isServerBusy();
    pushEnvelope.messageCode = GTNetMessageCodeType.GT_NET_HISTORYQUOTE_PUSH_SEL_C.getValue();
    pushEnvelope.timestamp = new Date();
    pushEnvelope.payload = objectMapper.valueToTree(pushPayload);

    log.info("Pushing {} securities, {} pairs historical data to {}",
        pushPayload.securities.size(), pushPayload.currencypairs.size(),
        supplier.getDomainRemoteName());

    SendResult result = baseDataClient.sendToMsgWithStatus(
        config.getTokenRemote(),
        supplier.getDomainRemoteName(),
        pushEnvelope);

    if (result.isFailed()) {
      if (result.httpError()) {
        log.warn("GTNet server {} returned HTTP error {} for historical data push",
            supplier.getDomainRemoteName(), result.httpStatusCode());
      } else {
        log.warn("GTNet server {} is unreachable for historical data push", supplier.getDomainRemoteName());
      }
    }
  }

  /**
   * Queries local historyquote for a security by ISIN+currency and date range.
   */
  private InstrumentHistoryquoteDTO queryLocalSecurityHistoryquote(String isin, String currency,
      Date fromDate, Date toDate) {
    // Find the local security by ISIN and currency
    Security security = securityJpaRepository.findByIsinAndCurrency(isin, currency);
    if (security == null) {
      return null;
    }

    List<Historyquote> quotes = historyquoteJpaRepository
        .findByIdSecuritycurrencyAndDateBetweenOrderByDate(security.getIdSecuritycurrency(), fromDate, toDate);

    if (quotes.isEmpty()) {
      return null;
    }

    InstrumentHistoryquoteDTO result = new InstrumentHistoryquoteDTO();
    result.setIsin(isin);
    result.setCurrency(currency);
    result.setFromDate(fromDate);
    result.setToDate(toDate);
    result.setRecords(convertToRecords(quotes));
    return result;
  }

  /**
   * Queries local historyquote for a currency pair by currencies and date range.
   */
  private InstrumentHistoryquoteDTO queryLocalCurrencypairHistoryquote(String fromCurrency, String toCurrency,
      Date fromDate, Date toDate) {
    // Find the local currency pair
    Currencypair pair = currencypairJpaRepository.findByFromCurrencyAndToCurrency(fromCurrency, toCurrency);
    if (pair == null) {
      return null;
    }

    List<Historyquote> quotes = historyquoteJpaRepository
        .findByIdSecuritycurrencyAndDateBetweenOrderByDate(pair.getIdSecuritycurrency(), fromDate, toDate);

    if (quotes.isEmpty()) {
      return null;
    }

    InstrumentHistoryquoteDTO result = new InstrumentHistoryquoteDTO();
    result.setCurrency(fromCurrency);
    result.setToCurrency(toCurrency);
    result.setFromDate(fromDate);
    result.setToDate(toDate);
    result.setRecords(convertToRecords(quotes));
    return result;
  }

  /**
   * Converts Historyquote entities to HistoryquoteRecordDTOs.
   */
  private List<HistoryquoteRecordDTO> convertToRecords(List<Historyquote> quotes) {
    List<HistoryquoteRecordDTO> records = new ArrayList<>();
    for (Historyquote hq : quotes) {
      records.add(new HistoryquoteRecordDTO(
          hq.getDate(),
          hq.getOpen(),
          hq.getHigh(),
          hq.getLow(),
          hq.getClose(),
          hq.getVolume()));
    }
    return records;
  }

  /**
   * Returns yesterday's date (used as default toDate for push requests).
   */
  private Date getYesterday() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DAY_OF_MONTH, -1);
    return cal.getTime();
  }

  /**
   * Excludes the local server's own entry from the supplier list.
   * This prevents the server from attempting to query itself for data, which would fail token validation.
   *
   * @param suppliers list of GTNet supplier entries
   * @return filtered list excluding the local server's entry
   */
  private List<GTNet> excludeOwnEntry(List<GTNet> suppliers) {
    Integer myEntryId = globalparametersService.getGTNetMyEntryID();
    if (myEntryId == null) {
      return suppliers;
    }
    return suppliers.stream()
        .filter(supplier -> !supplier.getIdGtNet().equals(myEntryId))
        .collect(Collectors.toList());
  }

  /**
   * Result of querying a remote server, containing stored record count, response payload, and "want to receive" markers.
   */
  private static class QueryResult {
    final int storedCount;
    final HistoryquoteExchangeMsg responsePayload;
    final HistoryquoteExchangeMsg wantToReceive;

    QueryResult(int storedCount, HistoryquoteExchangeMsg responsePayload, HistoryquoteExchangeMsg wantToReceive) {
      this.storedCount = storedCount;
      this.responsePayload = responsePayload;
      this.wantToReceive = wantToReceive;
    }
  }

  /**
   * Stores received historical quotes in the appropriate tables.
   * For local instruments (idSecuritycurrency != null): stores in historyquote table
   * For foreign instruments (idSecuritycurrency == null): stores in gt_net_historyquote table
   */
  private int storeReceivedHistoryquotes(HistoryquoteExchangeMsg response, Integer myGTNetId) {
    int storedCount = 0;

    // Store securities
    if (response.securities != null) {
      for (InstrumentHistoryquoteDTO dto : response.securities) {
        storedCount += storeSecurityHistoryquotes(dto, myGTNetId);
      }
    }

    // Store currency pairs
    if (response.currencypairs != null) {
      for (InstrumentHistoryquoteDTO dto : response.currencypairs) {
        storedCount += storeCurrencypairHistoryquotes(dto, myGTNetId);
      }
    }

    return storedCount;
  }

  private int storeSecurityHistoryquotes(InstrumentHistoryquoteDTO dto, Integer myGTNetId) {
    if (dto.getRecords() == null || dto.getRecords().isEmpty()) {
      return 0;
    }

    // Find or create instrument in pool
    GTNetInstrumentSecurity instrument = gtNetInstrumentSecurityJpaRepository.findByIsinAndCurrency(
        dto.getIsin(), dto.getCurrency()).orElse(null);

    if (instrument == null) {
      instrument = gtNetInstrumentSecurityJpaRepository.findOrCreateInstrument(
          dto.getIsin(), dto.getCurrency(), null, myGTNetId);
    }

    return storeHistoryquotesForInstrument(instrument, dto.getRecords());
  }

  private int storeCurrencypairHistoryquotes(InstrumentHistoryquoteDTO dto, Integer myGTNetId) {
    if (dto.getRecords() == null || dto.getRecords().isEmpty()) {
      return 0;
    }

    // Find or create instrument in pool
    GTNetInstrumentCurrencypair instrument = gtNetInstrumentCurrencypairJpaRepository.findByFromCurrencyAndToCurrency(
        dto.getCurrency(), dto.getToCurrency()).orElse(null);

    if (instrument == null) {
      instrument = gtNetInstrumentCurrencypairJpaRepository.findOrCreateInstrument(
          dto.getCurrency(), dto.getToCurrency(), null, myGTNetId);
    }

    return storeHistoryquotesForInstrument(instrument, dto.getRecords());
  }

  private int storeHistoryquotesForInstrument(GTNetInstrument instrument, List<HistoryquoteRecordDTO> records) {
    int storedCount = 0;

    if (instrument.isLocalInstrument()) {
      // Store in local historyquote table
      for (HistoryquoteRecordDTO record : records) {
        if (record.getDate() != null && record.getClose() != null) {
          Optional<Historyquote> existing = historyquoteJpaRepository.findByIdSecuritycurrencyAndDate(
              instrument.getIdSecuritycurrency(), record.getDate());

          if (existing.isEmpty()) {
            Historyquote hq = new Historyquote();
            hq.setIdSecuritycurrency(instrument.getIdSecuritycurrency());
            hq.setDate(record.getDate());
            hq.setOpen(record.getOpen());
            hq.setHigh(record.getHigh());
            hq.setLow(record.getLow());
            hq.setClose(record.getClose());
            hq.setVolume(record.getVolume());
            historyquoteJpaRepository.save(hq);
            storedCount++;
          }
        }
      }
    } else {
      // Store in gt_net_historyquote table
      for (HistoryquoteRecordDTO record : records) {
        if (record.getDate() != null && record.getClose() != null) {
          Optional<GTNetHistoryquote> existing = gtNetHistoryquoteJpaRepository
              .findByGtNetInstrumentIdGtNetInstrumentAndDate(instrument.getIdGtNetInstrument(), record.getDate());

          if (existing.isEmpty()) {
            GTNetHistoryquote hq = new GTNetHistoryquote();
            hq.setGtNetInstrument(instrument);
            hq.setDate(record.getDate());
            hq.setOpen(record.getOpen());
            hq.setHigh(record.getHigh());
            hq.setLow(record.getLow());
            hq.setClose(record.getClose());
            hq.setVolume(record.getVolume());
            gtNetHistoryquoteJpaRepository.save(hq);
            storedCount++;
          }
        }
      }
    }

    return storedCount;
  }
}
