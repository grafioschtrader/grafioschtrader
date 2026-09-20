package grafioschtrader.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;

import grafioschtrader.entities.Historyquote;
import grafioschtrader.repository.AlgoTradingRepository;

/**
 * The no-look-ahead guarantee of a replay, as a property of the class rather than of the code that uses it.
 *
 * <p>
 * A decision engine asks for the history "through" a day and trusts what it gets. These tests assert that no request,
 * however it is phrased, can be answered with an observation of a later day - which is what makes a replayed result
 * mean anything at all.
 * </p>
 */
class AlgoReplayMarketDataTest {

  private static final LocalDate START = LocalDate.of(2026, 1, 1);
  private static final LocalDate END = START.plusDays(9);

  private final AlgoTradingRepository repository = mock(AlgoTradingRepository.class);

  @Test
  void volumeUsesLatestVisibleObservationEvenWhenItsVolumeIsMissing() {
    Historyquote first = new Historyquote(1, START, 100);
    first.setVolume(100L);
    Historyquote missing = new Historyquote(2, START.plusDays(1), 101);
    Historyquote future = new Historyquote(3, START.plusDays(2), 102);
    future.setVolume(999L);
    when(repository.history(eq(7), any(LocalDate.class), any(Limit.class))).thenReturn(List.of(future, missing, first));
    var market = new AlgoReplayMarketData(repository, START, END);
    assertEquals(100L, market.volume(7, START));
    assertNull(market.volume(7, START.plusDays(1)));
    assertNull(market.volume(7, START.minusDays(1)));
    assertEquals(999L, market.volume(7, START.plusDays(3)));
  }

  /** Ten consecutive days at 100, 101, ... as the repository delivers them: newest first. */
  private void quotes() {
    List<Historyquote> descending = new ArrayList<>();
    for (int day = 9; day >= 0; day--) {
      descending.add(new Historyquote(day + 1, START.plusDays(day), 100 + day));
    }
    when(repository.history(eq(7), any(LocalDate.class), any(Limit.class))).thenReturn(descending);
  }

  @Test
  @DisplayName("A day in the middle of the run sees its own close and everything before it, and nothing after")
  void historyEndsAtTheRequestedDay() {
    quotes();
    var market = new AlgoReplayMarketData(repository, START, END);

    List<Historyquote> history = market.history(7, START.plusDays(4));

    assertEquals(5, history.size(), "the opening day plus four");
    assertEquals(START, history.getFirst().getDate(), "ascending, oldest first");
    assertEquals(START.plusDays(4), history.getLast().getDate());
    assertTrue(history.stream().noneMatch(quote -> quote.getDate().isAfter(START.plusDays(4))));
  }

  @Test
  @DisplayName("The closing price of a day is the last observation up to it, never a later one")
  void closeNeverReadsForward() {
    quotes();
    var market = new AlgoReplayMarketData(repository, START, END);

    assertEquals(104.0, market.close(7, START.plusDays(4)));
    assertEquals(109.0, market.close(7, END));
  }

  @Test
  @DisplayName("A day before the first observation has no price rather than the first one available")
  void noPriceBeforeTheFirstObservation() {
    quotes();
    var market = new AlgoReplayMarketData(repository, START, END);

    assertNull(market.close(7, START.minusDays(1)));
    assertTrue(market.history(7, START.minusDays(1)).isEmpty());
  }

  @Test
  @DisplayName("Asking beyond the horizon of the run is refused instead of answered")
  void horizonIsAHardBound() {
    quotes();
    var market = new AlgoReplayMarketData(repository, START, START.plusDays(4));

    assertThrows(IllegalArgumentException.class, () -> market.history(7, START.plusDays(5)));
  }

  @Test
  @DisplayName("The history of an instrument is read once for the whole run")
  void observationsAreLoadedOnce() {
    quotes();
    var market = new AlgoReplayMarketData(repository, START, END);

    market.history(7, START.plusDays(2));
    market.history(7, START.plusDays(3));
    market.close(7, START.plusDays(4));

    verify(repository, times(1)).history(eq(7), any(LocalDate.class), any(Limit.class));
  }

  @Test
  @DisplayName("An instrument without history yields no observations rather than an error")
  void unknownInstrument() {
    when(repository.history(eq(8), any(LocalDate.class), any(Limit.class))).thenReturn(List.of());
    var market = new AlgoReplayMarketData(repository, START, END);

    assertTrue(market.history(8, START).isEmpty());
    assertNull(market.close(8, START));
  }

  @Test
  @DisplayName("The window covers the whole run even for an instrument quoted on every calendar day")
  void windowSpansTheRunInCalendarDays() {
    LocalDate opening = LocalDate.of(2006, 2, 6);
    LocalDate end = LocalDate.of(2026, 9, 7);
    when(repository.history(eq(7), any(LocalDate.class), any(Limit.class))).thenReturn(List.of());
    var market = new AlgoReplayMarketData(repository, opening, end);

    market.close(7, opening);

    var limit = ArgumentCaptor.forClass(Limit.class);
    verify(repository).history(eq(7), eq(end), limit.capture());
    long calendarDays = ChronoUnit.DAYS.between(opening, end) + 1;
    assertTrue(limit.getValue().max() >= calendarDays + AlgoReplayMarketData.OBSERVATION_WINDOW,
        "a currency pair carries a row on every calendar day, so the run alone consumes that many observations");
  }
}
