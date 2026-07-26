package grafioschtrader.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.SortedSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import grafioschtrader.GlobalConstants;
import grafioschtrader.calendar.rule.EasterCalculator;
import grafioschtrader.calendar.rule.HolidayRuleSet;
import grafioschtrader.entities.TradingCalendarRuleSet;
import grafioschtrader.repository.TradingCalendarRuleSetJpaRepository;
import grafioschtrader.test.start.GTforTest;

/**
 * Known-answer tests for the holiday rule engine and for the rule sets seeded into {@code trading_calendar_rule_set}.
 *
 * <p>
 * Their purpose is to separate two kinds of failure. When a trading calendar disagrees with the stored one, it must be
 * clear whether the rules describe the exchange wrongly or whether the engine resolves them wrongly. These tests pin
 * the engine, so any disagreement can be attributed to the rule data rather than to the calculation.
 * </p>
 *
 * <p>
 * The rules used to be classpath files and the test needed no database. They are shared data now, so the expected
 * answers are checked against the rule sets the migration seeded into the test database.
 * </p>
 */
@SpringBootTest(classes = GTforTest.class)
@ActiveProfiles("test")
@DisplayName("Holiday rule engine: known answers against the seeded rule sets")
class HolidayRuleEngineTest {

  @Autowired
  private TradingCalendarRuleSetJpaRepository tradingCalendarRuleSetJpaRepository;

  @Test
  @DisplayName("Western Easter is calculated correctly for a range of years")
  void westernEasterTest() {
    assertThat(EasterCalculator.westernEaster(2024)).isEqualTo(LocalDate.of(2024, 3, 31));
    assertThat(EasterCalculator.westernEaster(2025)).isEqualTo(LocalDate.of(2025, 4, 20));
    assertThat(EasterCalculator.westernEaster(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
    assertThat(EasterCalculator.westernEaster(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
    assertThat(EasterCalculator.westernEaster(2000)).isEqualTo(LocalDate.of(2000, 4, 23));
  }

  @Test
  @DisplayName("Orthodox Easter falls on a different Sunday than the Western one in most years")
  void orthodoxEasterTest() {
    assertThat(EasterCalculator.orthodoxEaster(2024)).isEqualTo(LocalDate.of(2024, 5, 5));
    assertThat(EasterCalculator.orthodoxEaster(2025)).isEqualTo(LocalDate.of(2025, 4, 20));
    assertThat(EasterCalculator.orthodoxEaster(2026)).isEqualTo(LocalDate.of(2026, 4, 12));
    assertThat(EasterCalculator.orthodoxEaster(2024).getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
  }

  /**
   * The 2026 closures of the US exchanges are the strongest single check available, because the stored trading calendar
   * in the live database already contains six of these dates for NASDAQ. Reproducing them from rules alone shows that
   * the engine and the rule file agree with data that was derived independently, from gaps in the price history of the
   * reference index.
   *
   * <p>
   * New Year and Christmas are the two the stored calendar does not hold: 1 January and 25 December are excluded from
   * {@code trading_days_plus} for every exchange, so they can never appear as a per-exchange closure. The rules
   * describe the exchange rather than GT's storage and therefore produce them, which is why the comparison report
   * cross-checks against {@code trading_days_plus} instead of treating the difference as a disagreement.
   * </p>
   */
  @Test
  @DisplayName("NYSE 2026 reproduces the closures the live database derived from the index")
  void nyseClosures2026Test() {
    SortedSet<LocalDate> closures = ruleSet("XNYS").closureDatesForYear(2026);

    assertThat(closures).containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 19),
        LocalDate.of(2026, 2, 16), LocalDate.of(2026, 4, 3), LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 19),
        LocalDate.of(2026, 7, 3), LocalDate.of(2026, 9, 7), LocalDate.of(2026, 11, 26), LocalDate.of(2026, 12, 25));
  }

  @Test
  @DisplayName("Independence Day on a Saturday is observed on the preceding Friday")
  void independenceDayObservanceTest() {
    assertThat(ruleSet("XNYS").closureDatesForYear(2026)).contains(LocalDate.of(2026, 7, 3))
        .doesNotContain(LocalDate.of(2026, 7, 4));
    // 4 July 2027 is a Sunday and moves forward to the Monday.
    assertThat(ruleSet("XNYS").closureDatesForYear(2027)).contains(LocalDate.of(2027, 7, 5));
  }

  @Test
  @DisplayName("New Year on a Saturday produces no closure, because the US exchanges do not close on 31 December")
  void newYearOnSaturdayIsNotTransferredTest() {
    // 1 January 2028 is a Saturday.
    assertThat(ruleSet("XNYS").closureDatesForYear(2027)).doesNotContain(LocalDate.of(2027, 12, 31));
    // 1 January 2028 itself must not appear as a closure on 31 December 2027 nor move to 3 January 2028.
    assertThat(ruleSet("XNYS").closureDatesForYear(2028)).doesNotContain(LocalDate.of(2028, 1, 3));
  }

  @Test
  @DisplayName("Juneteenth is absent before the exchange first observed it in 2022")
  void juneteenthValidityTest() {
    assertThat(ruleSet("XNYS").closureDatesForYear(2020)).doesNotContain(LocalDate.of(2020, 6, 19));
    assertThat(ruleSet("XNYS").closureDatesForYear(2021)).doesNotContain(LocalDate.of(2021, 6, 18));
    assertThat(ruleSet("XNYS").closureDatesForYear(2022)).contains(LocalDate.of(2022, 6, 20));
  }

  /**
   * SIX 2026 is the second independent check against the live database. The stored calendar holds six of these dates
   * plus 18 June 2026, which is an ordinary trading Thursday and was produced by a single missing quote in the
   * reference index. The rules are expected to produce the six real ones and not the seventh.
   */
  @Test
  @DisplayName("SIX 2026 produces the real closures and not the phantom day the index inferred")
  void sixClosures2026Test() {
    SortedSet<LocalDate> closures = ruleSet("XSWX").closureDatesForYear(2026);

    assertThat(closures).contains(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 4, 3),
        LocalDate.of(2026, 4, 6), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 14), LocalDate.of(2026, 5, 25));
    assertThat(closures).doesNotContain(LocalDate.of(2026, 6, 18));
    // Swiss National Day 2026 falls on a Saturday and is not transferred, so it yields no closure.
    assertThat(closures).doesNotContain(LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 3));
  }

  @Test
  @DisplayName("Shanghai 2026 matches the complete announced SSE schedule")
  void shanghaiClosures2026Test() {
    assertThat(ruleSet("XSHG").closureDatesForYear(2026)).containsExactly(LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 1, 2), LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 17),
        LocalDate.of(2026, 2, 18), LocalDate.of(2026, 2, 19), LocalDate.of(2026, 2, 20),
        LocalDate.of(2026, 2, 23), LocalDate.of(2026, 4, 6), LocalDate.of(2026, 5, 1),
        LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 5), LocalDate.of(2026, 6, 19),
        LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2),
        LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7));
  }

  @Test
  @DisplayName("Shanghai history includes exceptional closures without copying gaps from the reference index")
  void shanghaiHistoricalExceptionsTest() {
    HolidayRuleSet shanghai = ruleSet("XSHG");

    assertThat(shanghai.closureDates(2000, 2026)).contains(LocalDate.of(2000, 1, 31),
        LocalDate.of(2018, 4, 30), LocalDate.of(2018, 12, 31), LocalDate.of(2020, 1, 31))
        .doesNotContain(LocalDate.of(2003, 3, 11), LocalDate.of(2010, 12, 20), LocalDate.of(2019, 4, 29),
            LocalDate.of(2019, 4, 30), LocalDate.of(2026, 10, 8));
  }

  @Test
  @DisplayName("Shanghai and Shenzhen reject years without an announced authoritative schedule")
  void mainlandChinaAuthoritativeCoverageTest() {
    HolidayRuleSet shanghai = ruleSet("XSHG");
    HolidayRuleSet shenzhen = ruleSet("XSHE");

    assertThat(shanghai.isAuthoritativeForYear(1999)).isFalse();
    assertThat(shanghai.isAuthoritativeForYear(2000)).isTrue();
    assertThat(shanghai.isAuthoritativeForYear(2026)).isTrue();
    assertThat(shanghai.isAuthoritativeForYear(2027)).isFalse();
    assertThat(shenzhen.getAuthoritativeFrom()).isEqualTo(2000);
    assertThat(shenzhen.getAuthoritativeThrough()).isEqualTo(2026);
    assertThatThrownBy(() -> shanghai.closureDatesForYear(2027)).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("XSHG").hasMessageContaining("2027").hasMessageContaining("2000-2026");
    assertThatThrownBy(() -> shenzhen.closureDatesForYear(2027)).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("XSHE").hasMessageContaining("2027").hasMessageContaining("2000-2026");
  }

  @Test
  @DisplayName("The shared trading calendar is configured through the end of 2030")
  void tradingCalendarRangeTest() {
    assertThat(GlobalConstants.YOUNGEST_TRADING_CALENDAR_DAY).isEqualTo("2030-12-31");
  }

  @Test
  @DisplayName("Nth-weekday and last-weekday rules resolve correctly")
  void nthWeekdayTest() {
    // Fourth Thursday in November and last Monday in May.
    assertThat(ruleSet("XNYS").closureDatesForYear(2026)).contains(LocalDate.of(2026, 11, 26),
        LocalDate.of(2026, 5, 25));
    // Last Monday in August for the UK summer bank holiday.
    assertThat(ruleSet("XLON").closureDatesForYear(2026)).contains(LocalDate.of(2026, 8, 31));
  }

  @Test
  @DisplayName("Half days are recorded but never emitted as closures")
  void halfDaysAreNotClosuresTest() {
    HolidayRuleSet nyse = ruleSet("XNYS");
    // The Friday after Thanksgiving 2026 is a shortened session, not a closure.
    assertThat(nyse.closureDatesForYear(2026)).doesNotContain(LocalDate.of(2026, 11, 27));
    assertThat(nyse.halfDaysWithRuleName(2026)).containsKey(LocalDate.of(2026, 11, 27));
  }

  @Test
  @DisplayName("Closures never fall on a weekend")
  void noWeekendClosuresTest() {
    for (TradingCalendarRuleSet entity : tradingCalendarRuleSetJpaRepository.findAll()) {
      HolidayRuleSet ruleSet = tradingCalendarRuleSetJpaRepository
          .getResolvedRuleSet(entity.getIdTradingCalendarRuleSet());
      int firstYear = ruleSet.getAuthoritativeFrom() == null ? 2000 : Math.max(2000, ruleSet.getAuthoritativeFrom());
      int lastYear = ruleSet.getAuthoritativeThrough() == null ? 2030
          : Math.min(2030, ruleSet.getAuthoritativeThrough());
      for (LocalDate date : ruleSet.closureDates(firstYear, lastYear)) {
        assertThat(date.getDayOfWeek()).as("%s closure %s", entity.getName(), date).isNotIn(DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY);
      }
    }
  }

  @Test
  @DisplayName("Weekly rules model non-standard weekends and respect validity ranges")
  void weeklyClosuresTest() {
    assertThat(ruleSet("XSAU").closureDatesForYear(2026)).contains(LocalDate.of(2026, 7, 10));
    assertThat(ruleSet("XTAE").closureDatesForYear(2025)).contains(LocalDate.of(2025, 7, 11));
    assertThat(ruleSet("XTAE").closureDatesForYear(2026)).doesNotContain(LocalDate.of(2026, 7, 10));
  }

  @Test
  @DisplayName("Reference-calendar early closes remain trading days")
  void referenceEarlyClosesAreNotClosuresTest() {
    assertThat(ruleSet("CMES").closureDatesForYear(2026)).doesNotContain(LocalDate.of(2026, 11, 27));
    assertThat(ruleSet("IEPA").closureDatesForYear(2026)).doesNotContain(LocalDate.of(2026, 11, 26));
    assertThat(ruleSet("XTAE").closureDatesForYear(2026)).doesNotContain(LocalDate.of(2026, 9, 28));
  }

  @Test
  @DisplayName("A rule set extending another one receives the parent rules")
  void sameAsInheritanceTest() {
    assertThat(ruleSet("XNAS").closureDatesForYear(2026)).isEqualTo(ruleSet("XNYS").closureDatesForYear(2026));
    assertThat(ruleSet("XBRN").closureDatesForYear(2026)).isEqualTo(ruleSet("XSWX").closureDatesForYear(2026));
    assertThat(ruleSet("XLIS").closureDatesForYear(2026)).isEqualTo(ruleSet("XPAR").closureDatesForYear(2026));
    // Dublin is deliberately not a Euronext inheritor, but public holidays are not automatically exchange closures.
    assertThat(ruleSet("XMSM").closureDatesForYear(2026)).doesNotContain(LocalDate.of(2026, 3, 17));
  }

  /**
   * Euronext Lisbon trades on the Portuguese public holidays. This was established by the comparison report against the
   * live database and confirmed directly: on Carnival Tuesday 2018 the PSI-20 traded between 5356 and 5415 on a volume
   * of 49.8 million, which is an ordinary session and not a price carried forward over a closure.
   */
  @Test
  @DisplayName("Lisbon does not close on Portuguese public holidays")
  void lisbonTradesOnPortugueseHolidaysTest() {
    SortedSet<LocalDate> closures = ruleSet("XLIS").closureDates(2000, 2026);

    // Carnival Tuesday, Liberty Day, Portugal Day and the Immaculate Conception in years where each is a weekday.
    assertThat(closures).doesNotContain(LocalDate.of(2018, 2, 13), LocalDate.of(2018, 4, 25),
        LocalDate.of(2019, 6, 10), LocalDate.of(2021, 12, 8));
  }

  @Test
  @DisplayName("Hijri rules resolve to Gregorian dates within the requested year")
  void hijriRulesTest() {
    SortedSet<LocalDate> closures = ruleSet("XCAI").closureDatesForYear(2026);

    assertThat(closures).isNotEmpty();
    assertThat(closures).allSatisfy(date -> assertThat(date.getYear()).isEqualTo(2026));
  }

  @Test
  @DisplayName("Every seeded rule set parses, validates and produces closures")
  void allSeededRuleSetsResolveTest() {
    List<TradingCalendarRuleSet> all = tradingCalendarRuleSetJpaRepository.findAll();

    assertThat(all).hasSizeGreaterThanOrEqualTo(35);
    assertThat(all).extracting(TradingCalendarRuleSet::getMic).contains("XNYS", "XNAS", "XSWX", "XETR", "XPAR", "XLON",
        "XTKS", "XHKG", "MISX", "XCAI");
    for (TradingCalendarRuleSet entity : all) {
      assertThatCode(() -> tradingCalendarRuleSetJpaRepository.getResolvedRuleSet(entity.getIdTradingCalendarRuleSet()))
          .as("rule set %s does not resolve", entity.getName()).doesNotThrowAnyException();
      // Every set must produce at least one closure in a representative year, otherwise it is effectively empty.
      assertThat(tradingCalendarRuleSetJpaRepository.getResolvedRuleSet(entity.getIdTradingCalendarRuleSet())
          .closureDatesForYear(2026)).as("rule set %s produced no closure in 2026", entity.getName()).isNotEmpty();
    }
  }

  /**
   * Resolves the rule set written for a MIC, which is how the application reaches the calendar of an exchange.
   */
  private HolidayRuleSet ruleSet(String mic) {
    TradingCalendarRuleSet entity = tradingCalendarRuleSetJpaRepository.findByMic(mic)
        .orElseThrow(() -> new AssertionError("No trading calendar rule set found for MIC " + mic));
    return tradingCalendarRuleSetJpaRepository.getResolvedRuleSet(entity.getIdTradingCalendarRuleSet());
  }
}
