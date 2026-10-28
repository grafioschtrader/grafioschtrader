package grafioschtrader.rest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;

import grafioschtrader.GlobalConstants;
import grafioschtrader.repository.TradingDaysBase.AddRemoveDay;
import grafioschtrader.repository.TradingDaysBase.SaveTradingDays;

/**
 * Creates the global trading calendar ({@code trading_days_plus}) for every year that dated data of later tests can
 * reach: from {@link GlobalConstants#OLDEST_TRADING_YEAR} through the year after the current one. A trading day is
 * every weekday except New Year's Day and Christmas Day.
 *
 * <p>
 * The fresh E2E schema leaves the calendar empty, and every security transaction is rejected for a date absent from it.
 * Creating these years here, before Playwright resumes at 050, keeps a failure of the calendar UI spec 055 from
 * cascading into every later spec that books, imports or replays dated data. Spec 055 still enters the remaining future
 * years through the UI and verifies that the years created here are complete.
 *
 * <p>
 * The calendar is written through the REST resource like the UI does, because saving also resets the cached trading-day
 * counts of {@code TradingDaysPlusJpaRepositoryImpl}. The test is rerunnable: each year receives only the difference to
 * the expected days.
 */
@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class TradingDaysPlusResourceTest extends BaseIntegrationTest {

  private static final MonthDay NEW_YEAR = MonthDay.of(1, 1);
  private static final MonthDay CHRISTMAS = MonthDay.of(12, 25);

  /** Response shape of {@code /api/tradingdaysplus}; the date boundaries of the full DTO are not needed here. */
  record TradingDays(List<LocalDate> dates) {
  }

  /** Last year created by this test; spec 055 creates the years after it through the UI. */
  static int lastIntegrationYear() {
    return LocalDate.now().getYear() + 1;
  }

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  @Test
  @Order(1)
  @DisplayName("Admin creates the global trading calendar from the oldest trading year through next year")
  void createTradingCalendarTest() {
    for (int year = GlobalConstants.OLDEST_TRADING_YEAR; year <= lastIntegrationYear(); year++) {
      List<LocalDate> expected = expectedTradingDays(year);
      List<AddRemoveDay> changes = differenceToExpected(loadTradingDays(year), expected);
      if (!changes.isEmpty()) {
        TradingDays saved = authenticatedClient(RestTestHelper.ADMIN).put().uri(RequestGTMappings.TRADINGDAYSPLUS_MAP)
            .body(createSaveTradingDays(year, changes)).exchange().expectStatus().isOk().expectBody(TradingDays.class)
            .returnResult().getResponseBody();
        Assertions.assertThat(saved.dates()).as("saved trading days of %d", year)
            .containsExactlyInAnyOrderElementsOf(expected);
      }
      Assertions.assertThat(loadTradingDays(year)).as("trading days of %d", year)
          .containsExactlyInAnyOrderElementsOf(expected);
    }
  }

  @Test
  @Order(2)
  @DisplayName("A non-admin user can not remove a day from the global trading calendar")
  void nonAdminCanNotChangeCalendarTest() {
    int year = GlobalConstants.OLDEST_TRADING_YEAR;
    LocalDate firstTradingDay = expectedTradingDays(year).getFirst();

    // SecurityGTConfig restricts the PUT to administrators before the repository is reached.
    authenticatedClient(RestTestHelper.LIMIT1).put().uri(RequestGTMappings.TRADINGDAYSPLUS_MAP)
        .body(createSaveTradingDays(year, List.of(addRemoveDay(firstTradingDay, false)))).exchange().expectStatus()
        .isForbidden();
    Assertions.assertThat(loadTradingDays(year)).contains(firstTradingDay);
  }

  private List<LocalDate> loadTradingDays(int year) {
    return authenticatedClient(RestTestHelper.ADMIN).get().uri(RequestGTMappings.TRADINGDAYSPLUS_MAP + "/" + year)
        .exchange().expectStatus().isOk().expectBody(TradingDays.class).returnResult().getResponseBody().dates();
  }

  private static List<LocalDate> expectedTradingDays(int year) {
    List<LocalDate> tradingDays = new ArrayList<>();
    for (LocalDate date = LocalDate.of(year, 1, 1); date.getYear() == year; date = date.plusDays(1)) {
      MonthDay monthDay = MonthDay.from(date);
      if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY
          && !monthDay.equals(NEW_YEAR) && !monthDay.equals(CHRISTMAS)) {
        tradingDays.add(date);
      }
    }
    return tradingDays;
  }

  private static List<AddRemoveDay> differenceToExpected(List<LocalDate> existing, List<LocalDate> expected) {
    Set<LocalDate> existingSet = new HashSet<>(existing);
    Set<LocalDate> expectedSet = new HashSet<>(expected);
    List<AddRemoveDay> changes = new ArrayList<>();
    expected.stream().filter(date -> !existingSet.contains(date))
        .forEach(date -> changes.add(addRemoveDay(date, true)));
    existing.stream().filter(date -> !expectedSet.contains(date))
        .forEach(date -> changes.add(addRemoveDay(date, false)));
    return changes;
  }

  private static AddRemoveDay addRemoveDay(LocalDate date, boolean add) {
    AddRemoveDay addRemoveDay = new AddRemoveDay();
    addRemoveDay.date = date;
    addRemoveDay.add = add;
    return addRemoveDay;
  }

  private static SaveTradingDays createSaveTradingDays(int year, List<AddRemoveDay> changes) {
    SaveTradingDays saveTradingDays = new SaveTradingDays();
    saveTradingDays.year = year;
    saveTradingDays.addRemoveDays = changes;
    return saveTradingDays;
  }

}
