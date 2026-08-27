package grafioschtrader.gtnet.handler.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfig;
import grafiosch.entities.GTNetConfigEntity;
import grafiosch.entities.GTNetEntity;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.AcceptRequestTypes;
import grafiosch.gtnet.ExchangeKindTypeRegistry;
import grafiosch.gtnet.GTNetGrantService;
import grafiosch.gtnet.GTNetServerStateTypes;
import grafiosch.gtnet.handler.GTNetMessageContext;
import grafiosch.gtnet.handler.HandlerResult;
import grafiosch.gtnet.m2m.model.MessageEnvelope;
import grafiosch.repository.GTNetMessageJpaRepository;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.model.msg.HistoryquoteCoverageQueryMsg;
import grafioschtrader.gtnet.model.msg.HistoryquoteCoverageQueryMsg.InstrumentIdentifier;
import grafioschtrader.gtnet.model.msg.HistoryquoteCoverageResponseMsg;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.GTNetHistoryquoteJpaRepository;
import grafioschtrader.repository.GTNetInstrumentCurrencypairJpaRepository;
import grafioschtrader.repository.GTNetInstrumentSecurityJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import tools.jackson.databind.ObjectMapper;

class HistoryquoteCoverageHandlerTest {

  private final SecurityJpaRepository securityRepository = mock(SecurityJpaRepository.class);
  private final CurrencypairJpaRepository currencypairRepository = mock(CurrencypairJpaRepository.class);
  private final GTNetInstrumentSecurityJpaRepository poolSecurityRepository = mock(
      GTNetInstrumentSecurityJpaRepository.class);
  private final GTNetInstrumentCurrencypairJpaRepository poolPairRepository = mock(
      GTNetInstrumentCurrencypairJpaRepository.class);
  private final HistoryquoteJpaRepository historyquoteRepository = mock(HistoryquoteJpaRepository.class);
  private final GTNetHistoryquoteJpaRepository poolHistoryquoteRepository = mock(GTNetHistoryquoteJpaRepository.class);
  private final GTNetMessageJpaRepository messageRepository = mock(GTNetMessageJpaRepository.class);
  private final GlobalparametersJpaRepository globalparameters = mock(GlobalparametersJpaRepository.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private HistoryquoteCoverageHandler handler;

  @BeforeEach
  void setUp() {
    handler = new HistoryquoteCoverageHandler(securityRepository, currencypairRepository, poolSecurityRepository,
        poolPairRepository, historyquoteRepository, poolHistoryquoteRepository);
    ReflectionTestUtils.setField(handler, "gtNetMessageJpaRepository", messageRepository);
    ReflectionTestUtils.setField(handler, "objectMapper", objectMapper);
    ReflectionTestUtils.setField(handler, "globalparametersJpaRepository", globalparameters);
    ReflectionTestUtils.setField(handler, "grantService", new GTNetGrantService(exchangeKindRegistry()));
    when(globalparameters.getMaxLimitExceededCount()).thenReturn(20);
    AtomicInteger ids = new AtomicInteger(10);
    when(messageRepository.saveMsg(any())).thenAnswer(invocation -> {
      GTNetMessage message = invocation.getArgument(0);
      message.setIdGtNetMessage(ids.incrementAndGet());
      return message;
    });
    when(securityRepository.findIdsWithGtNetHistoricalSend()).thenReturn(Set.of(10));
    when(currencypairRepository.findIdsWithGtNetHistoricalSend()).thenReturn(Set.of());
    when(currencypairRepository.findByCurrencyTuples(anyList())).thenReturn(List.of());
  }

  @Test
  void returnsLocalCoverageAndExplicitUnavailableEntries() throws Exception {
    Security security = new Security();
    security.setIdSecuritycurrency(10);
    security.setIsin("CH001");
    security.setCurrency("CHF");
    when(securityRepository.findByIsinCurrencyTuples(anyList())).thenReturn(List.of(security));
    when(historyquoteRepository.findCoverageBySecuritycurrencyIds(anyList()))
        .thenReturn(List.<Object[]>of(new Object[] { 10, LocalDate.of(2020, 1, 2), LocalDate.of(2024, 12, 30), 900L }));
    HistoryquoteCoverageQueryMsg query = HistoryquoteCoverageQueryMsg.forQuery(
        List.of(InstrumentIdentifier.forSecurity("CH001", "CHF"), InstrumentIdentifier.forSecurity("CH999", "CHF")),
        List.of());

    var result = handler.handle(context(query, (short) 10));

    MessageEnvelope envelope = ((HandlerResult.ImmediateResponse<GTNetMessage, MessageEnvelope>) result).response();
    assertThat(envelope.messageCode).isEqualTo(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_COVERAGE_RESPONSE_S.getValue());
    HistoryquoteCoverageResponseMsg response = objectMapper.treeToValue(envelope.payload,
        HistoryquoteCoverageResponseMsg.class);
    assertThat(response.securities).hasSize(2);
    assertThat(response.securities.get(0).isAvailable()).isTrue();
    assertThat(response.securities.get(0).getRecordCount()).isEqualTo(900);
    assertThat(response.securities.get(1).isAvailable()).isFalse();
  }

  @Test
  void rejectsRequestsAboveHistoryEntityLimit() throws Exception {
    HistoryquoteCoverageQueryMsg query = HistoryquoteCoverageQueryMsg.forQuery(
        List.of(InstrumentIdentifier.forSecurity("CH001", "CHF"), InstrumentIdentifier.forSecurity("CH002", "CHF")),
        List.of());

    var result = handler.handle(context(query, (short) 1));

    MessageEnvelope envelope = ((HandlerResult.ImmediateResponse<GTNetMessage, MessageEnvelope>) result).response();
    assertThat(envelope.messageCode)
        .isEqualTo(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_MAX_LIMIT_EXCEEDED_S.getValue());
    assertThat(envelope.message).contains("2 instruments requested", "limit is 1");
  }

  @Test
  void refusesARemoteThatExhaustedItsRequestViolationBudget() throws Exception {
    HistoryquoteCoverageQueryMsg query = HistoryquoteCoverageQueryMsg
        .forQuery(List.of(InstrumentIdentifier.forSecurity("CH001", "CHF")), List.of());
    GTNetMessageContext context = context(query, (short) 10);
    GTNetConfig config = new GTNetConfig();
    config.setRequestViolationCount((byte) 20);
    context.getRemoteGTNet().setGtNetConfig(config);

    var result = handler.handle(context);

    MessageEnvelope envelope = ((HandlerResult.ImmediateResponse<GTNetMessage, MessageEnvelope>) result).response();
    assertThat(envelope.messageCode)
        .isEqualTo(GTNetMessageCodeType.GT_NET_HISTORYQUOTE_MAX_LIMIT_EXCEEDED_S.getValue());
    assertThat(envelope.message).contains("max_limit violations");
    // The counter is already at the threshold; refusing must not push it further.
    assertThat(config.getRequestViolationCount()).isEqualTo((byte) 20);
  }

  @Test
  void refusesAPeerWithoutAnAcceptedDataExchange() throws Exception {
    // A completed handshake used to be enough, because the accept flag the handler tested is global rather than per
    // peer. Serving historical prices now needs a grant for this peer and this kind as well.
    HistoryquoteCoverageQueryMsg query = HistoryquoteCoverageQueryMsg
        .forQuery(List.of(InstrumentIdentifier.forSecurity("CH001", "CHF")), List.of());
    GTNetMessageContext context = new GTNetMessageContext(context(query, (short) 10).getMyGTNet(), peer(false),
        envelopeFor(query), List.of(), objectMapper);

    var result = handler.handle(context);

    assertThat(result).isInstanceOf(HandlerResult.ProcessingError.class);
    assertThat(((HandlerResult.ProcessingError<GTNetMessage, MessageEnvelope>) result).errorCode())
        .isEqualTo(GTNetGrantService.NO_GRANT);
  }

  private MessageEnvelope envelopeFor(HistoryquoteCoverageQueryMsg query) {
    MessageEnvelope request = new MessageEnvelope();
    request.messageCode = GTNetMessageCodeType.GT_NET_HISTORYQUOTE_COVERAGE_SEL_C.getValue();
    request.timestamp = LocalDateTime.now();
    request.sourceDomain = "https://remote";
    request.payload = objectMapper.valueToTree(query);
    return request;
  }

  /**
   * A peer with an accepted data exchange for historical prices, which is what the handler now requires on top of this
   * instance's own accept flag.
   */
  private static GTNet peer(boolean granted) {
    GTNet remote = new GTNet();
    remote.setIdGtNet(2);
    remote.setDomainRemoteName("https://remote");
    GTNetEntity entity = remote.getOrCreateEntityByKind(GTNetExchangeKindType.HISTORICAL_PRICES.getValue());
    GTNetConfigEntity configEntity = new GTNetConfigEntity();
    configEntity.setExchange(granted);
    entity.setGtNetConfigEntity(configEntity);
    return remote;
  }

  private static ExchangeKindTypeRegistry exchangeKindRegistry() {
    ExchangeKindTypeRegistry registry = new ExchangeKindTypeRegistry();
    for (GTNetExchangeKindType kind : GTNetExchangeKindType.values()) {
      registry.registerExchangeKind(kind);
    }
    return registry;
  }

  private GTNetMessageContext context(HistoryquoteCoverageQueryMsg query, short maxLimit) {
    GTNet myGTNet = new GTNet();
    myGTNet.setIdGtNet(1);
    myGTNet.setDomainRemoteName("https://local");
    GTNetEntity historyEntity = new GTNetEntity();
    historyEntity.setEntityKindValue(GTNetExchangeKindType.HISTORICAL_PRICES.getValue());
    historyEntity.setAcceptRequest(AcceptRequestTypes.AC_OPEN);
    historyEntity.setServerState(GTNetServerStateTypes.SS_OPEN);
    historyEntity.setMaxLimit(maxLimit);
    myGTNet.getGtNetEntities().add(historyEntity);
    GTNet remote = peer(true);
    MessageEnvelope request = new MessageEnvelope();
    request.messageCode = GTNetMessageCodeType.GT_NET_HISTORYQUOTE_COVERAGE_SEL_C.getValue();
    request.timestamp = LocalDateTime.now();
    request.sourceDomain = remote.getDomainRemoteName();
    request.payload = objectMapper.valueToTree(query);
    return new GTNetMessageContext(myGTNet, remote, request, List.of(), objectMapper);
  }
}
