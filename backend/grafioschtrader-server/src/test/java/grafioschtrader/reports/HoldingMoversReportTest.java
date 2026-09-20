package grafioschtrader.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.dto.IHeldInstrumentIntraday;
import grafioschtrader.dto.ISecuritycurrencyIdDateCloseCreateType;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.reports.HoldingMoversReport.Direction;
import grafioschtrader.reportviews.dashboard.HoldingMoversPayload.Movers;
import grafioschtrader.reportviews.dashboard.HoldingMoversPayload.Row;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TradingDaysMinusJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;

/** Exercises the report boundary without a Spring context or database. */
class HoldingMoversReportTest {

  @Test
  void roundsBothRankingsAfterSelectingTheStrongestRawMove() {
    // The stronger move has the larger id: rounding before sorting would select id 1 instead.
    HoldingMoversReport report = report(1.231, 1.234);
    Movers movers = report.getMovers(1, 1, null, Direction.WINNERS);
    for (var branch : movers.branches()) {
      assertEquals(2, branch.rankedCount());
      for (List<Row> ranking : List.of(branch.byPercentage(), branch.byAmount())) {
        assertEquals(1, ranking.size());
        assertEquals(2, ranking.getFirst().idSecuritycurrency());
        assertEquals(1.23, ranking.getFirst().changePercentage());
        Row row = ranking.getFirst();
        assertEquals(row.units() * (row.price() - row.previousPrice()), row.amountMC());
      }
    }
  }

  @Test
  void preservesTinyWinnersAndLosersButExcludesExactZero() {
    HoldingMoversReport report = report(1.236, -1.236, 0.004, -0.004, 0);
    for (Direction direction : Direction.values()) {
      Movers movers = report.getMovers(1, 10, null, direction);
      boolean winners = direction == Direction.WINNERS;
      for (var branch : movers.branches()) {
        for (List<Row> ranking : List.of(branch.byPercentage(), branch.byAmount())) {
          assertEquals(winners ? List.of(1, 3) : List.of(2, 4), ranking.stream().map(Row::idSecuritycurrency).toList());
          assertEquals(winners ? 1.24 : -1.24, ranking.getFirst().changePercentage());
          assertEquals(0d, ranking.getLast().changePercentage());
        }
      }
    }
  }

  private HoldingMoversReport report(double... percentages) {
    var holdings = mock(HoldSecurityaccountSecurityJpaRepository.class);
    var history = mock(HistoryquoteJpaRepository.class);
    var days = mock(TradingDaysPlusJpaRepository.class);
    var holidays = mock(TradingDaysMinusJpaRepository.class);
    var tenants = mock(TenantJpaRepository.class);
    var tenant = mock(Tenant.class);
    when(tenants.getReferenceById(1)).thenReturn(tenant);
    when(tenant.getCurrency()).thenReturn("CHF");
    when(days.existsById(any(LocalDate.class))).thenReturn(true);
    when(days.findTopByTradingDateLessThanOrderByTradingDateDesc(any(LocalDate.class)))
        .thenAnswer(call -> new TradingDaysPlus(call.<LocalDate>getArgument(0).minusDays(1)));

    List<IHeldInstrumentIntraday> instruments = new ArrayList<>();
    for (int i = 0; i < percentages.length; i++) {
      var instrument = mock(IHeldInstrumentIntraday.class);
      when(instrument.getIdSecuritycurrency()).thenReturn(i + 1);
      when(instrument.getIdStockexchange()).thenReturn(1);
      when(instrument.getCurrency()).thenReturn("CHF");
      when(instrument.getIdCurrencypairTenant()).thenReturn(null);
      when(instrument.getUnits()).thenReturn(2d);
      when(instrument.getSLast()).thenReturn(100 + percentages[i]);
      when(instrument.getSChangePercentage()).thenReturn(percentages[i]);
      instruments.add(instrument);
    }
    when(holdings.getHeldInstrumentsWithIntradayByTenant(eq(1), any(LocalDate.class))).thenReturn(instruments);
    when(history.getIdDateCloseByIdsAndDate(anyList(), any(LocalDate.class))).thenAnswer(call -> {
      LocalDate date = call.getArgument(1);
      long offset = date.toEpochDay() - LocalDate.now().minusDays(2).toEpochDay();
      List<ISecuritycurrencyIdDateCloseCreateType> closes = new ArrayList<>();
      for (int i = 0; i < percentages.length; i++) {
        var close = mock(ISecuritycurrencyIdDateCloseCreateType.class);
        when(close.getIdSecuritycurrency()).thenReturn(i + 1);
        when(close.getDate()).thenReturn(date);
        when(close.getClose()).thenReturn(100 * Math.pow(1 + percentages[i] / 100, offset));
        closes.add(close);
      }
      return closes;
    });
    return new HoldingMoversReport(holdings, history, days, holidays, tenants);
  }
}
