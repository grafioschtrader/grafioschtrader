package grafioschtrader.priceupdate.intraday;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedIdentifier;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.SecurityCurrencypairJpaRepository;
import grafioschtrader.service.GlobalparametersService;

/**
 * Decisions of the batched intraday update: which securities are sent, in which requests, and how the outcome of a
 * request sets price and retry counter. Claim and save are stubbed, so nothing here says anything about persistence.
 */
class IntradayThruConnectorBatchTest {

  private static final String BATCH_ID = "gt.datafeed.batch";
  private static final String SINGLE_ID = "gt.datafeed.single";
  private static final LocalDateTime OLD = LocalDateTime.now().minusDays(1);

  private final List<List<Security>> batches = Collections.synchronizedList(new ArrayList<>());
  private final List<Security> singles = Collections.synchronizedList(new ArrayList<>());
  private Function<List<Security>, List<Security>> answer;
  private IntradayThruConnector<Security> thru;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    SecurityCurrencypairJpaRepository<Security> repository = mock(SecurityCurrencypairJpaRepository.class);
    when(repository.claimIntradayUpdate(any(), any(), any())).thenReturn(1);
    when(repository.save(any(Security.class))).thenAnswer(invocation -> invocation.getArgument(0));
    GlobalparametersService globalparametersService = mock(GlobalparametersService.class);
    when(globalparametersService.getMaxIntraRetry()).thenReturn((short) 4);
    when(globalparametersService.getSecurityCurrencyIntradayUpdateTimeout()).thenReturn(60);
    answer = _ -> List.of();
    thru = new IntradayThruConnector<>(repository, globalparametersService,
        List.of(connector(BATCH_ID, 2), connector(SINGLE_ID, 1)), new RecordingAccess(), null);
  }

  @Test
  @DisplayName("Five due securities of a connector with batch size 2 go out as 2, 2 and 1; a fresh one is not sent")
  void dueSecuritiesAreSplitIntoBatches() {
    List<Security> input = new ArrayList<>();
    for (int id = 1; id <= 5; id++) {
      input.add(security(id, BATCH_ID, null, null));
    }
    Security fresh = security(6, BATCH_ID, 50.0, LocalDateTime.now());
    input.add(2, fresh);

    List<Security> result = thru.updateLastPriceOfSecuritycurrency(input, true);

    assertEquals(List.of(2, 2, 1), batches.stream().map(List::size).toList());
    assertTrue(batches.stream().noneMatch(batch -> batch.contains(fresh)));
    assertEquals(input, result);
    assertEquals(50.0, fresh.getSLast());
    input.stream().filter(s -> s != fresh).forEach(s -> {
      assertEquals(100.0 + s.getIdSecuritycurrency(), s.getSLast());
      assertEquals((short) 0, s.getRetryIntraLoad());
    });
  }

  @Test
  @DisplayName("A failed request restores the previous price and counts a retry for every security of the batch")
  void failedRequestFailsTheWholeBatch() {
    Security first = security(1, BATCH_ID, 10.0, OLD);
    Security second = security(2, BATCH_ID, 20.0, OLD);
    answer = _ -> {
      throw new IllegalStateException("provider down");
    };

    thru.updateLastPriceOfSecuritycurrency(List.of(first, second), true);

    assertEquals(1, batches.size());
    assertEquals(10.0, first.getSLast());
    assertEquals(20.0, second.getSLast());
    assertEquals(OLD, first.getSTimestamp());
    assertEquals((short) 1, first.getRetryIntraLoad());
    assertEquals((short) 1, second.getRetryIntraLoad());
  }

  @Test
  @DisplayName("A security missing from the answer fails alone, its batch neighbour is updated")
  void missingSecurityFailsAlone() {
    Security first = security(1, BATCH_ID, 10.0, OLD);
    Security second = security(2, BATCH_ID, 20.0, OLD);
    answer = _ -> List.of(second);

    thru.updateLastPriceOfSecuritycurrency(List.of(first, second), true);

    assertEquals(101.0, first.getSLast());
    assertEquals((short) 0, first.getRetryIntraLoad());
    assertEquals(20.0, second.getSLast());
    assertEquals(OLD, second.getSTimestamp());
    assertEquals((short) 1, second.getRetryIntraLoad());
  }

  @Test
  @DisplayName("In parallel, single-connector securities keep their own path and the result keeps the input order")
  void mixedListInParallelKeepsOrder() {
    List<Security> input = List.of(security(1, BATCH_ID, null, null), security(2, SINGLE_ID, null, null),
        security(3, BATCH_ID, null, null), security(4, SINGLE_ID, null, null), security(5, BATCH_ID, null, null));

    List<Security> result = thru.updateLastPriceOfSecuritycurrency(input, false);

    assertEquals(input.size(), result.size());
    for (int i = 0; i < input.size(); i++) {
      assertSame(input.get(i), result.get(i));
    }
    assertEquals(2, singles.size());
    assertTrue(singles.stream().allMatch(s -> SINGLE_ID.equals(s.getIdConnectorIntra())));
    assertEquals(3, batches.stream().mapToInt(List::size).sum());
    assertTrue(batches.stream().allMatch(batch -> batch.size() <= 2));
    input.forEach(s -> assertEquals(100.0 + s.getIdSecuritycurrency(), s.getSLast()));
  }

  private static IFeedConnector connector(String id, int batchSize) {
    IFeedConnector connector = mock(IFeedConnector.class);
    when(connector.getID()).thenReturn(id);
    when(connector.getSecuritycurrencyFeedSupport(FeedSupport.FS_INTRA))
        .thenReturn(new FeedIdentifier[] { FeedIdentifier.SECURITY_URL });
    when(connector.getIntradayBatchSize()).thenReturn(batchSize);
    when(connector.getIntradayDelayedSeconds()).thenReturn(900);
    return connector;
  }

  private static Security security(int id, String idConnectorIntra, Double sLast, LocalDateTime sTimestamp) {
    Security security = new Security();
    security.setIdSecuritycurrency(id);
    security.setIdConnectorIntra(idConnectorIntra);
    security.setActiveToDate(LocalDate.now().plusYears(1));
    security.setSLast(sLast);
    security.setSTimestamp(sTimestamp);
    security.setRetryIntraLoad((short) 0);
    return security;
  }

  /** Writes price 100 + id like a connector would, before the configured answer decides the outcome. */
  private class RecordingAccess implements IIntradayEntityAccess<Security> {

    @Override
    public void updateIntraSecurityCurrency(Security security, IFeedConnector feedConnector) {
      singles.add(security);
      deliver(security);
    }

    @Override
    public List<Security> updateIntraSecurityCurrencies(List<Security> securities, IFeedConnector feedConnector) {
      batches.add(List.copyOf(securities));
      securities.forEach(this::deliver);
      return answer.apply(securities);
    }

    private void deliver(Security security) {
      security.setSLast(100.0 + security.getIdSecuritycurrency());
      security.setSTimestamp(LocalDateTime.now());
    }
  }
}
