package grafioschtrader.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetConfig;
import grafioschtrader.entities.GTNetEntity;
import grafioschtrader.entities.GTNetHistoryquote;
import grafioschtrader.entities.GTNetInstrument;
import grafioschtrader.entities.GTNetInstrumentCurrencypair;
import grafioschtrader.entities.GTNetInstrumentSecurity;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.gtnet.AcceptRequestTypes;
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
import grafioschtrader.repository.GTNetHistoryquoteJpaRepository;
import grafioschtrader.repository.GTNetInstrumentCurrencypairJpaRepository;
import grafioschtrader.repository.GTNetInstrumentSecurityJpaRepository;
import grafioschtrader.repository.GTNetJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;

/**
 * Service for orchestrating historical price exchange with GTNet providers.
 *
 * This service implements both consumer and supplier side flows for historical price sharing:
 *
 * <h3>Consumer Side Flow (requesting data from remote servers)</h3>
 * <ol>
 *   <li>Build request with instruments and date ranges</li>
 *   <li>Query push-open servers first (prioritized by consumerUsage)</li>
 *   <li>Query open servers for remaining unfilled instruments</li>
 *   <li>Store received data: local historyquote for local instruments, gt_net_historyquote for foreign</li>
 * </ol>
 *
 * <h3>Storage Decision</h3>
 * When storing received historical quotes:
 * <ul>
 *   <li>If instrument exists locally (GTNetInstrument.idSecuritycurrency != null): store in historyquote table</li>
 *   <li>If instrument is foreign (GTNetInstrument.idSecuritycurrency == null): store in gt_net_historyquote table</li>
 * </ul>
 *
 * @see GTNetLastpriceService for intraday price exchange
 */
@Service
public class GTNetHistoryquoteService {

  private static final Logger log = LoggerFactory.getLogger(GTNetHistoryquoteService.class);

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GTNetInstrumentSecurityJpaRepository gtNetInstrumentSecurityJpaRepository;

  @Autowired
  private GTNetInstrumentCurrencypairJpaRepository gtNetInstrumentCurrencypairJpaRepository;

  @Autowired
  private GTNetHistoryquoteJpaRepository gtNetHistoryquoteJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private BaseDataClient baseDataClient;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private GTNetExchangeLogService gtNetExchangeLogService;

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
   * Executes the historyquote exchange with remote GTNet suppliers.
   */
  private int executeHistoryquoteExchange(HistoryquoteExchangeMsg request) {
    int totalReceived = 0;

    // 1. Query push-open servers by priority
    List<GTNet> pushOpenSuppliers = getSuppliersByPriorityWithRandomization(
        gtNetJpaRepository.findHistoryquotePushOpenSuppliers());
    totalReceived += queryRemoteServers(pushOpenSuppliers, request);

    // 2. Query open servers for remaining
    List<GTNet> openSuppliers = getSuppliersByPriorityWithRandomization(
        gtNetJpaRepository.findHistoryquoteOpenSuppliers());
    totalReceived += queryRemoteServers(openSuppliers, request);

    log.info("Historyquote exchange complete: {} total records received", totalReceived);
    return totalReceived;
  }

  /**
   * Randomizes suppliers within the same priority level.
   */
  private List<GTNet> getSuppliersByPriorityWithRandomization(List<GTNet> suppliers) {
    if (suppliers == null || suppliers.size() <= 1) {
      return suppliers != null ? suppliers : new ArrayList<>();
    }

    // Group by priority (consumerUsage value from GTNetConfigEntity)
    Map<Byte, List<GTNet>> byPriority = suppliers.stream()
        .collect(Collectors.groupingBy(gtNet -> {
          return gtNet.getGtNetEntities().stream()
              .filter(e -> e.getEntityKind() == GTNetExchangeKindType.HISTORICAL_PRICES)
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
   * Queries remote servers for historical price data.
   */
  private int queryRemoteServers(List<GTNet> suppliers, HistoryquoteExchangeMsg request) {
    int totalReceived = 0;

    for (GTNet supplier : suppliers) {
      try {
        totalReceived += queryRemoteServer(supplier, request);
      } catch (Exception e) {
        log.warn("Failed to query GTNet server {} for historyquotes: {}",
            supplier.getDomainRemoteName(), e.getMessage());
      }
    }

    return totalReceived;
  }

  private int queryRemoteServer(GTNet supplier, HistoryquoteExchangeMsg request) {
    GTNetConfig config = supplier.getGtNetConfig();
    if (config == null || !config.isAuthorizedRemoteEntry()) {
      log.debug("Skipping unauthorized server: {}", supplier.getDomainRemoteName());
      return 0;
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

    if (!result.serverReachable()) {
      log.warn("GTNet server {} is unreachable", supplier.getDomainRemoteName());
      return 0;
    }

    MessageEnvelope response = result.response();
    if (response == null || response.payload == null) {
      log.debug("No historyquote data received from {}", supplier.getDomainRemoteName());
      return 0;
    }

    // Parse response
    try {
      HistoryquoteExchangeMsg responsePayload = objectMapper.treeToValue(response.payload, HistoryquoteExchangeMsg.class);

      if (responsePayload == null) {
        log.debug("Empty response payload from {}", supplier.getDomainRemoteName());
        return 0;
      }

      int recordCount = responsePayload.getTotalRecordCount();
      log.info("Received {} historyquote records from {}", recordCount, supplier.getDomainRemoteName());

      // Store received data
      int storedCount = storeReceivedHistoryquotes(responsePayload, myGTNetId);

      // Log exchange statistics as consumer
      int instrumentsSent = request.getTotalInstrumentCount();
      gtNetExchangeLogService.logAsConsumer(supplier, GTNetExchangeKindType.HISTORICAL_PRICES,
          instrumentsSent, storedCount, recordCount);

      return storedCount;

    } catch (JsonProcessingException e) {
      log.error("Failed to parse historyquote response from {}", supplier.getDomainRemoteName(), e);
      return 0;
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
