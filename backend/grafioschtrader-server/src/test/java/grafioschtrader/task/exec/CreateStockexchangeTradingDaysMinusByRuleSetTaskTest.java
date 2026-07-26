package grafioschtrader.task.exec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import grafiosch.entities.TaskDataChange;
import grafioschtrader.calendar.rule.HolidayRuleSet;
import grafioschtrader.calendar.rule.HolidayRuleSetYamlParser;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.TradingDaysMinus;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.repository.StockexchangeJpaRepository;
import grafioschtrader.repository.TradingCalendarRuleSetJpaRepository;
import grafioschtrader.repository.TradingDaysMinusJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;
import grafioschtrader.types.CreateType;

/**
 * Unit tests for the rule based trading calendar generator, exercised with mocked repositories so the closure to
 * {@code trading_days_minus} translation is verified without a database or the background worker.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Rule based trading calendar generation")
class CreateStockexchangeTradingDaysMinusByRuleSetTaskTest {

  private static final Integer ID_STOCKEXCHANGE = 42;
  private static final Integer ID_RULE_SET = 7;
  private static final LocalDate PERIOD_START = LocalDate.of(2024, 1, 2);
  private static final LocalDate PERIOD_END = LocalDate.of(2024, 12, 31);
  private static final LocalDate LABOUR_DAY = LocalDate.of(2024, 5, 1);
  private static final LocalDate NON_CLOSURE_WEEKDAY = LocalDate.of(2024, 5, 2);

  @Mock
  private StockexchangeJpaRepository stockexchangeJpaRepository;
  @Mock
  private TradingCalendarRuleSetJpaRepository tradingCalendarRuleSetJpaRepository;
  @Mock
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;
  @Mock
  private TradingDaysMinusJpaRepository tradingDaysMinusJpaRepository;

  @InjectMocks
  private CreateStockexchangeTradingDaysMinusByRuleSetTask task;

  /** A rule set authoritative for 2024 only, whose single rule closes the exchange on Labour Day. */
  private HolidayRuleSet labourDayRuleSet() {
    return HolidayRuleSetYamlParser.parse("""
        authoritativeFrom: 2024
        authoritativeThrough: 2024
        rules:
          - {name: LabourDay, type: FIXED, month: 5, day: 1}
        """);
  }

  private Stockexchange ruleBasedExchange() {
    Stockexchange se = new Stockexchange();
    se.setIdStockexchange(ID_STOCKEXCHANGE);
    se.setName("Test Exchange");
    se.setIdTradingCalendarRuleSet(ID_RULE_SET);
    return se;
  }

  private void stubPeriodAndRuleSet() {
    when(tradingDaysPlusJpaRepository.findTopByOrderByTradingDateDesc()).thenReturn(new TradingDaysPlus(PERIOD_END));
    when(tradingDaysPlusJpaRepository.findTopByOrderByTradingDateAsc()).thenReturn(new TradingDaysPlus(PERIOD_START));
    when(tradingCalendarRuleSetJpaRepository.getResolvedRuleSet(ID_RULE_SET)).thenReturn(labourDayRuleSet());
    // Both the closure and a nearby ordinary weekday are candidate trading days.
    when(tradingDaysPlusJpaRepository.findByTradingDateBetweenOrderByTradingDate(PERIOD_START, PERIOD_END))
        .thenReturn(List.of(new TradingDaysPlus(LABOUR_DAY), new TradingDaysPlus(NON_CLOSURE_WEEKDAY)));
  }

  @SuppressWarnings("unchecked")
  private List<TradingDaysMinus> captureSavedRows() {
    ArgumentCaptor<List<TradingDaysMinus>> captor = ArgumentCaptor.forClass(List.class);
    verify(tradingDaysMinusJpaRepository).saveAll(captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("A full rebuild writes the rule closures as RULE_CREATED rows over the whole period")
  void fullRebuildWritesClosures() {
    stubPeriodAndRuleSet();
    when(tradingDaysMinusJpaRepository.findUserDatesInRange(ID_STOCKEXCHANGE, PERIOD_START, PERIOD_END))
        .thenReturn(Set.of());
    when(stockexchangeJpaRepository.findById(ID_STOCKEXCHANGE)).thenReturn(java.util.Optional.of(ruleBasedExchange()));

    task.doWork(stockexchangeTask());

    // The whole period is cleared before the closures are re-inserted.
    verify(tradingDaysMinusJpaRepository).deleteDerivedByStockexchangeInRange(ID_STOCKEXCHANGE, PERIOD_START,
        PERIOD_END);

    List<TradingDaysMinus> saved = captureSavedRows();
    assertThat(saved).hasSize(1);
    TradingDaysMinus row = saved.get(0);
    assertThat(row.getTradingDateMinus()).isEqualTo(LABOUR_DAY);
    assertThat(row.getIdStockexchange()).isEqualTo(ID_STOCKEXCHANGE);
    assertThat(row.getCreateType()).isEqualTo(CreateType.RULE_CREATED);

    // The non-closure weekday is a candidate day but not a closure, so it is never marked.
    assertThat(saved).noneMatch(r -> r.getTradingDateMinus().equals(NON_CLOSURE_WEEKDAY));

    // The calendar is now filled to the end of the period.
    ArgumentCaptor<Stockexchange> seCaptor = ArgumentCaptor.forClass(Stockexchange.class);
    verify(stockexchangeJpaRepository).save(seCaptor.capture());
    assertThat(seCaptor.getValue().getMaxCalendarUpdDate()).isEqualTo(PERIOD_END);
  }

  @Test
  @DisplayName("A user-created non trading day on the same date is preserved, not duplicated")
  void userRowSurvives() {
    stubPeriodAndRuleSet();
    // The user already recorded Labour Day manually, so the generator must not insert it again.
    when(tradingDaysMinusJpaRepository.findUserDatesInRange(ID_STOCKEXCHANGE, PERIOD_START, PERIOD_END))
        .thenReturn(Set.of(LABOUR_DAY));
    when(stockexchangeJpaRepository.findById(ID_STOCKEXCHANGE)).thenReturn(java.util.Optional.of(ruleBasedExchange()));

    task.doWork(stockexchangeTask());

    assertThat(captureSavedRows()).isEmpty();
  }

  private TaskDataChange stockexchangeTask() {
    TaskDataChange taskDataChange = new TaskDataChange();
    taskDataChange.setEntity(Stockexchange.class.getSimpleName());
    taskDataChange.setIdEntity(ID_STOCKEXCHANGE);
    return taskDataChange;
  }
}
