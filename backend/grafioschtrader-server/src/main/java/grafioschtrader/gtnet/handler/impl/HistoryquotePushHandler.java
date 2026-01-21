package grafioschtrader.gtnet.handler.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.GTNetEntity;
import grafioschtrader.entities.GTNetHistoryquote;
import grafioschtrader.entities.GTNetInstrument;
import grafioschtrader.entities.GTNetInstrumentCurrencypair;
import grafioschtrader.entities.GTNetInstrumentSecurity;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.MessageCategory;
import grafioschtrader.gtnet.handler.AbstractGTNetMessageHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;
import grafioschtrader.gtnet.handler.HandlerResult;
import grafioschtrader.gtnet.m2m.model.HistoryquoteRecordDTO;
import grafioschtrader.gtnet.m2m.model.InstrumentHistoryquoteDTO;
import grafioschtrader.gtnet.model.msg.HistoryquoteExchangeMsg;
import grafioschtrader.repository.GTNetHistoryquoteJpaRepository;
import grafioschtrader.repository.GTNetInstrumentCurrencypairJpaRepository;
import grafioschtrader.repository.GTNetInstrumentSecurityJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.service.GTNetExchangeLogService;

/**
 * Handler for GT_NET_HISTORYQUOTE_PUSH_SEL_C messages from remote instances.
 *
 * Receives pushed historical price data from consumers who expressed interest via "want to receive" markers
 * during the initial EXCHANGE protocol. This completes the bidirectional data flow:
 *
 * <ol>
 *   <li>Consumer sends EXCHANGE request with instruments</li>
 *   <li>Supplier responds with data and "want to receive" markers for instruments it needs</li>
 *   <li>Consumer pushes data back via this PUSH message</li>
 *   <li>This handler stores the data and responds with ACK</li>
 * </ol>
 *
 * Storage routing uses JOIN-based locality lookup:
 * <ul>
 *   <li>Local instruments (matching ISIN+currency exists in local security table): stored in historyquote table</li>
 *   <li>Foreign instruments (no local match): stored in gt_net_historyquote table</li>
 * </ul>
 *
 * @see GTNetMessageCodeType#GT_NET_HISTORYQUOTE_PUSH_SEL_C
 * @see GTNetMessageCodeType#GT_NET_HISTORYQUOTE_PUSH_ACK_S
 */
@Component
public class HistoryquotePushHandler extends AbstractGTNetMessageHandler {

  private static final Logger log = LoggerFactory.getLogger(HistoryquotePushHandler.class);

  @Autowired
  private GTNetInstrumentSecurityJpaRepository gtNetInstrumentSecurityJpaRepository;

  @Autowired
  private GTNetInstrumentCurrencypairJpaRepository gtNetInstrumentCurrencypairJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private GTNetHistoryquoteJpaRepository gtNetHistoryquoteJpaRepository;

  @Autowired
  private GTNetExchangeLogService gtNetExchangeLogService;

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_HISTORYQUOTE_PUSH_SEL_C;
  }

  @Override
  public MessageCategory getCategory() {
    return MessageCategory.REQUEST;
  }

  @Override
  public HandlerResult handle(GTNetMessageContext context) throws Exception {
    // Validate local GTNet configuration
    if (context.getMyGTNet() == null) {
      return new HandlerResult.ProcessingError("NO_LOCAL_GTNET", "Local GTNet configuration not found");
    }

    // Check if this server accepts historyquote data
    Optional<GTNetEntity> historyEntity = context.getMyGTNet().getEntity(GTNetExchangeKindType.HISTORICAL_PRICES);
    if (historyEntity.isEmpty() || !historyEntity.get().isAccepting()) {
      log.debug("Server not accepting historyquote data pushes");
      return new HandlerResult.ProcessingError("NOT_ACCEPTING", "This server is not accepting historyquote data");
    }

    // Store incoming message for logging
    GTNetMessage storedRequest = storeIncomingMessage(context);

    // Parse push payload
    if (!context.hasPayload()) {
      return createAckResponse(context, storedRequest, 0);
    }

    HistoryquoteExchangeMsg pushPayload = context.getPayloadAs(HistoryquoteExchangeMsg.class);
    if (pushPayload == null || pushPayload.isEmpty()) {
      return createAckResponse(context, storedRequest, 0);
    }

    int totalRecordCount = pushPayload.getTotalRecordCount();
    log.info("Received historyquote push with {} securities, {} currencypairs ({} total records) from {}",
        pushPayload.securities != null ? pushPayload.securities.size() : 0,
        pushPayload.currencypairs != null ? pushPayload.currencypairs.size() : 0,
        totalRecordCount,
        context.getRemoteGTNet() != null ? context.getRemoteGTNet().getDomainRemoteName() : "unknown");

    // Store received data
    int acceptedCount = 0;

    // Store securities
    if (pushPayload.securities != null) {
      acceptedCount += storeSecurityHistoryquotes(pushPayload.securities);
    }

    // Store currency pairs
    if (pushPayload.currencypairs != null) {
      acceptedCount += storeCurrencypairHistoryquotes(pushPayload.currencypairs);
    }

    log.info("Accepted {} of {} pushed historyquote records from {}",
        acceptedCount, totalRecordCount,
        context.getRemoteGTNet() != null ? context.getRemoteGTNet().getDomainRemoteName() : "unknown");

    // Log exchange statistics as supplier (we're receiving data)
    if (context.getRemoteGTNet() != null) {
      gtNetExchangeLogService.logAsSupplier(context.getRemoteGTNet(), GTNetExchangeKindType.HISTORICAL_PRICES,
          pushPayload.getTotalInstrumentCount(), pushPayload.getTotalInstrumentCount(), acceptedCount);
    }

    return createAckResponse(context, storedRequest, acceptedCount);
  }

  /**
   * Stores security historyquotes from the push payload using batch locality lookup.
   *
   * @param dtos the list of instrument historyquote DTOs
   * @return number of records stored
   */
  private int storeSecurityHistoryquotes(List<InstrumentHistoryquoteDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return 0;
    }

    // Find or create instruments for all securities
    Map<String, GTNetInstrumentSecurity> instrumentMap = new HashMap<>();
    for (InstrumentHistoryquoteDTO dto : dtos) {
      if (dto.getIsin() != null && dto.getCurrency() != null) {
        String key = dto.getIsin() + ":" + dto.getCurrency();
        if (!instrumentMap.containsKey(key)) {
          GTNetInstrumentSecurity instrument = gtNetInstrumentSecurityJpaRepository
              .findByIsinAndCurrency(dto.getIsin(), dto.getCurrency())
              .orElseGet(() -> gtNetInstrumentSecurityJpaRepository
                  .findOrCreateInstrument(dto.getIsin(), dto.getCurrency()));
          instrumentMap.put(key, instrument);
        }
      }
    }

    if (instrumentMap.isEmpty()) {
      return 0;
    }

    // Determine locality via JOIN - which instruments have local securities
    List<Integer> instrumentIds = instrumentMap.values().stream()
        .map(GTNetInstrumentSecurity::getIdGtNetInstrument)
        .toList();
    Map<Integer, Integer> localityMap = buildLocalityMap(
        gtNetInstrumentSecurityJpaRepository.findLocalSecurityMappings(instrumentIds));

    // Store records for each DTO
    int storedCount = 0;
    for (InstrumentHistoryquoteDTO dto : dtos) {
      if (dto.getRecords() == null || dto.getRecords().isEmpty()) {
        continue;
      }

      String key = dto.getIsin() + ":" + dto.getCurrency();
      GTNetInstrumentSecurity instrument = instrumentMap.get(key);
      if (instrument == null) {
        continue;
      }

      Integer localSecurityId = localityMap.get(instrument.getIdGtNetInstrument());
      storedCount += storeHistoryquotesForInstrument(instrument, localSecurityId, dto.getRecords());
    }

    return storedCount;
  }

  /**
   * Stores currency pair historyquotes from the push payload using batch locality lookup.
   *
   * @param dtos the list of instrument historyquote DTOs
   * @return number of records stored
   */
  private int storeCurrencypairHistoryquotes(List<InstrumentHistoryquoteDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return 0;
    }

    // Find or create instruments for all currency pairs
    Map<String, GTNetInstrumentCurrencypair> instrumentMap = new HashMap<>();
    for (InstrumentHistoryquoteDTO dto : dtos) {
      if (dto.getCurrency() != null && dto.getToCurrency() != null) {
        String key = dto.getCurrency() + ":" + dto.getToCurrency();
        if (!instrumentMap.containsKey(key)) {
          GTNetInstrumentCurrencypair instrument = gtNetInstrumentCurrencypairJpaRepository
              .findByFromCurrencyAndToCurrency(dto.getCurrency(), dto.getToCurrency())
              .orElseGet(() -> gtNetInstrumentCurrencypairJpaRepository
                  .findOrCreateInstrument(dto.getCurrency(), dto.getToCurrency()));
          instrumentMap.put(key, instrument);
        }
      }
    }

    if (instrumentMap.isEmpty()) {
      return 0;
    }

    // Determine locality via JOIN - which instruments have local currency pairs
    List<Integer> instrumentIds = instrumentMap.values().stream()
        .map(GTNetInstrumentCurrencypair::getIdGtNetInstrument)
        .toList();
    Map<Integer, Integer> localityMap = buildLocalityMap(
        gtNetInstrumentCurrencypairJpaRepository.findLocalCurrencypairMappings(instrumentIds));

    // Store records for each DTO
    int storedCount = 0;
    for (InstrumentHistoryquoteDTO dto : dtos) {
      if (dto.getRecords() == null || dto.getRecords().isEmpty()) {
        continue;
      }

      String key = dto.getCurrency() + ":" + dto.getToCurrency();
      GTNetInstrumentCurrencypair instrument = instrumentMap.get(key);
      if (instrument == null) {
        continue;
      }

      Integer localCurrencypairId = localityMap.get(instrument.getIdGtNetInstrument());
      storedCount += storeHistoryquotesForInstrument(instrument, localCurrencypairId, dto.getRecords());
    }

    return storedCount;
  }

  /**
   * Builds a locality map from the JOIN query results.
   */
  private Map<Integer, Integer> buildLocalityMap(List<Object[]> mappings) {
    Map<Integer, Integer> localityMap = new HashMap<>();
    for (Object[] mapping : mappings) {
      Integer gtNetInstrumentId = ((Number) mapping[0]).intValue();
      Integer localId = ((Number) mapping[1]).intValue();
      localityMap.put(gtNetInstrumentId, localId);
    }
    return localityMap;
  }

  /**
   * Stores historyquotes for an instrument based on whether it's local or foreign.
   *
   * Storage routing:
   * - Local instruments (localSecuritycurrencyId != null): stored in historyquote table
   * - Foreign instruments (localSecuritycurrencyId == null): stored in gt_net_historyquote table
   *
   * @param instrument the GTNet instrument (security or currency pair)
   * @param localSecuritycurrencyId the local security/currencypair ID (null if foreign)
   * @param records the historyquote records to store
   * @return number of records stored (skips duplicates)
   */
  private int storeHistoryquotesForInstrument(GTNetInstrument instrument, Integer localSecuritycurrencyId,
      java.util.List<HistoryquoteRecordDTO> records) {
    int storedCount = 0;

    if (localSecuritycurrencyId != null) {
      // Store in local historyquote table
      for (HistoryquoteRecordDTO record : records) {
        if (record.getDate() != null && record.getClose() != null) {
          Optional<Historyquote> existing = historyquoteJpaRepository.findByIdSecuritycurrencyAndDate(
              localSecuritycurrencyId, record.getDate());

          if (existing.isEmpty()) {
            Historyquote hq = new Historyquote();
            hq.setIdSecuritycurrency(localSecuritycurrencyId);
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

  /**
   * Creates an ACK response with the count of accepted records.
   *
   * @param context the message context
   * @param storedRequest the stored incoming request message
   * @param acceptedCount number of records accepted
   * @return handler result with ACK response
   */
  private HandlerResult createAckResponse(GTNetMessageContext context, GTNetMessage storedRequest, int acceptedCount) {
    HistoryquoteExchangeMsg ackPayload = HistoryquoteExchangeMsg.forPushAck(acceptedCount);

    GTNetMessage responseMsg = storeResponseMessage(context, GTNetMessageCodeType.GT_NET_HISTORYQUOTE_PUSH_ACK_S,
        null, null, storedRequest);

    return new HandlerResult.ImmediateResponse(createResponseEnvelopeWithPayload(context, responseMsg, ackPayload));
  }
}
