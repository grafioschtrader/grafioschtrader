package grafioschtrader.gtnet.handler.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetEntity;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.AcceptRequestTypes;
import grafiosch.gtnet.MessageCategory;
import grafiosch.gtnet.handler.AbstractGTNetMessageHandler;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.gtnet.handler.HandlerResult;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNetInstrumentCurrencypair;
import grafioschtrader.entities.GTNetInstrumentSecurity;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.model.msg.HistoryquoteCoverageQueryMsg;
import grafioschtrader.gtnet.model.msg.HistoryquoteCoverageQueryMsg.InstrumentIdentifier;
import grafioschtrader.gtnet.model.msg.HistoryquoteCoverageResponseMsg;
import grafioschtrader.gtnet.model.msg.HistoryquoteCoverageResponseMsg.InstrumentCoverageDTO;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.GTNetHistoryquoteJpaRepository;
import grafioschtrader.repository.GTNetInstrumentCurrencypairJpaRepository;
import grafioschtrader.repository.GTNetInstrumentSecurityJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/** Answers lightweight historical-price coverage queries. */
@Component
public class HistoryquoteCoverageHandler extends AbstractGTNetMessageHandler {

  private static final Logger log = LoggerFactory.getLogger(HistoryquoteCoverageHandler.class);

  private final SecurityJpaRepository securityJpaRepository;
  private final CurrencypairJpaRepository currencypairJpaRepository;
  private final GTNetInstrumentSecurityJpaRepository gtNetInstrumentSecurityJpaRepository;
  private final GTNetInstrumentCurrencypairJpaRepository gtNetInstrumentCurrencypairJpaRepository;
  private final HistoryquoteJpaRepository historyquoteJpaRepository;
  private final GTNetHistoryquoteJpaRepository gtNetHistoryquoteJpaRepository;

  public HistoryquoteCoverageHandler(SecurityJpaRepository securityJpaRepository,
      CurrencypairJpaRepository currencypairJpaRepository,
      GTNetInstrumentSecurityJpaRepository gtNetInstrumentSecurityJpaRepository,
      GTNetInstrumentCurrencypairJpaRepository gtNetInstrumentCurrencypairJpaRepository,
      HistoryquoteJpaRepository historyquoteJpaRepository,
      GTNetHistoryquoteJpaRepository gtNetHistoryquoteJpaRepository) {
    this.securityJpaRepository = securityJpaRepository;
    this.currencypairJpaRepository = currencypairJpaRepository;
    this.gtNetInstrumentSecurityJpaRepository = gtNetInstrumentSecurityJpaRepository;
    this.gtNetInstrumentCurrencypairJpaRepository = gtNetInstrumentCurrencypairJpaRepository;
    this.historyquoteJpaRepository = historyquoteJpaRepository;
    this.gtNetHistoryquoteJpaRepository = gtNetHistoryquoteJpaRepository;
  }

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_HISTORYQUOTE_COVERAGE_SEL_C;
  }

  @Override
  public MessageCategory getCategory() {
    return MessageCategory.REQUEST;
  }

  @Override
  @Transactional
  public HandlerResult<GTNetMessage, MessageEnvelope> handle(GTNetMessageContext context) {
    GTNet myGTNet = context.getMyGTNet();
    if (myGTNet == null) {
      return new HandlerResult.ProcessingError<>("NO_LOCAL_GTNET", "Local GTNet configuration not found");
    }
    Optional<GTNetEntity> historyEntity = myGTNet.getEntityByKind(GTNetExchangeKindType.HISTORICAL_PRICES.getValue());
    if (historyEntity.isEmpty() || !historyEntity.get().isAccepting()) {
      return new HandlerResult.ProcessingError<>("NOT_ACCEPTING", "This server is not accepting historyquote requests");
    }
    // The accept flag says whether this instance serves the kind at all; the grant says whether it serves it to
    // this peer. A completed handshake is not an entitlement to data - only an accepted data request is.
    if (!hasExchangeGrant(context, GTNetExchangeKindType.HISTORICAL_PRICES)) {
      return noGrantResult(GTNetExchangeKindType.HISTORICAL_PRICES);
    }

    GTNetMessage storedRequest = storeIncomingMessage(context);
    if (isBlockedByRequestViolations(context)) {
      log.warn("Refusing historyquote coverage query from {}: request violation budget exhausted",
          context.getRemoteGTNet() != null ? context.getRemoteGTNet().getDomainRemoteName() : "unknown");
      GTNetMessage blocked = storeResponseMessage(context,
          GTNetMessageCodeType.GT_NET_HISTORYQUOTE_MAX_LIMIT_EXCEEDED_S,
          "Requests are refused because too many max_limit violations were recorded", null, storedRequest);
      return new HandlerResult.ImmediateResponse<>(createResponseEnvelope(context, blocked));
    }
    HistoryquoteCoverageQueryMsg request = context.hasPayload()
        ? context.getPayloadAs(HistoryquoteCoverageQueryMsg.class)
        : new HistoryquoteCoverageQueryMsg();
    int requestedCount = request.getTotalInstrumentCount();
    Short maxLimit = historyEntity.get().getMaxLimit();
    if (maxLimit != null && requestedCount > maxLimit) {
      incrementViolationCount(context);
      String message = String.format("Request exceeded max_limit: %d instruments requested, limit is %d",
          requestedCount, maxLimit);
      GTNetMessage response = storeResponseMessage(context,
          GTNetMessageCodeType.GT_NET_HISTORYQUOTE_MAX_LIMIT_EXCEEDED_S, message, null, storedRequest);
      return new HandlerResult.ImmediateResponse<>(createResponseEnvelope(context, response));
    }

    HistoryquoteCoverageResponseMsg coverage = historyEntity.get().getAcceptRequest() == AcceptRequestTypes.AC_PUSH_OPEN
        ? queryPushOpenCoverage(request)
        : queryOpenCoverage(request);
    GTNetMessage response = storeResponseMessage(context, GTNetMessageCodeType.GT_NET_HISTORYQUOTE_COVERAGE_RESPONSE_S,
        null, null, storedRequest);
    log.debug("Returning historyquote coverage for {} instruments to {}", requestedCount, context.getSourceDomain());
    return new HandlerResult.ImmediateResponse<>(createResponseEnvelopeWithPayload(context, response, coverage));
  }

  private void incrementViolationCount(GTNetMessageContext context) {
    if (context.getRemoteGTNet() != null && context.getRemoteGTNet().getGtNetConfig() != null) {
      context.getRemoteGTNet().getGtNetConfig().incrementRequestViolationCount();
      saveGTNetConfig(context.getRemoteGTNet().getGtNetConfig());
    }
  }

  private HistoryquoteCoverageResponseMsg queryOpenCoverage(HistoryquoteCoverageQueryMsg request) {
    HistoryquoteCoverageResponseMsg response = new HistoryquoteCoverageResponseMsg();
    Set<Integer> sendableIds = new HashSet<>(securityJpaRepository.findIdsWithGtNetHistoricalSend());
    sendableIds.addAll(currencypairJpaRepository.findIdsWithGtNetHistoricalSend());

    List<Security> securities = securityJpaRepository.findByIsinCurrencyTuples(securityTuples(request.securities));
    Map<String, Security> securitiesByKey = securities.stream()
        .filter(s -> canSend(sendableIds, s.getIdSecuritycurrency()))
        .collect(Collectors.toMap(s -> s.getIsin() + ":" + s.getCurrency(), Function.identity(), (first, _) -> first));
    List<Currencypair> pairs = currencypairJpaRepository.findByCurrencyTuples(currencyTuples(request.currencypairs));
    Map<String, Currencypair> pairsByKey = pairs.stream().filter(p -> canSend(sendableIds, p.getIdSecuritycurrency()))
        .collect(Collectors.toMap(p -> p.getFromCurrency() + ":" + p.getToCurrency(), Function.identity(),
            (first, _) -> first));

    List<Integer> ids = new ArrayList<>();
    securitiesByKey.values().forEach(s -> ids.add(s.getIdSecuritycurrency()));
    pairsByKey.values().forEach(p -> ids.add(p.getIdSecuritycurrency()));
    Map<Integer, Coverage> coverageById = localCoverage(ids);
    response.securities = buildSecurityCoverage(request.securities, key -> {
      Security security = securitiesByKey.get(key);
      return security == null ? null : coverageById.get(security.getIdSecuritycurrency());
    });
    response.currencypairs = buildCurrencypairCoverage(request.currencypairs, key -> {
      Currencypair pair = pairsByKey.get(key);
      return pair == null ? null : coverageById.get(pair.getIdSecuritycurrency());
    });
    return response;
  }

  private HistoryquoteCoverageResponseMsg queryPushOpenCoverage(HistoryquoteCoverageQueryMsg request) {
    HistoryquoteCoverageResponseMsg response = new HistoryquoteCoverageResponseMsg();
    List<GTNetInstrumentSecurity> securities = gtNetInstrumentSecurityJpaRepository
        .findByIsinCurrencyTuples(securityTuples(request.securities));
    List<GTNetInstrumentCurrencypair> pairs = gtNetInstrumentCurrencypairJpaRepository
        .findByCurrencyTuples(currencyTuples(request.currencypairs));

    List<Integer> instrumentIds = new ArrayList<>();
    securities.forEach(s -> instrumentIds.add(s.getIdGtNetInstrument()));
    pairs.forEach(p -> instrumentIds.add(p.getIdGtNetInstrument()));
    Map<Integer, Integer> localIdsByInstrument = new HashMap<>();
    if (!instrumentIds.isEmpty()) {
      addMappings(localIdsByInstrument, gtNetInstrumentSecurityJpaRepository.findLocalSecurityMappings(instrumentIds));
      addMappings(localIdsByInstrument,
          gtNetInstrumentCurrencypairJpaRepository.findLocalCurrencypairMappings(instrumentIds));
    }

    Map<Integer, Coverage> localCoverage = localCoverage(new ArrayList<>(localIdsByInstrument.values()));
    List<Integer> foreignIds = instrumentIds.stream().filter(id -> !localIdsByInstrument.containsKey(id)).toList();
    Map<Integer, Coverage> poolCoverage = poolCoverage(foreignIds);
    Map<Integer, Coverage> coverageByInstrument = new HashMap<>(poolCoverage);
    localIdsByInstrument.forEach((instrumentId, localId) -> {
      Coverage coverage = localCoverage.get(localId);
      if (coverage != null) {
        coverageByInstrument.put(instrumentId, coverage);
      }
    });

    Map<String, GTNetInstrumentSecurity> securitiesByKey = securities.stream()
        .collect(Collectors.toMap(s -> s.getIsin() + ":" + s.getCurrency(), Function.identity(), (first, _) -> first));
    Map<String, GTNetInstrumentCurrencypair> pairsByKey = pairs.stream().collect(
        Collectors.toMap(p -> p.getFromCurrency() + ":" + p.getToCurrency(), Function.identity(), (first, _) -> first));
    response.securities = buildSecurityCoverage(request.securities, key -> {
      GTNetInstrumentSecurity security = securitiesByKey.get(key);
      return security == null ? null : coverageByInstrument.get(security.getIdGtNetInstrument());
    });
    response.currencypairs = buildCurrencypairCoverage(request.currencypairs, key -> {
      GTNetInstrumentCurrencypair pair = pairsByKey.get(key);
      return pair == null ? null : coverageByInstrument.get(pair.getIdGtNetInstrument());
    });
    return response;
  }

  private boolean canSend(Set<Integer> sendableIds, Integer id) {
    return sendableIds.isEmpty() || sendableIds.contains(id);
  }

  private List<String[]> securityTuples(List<InstrumentIdentifier> identifiers) {
    if (identifiers == null) {
      return List.of();
    }
    return identifiers.stream().filter(id -> id != null && id.isin != null && id.currency != null)
        .map(id -> new String[] { id.isin, id.currency }).toList();
  }

  private List<String[]> currencyTuples(List<InstrumentIdentifier> identifiers) {
    if (identifiers == null) {
      return List.of();
    }
    return identifiers.stream().filter(id -> id != null && id.currency != null && id.toCurrency != null)
        .map(id -> new String[] { id.currency, id.toCurrency }).toList();
  }

  private Map<Integer, Coverage> localCoverage(List<Integer> ids) {
    return ids.isEmpty() ? Map.of() : toCoverageMap(historyquoteJpaRepository.findCoverageBySecuritycurrencyIds(ids));
  }

  private Map<Integer, Coverage> poolCoverage(List<Integer> ids) {
    return ids.isEmpty() ? Map.of() : toCoverageMap(gtNetHistoryquoteJpaRepository.findCoverageByInstrumentIds(ids));
  }

  private Map<Integer, Coverage> toCoverageMap(List<Object[]> rows) {
    Map<Integer, Coverage> result = new HashMap<>();
    for (Object[] row : rows) {
      result.put(((Number) row[0]).intValue(),
          new Coverage((LocalDate) row[1], (LocalDate) row[2], ((Number) row[3]).intValue()));
    }
    return result;
  }

  private void addMappings(Map<Integer, Integer> target, List<Object[]> rows) {
    rows.forEach(row -> target.put(((Number) row[0]).intValue(), ((Number) row[1]).intValue()));
  }

  private List<InstrumentCoverageDTO> buildSecurityCoverage(List<InstrumentIdentifier> identifiers,
      Function<String, Coverage> coverageLookup) {
    if (identifiers == null) {
      return new ArrayList<>();
    }
    return identifiers.stream().map(id -> {
      if (id == null) {
        return InstrumentCoverageDTO.notAvailable(null, null, null);
      }
      Coverage coverage = coverageLookup.apply(id.getKey());
      return coverage == null ? InstrumentCoverageDTO.notAvailable(id.isin, id.currency, null)
          : InstrumentCoverageDTO.forSecurity(id.isin, id.currency, coverage.minDate(), coverage.maxDate(),
              coverage.recordCount());
    }).toList();
  }

  private List<InstrumentCoverageDTO> buildCurrencypairCoverage(List<InstrumentIdentifier> identifiers,
      Function<String, Coverage> coverageLookup) {
    if (identifiers == null) {
      return new ArrayList<>();
    }
    return identifiers.stream().map(id -> {
      if (id == null) {
        return InstrumentCoverageDTO.notAvailable(null, null, null);
      }
      Coverage coverage = coverageLookup.apply(id.getKey());
      return coverage == null ? InstrumentCoverageDTO.notAvailable(null, id.currency, id.toCurrency)
          : InstrumentCoverageDTO.forCurrencypair(id.currency, id.toCurrency, coverage.minDate(), coverage.maxDate(),
              coverage.recordCount());
    }).toList();
  }

  private record Coverage(LocalDate minDate, LocalDate maxDate, int recordCount) {
  }
}
