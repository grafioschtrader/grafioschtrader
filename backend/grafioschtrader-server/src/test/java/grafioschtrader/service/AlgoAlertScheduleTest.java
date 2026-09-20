package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.Set;

import org.junit.jupiter.api.Test;

import grafioschtrader.entities.AlgoAlertEvaluationState;
import grafioschtrader.service.AlgoAlertSchedule.Window;

class AlgoAlertScheduleTest {
  private static final LocalTime OPEN = LocalTime.of(9, 0);
  private static final LocalTime CLOSE = LocalTime.of(17, 0);
  private static final Window ALWAYS = new Window(null, null, false, null);

  private Window window(String instant) {
    return AlgoAlertSchedule.window(Instant.parse(instant), false, "Europe/Zurich", OPEN, CLOSE, Set.of(), 900);
  }

  @Test
  void sessionUsesExchangeZoneAndDst() {
    assertThat(window("2026-09-04T06:59:00Z").eligible()).isFalse();
    assertThat(window("2026-09-04T07:00:00Z").eligible()).isTrue();
    assertThat(window("2026-01-05T07:00:00Z").eligible()).isFalse();
    assertThat(window("2026-01-05T08:00:00Z").eligible()).isTrue();
  }

  @Test
  void closingWaitsForProviderDelayAndNeverRunsOnWeekend() {
    assertThat(window("2026-09-04T15:00:00Z").eligible()).isFalse();
    assertThat(window("2026-09-04T15:14:59Z").eligible()).isFalse();
    Window closing = window("2026-09-04T15:15:00Z");
    assertThat(closing.eligible()).isTrue();
    assertThat(closing.closing()).isTrue();
    assertThat(window("2026-09-05T10:00:00Z").eligible()).isFalse();
    assertThat(window("2026-09-06T10:00:00Z").eligible()).isFalse();
  }

  @Test
  void weekendsUseExchangeDateRatherThanUtcDate() {
    assertThat(AlgoAlertSchedule.window(Instant.parse("2026-09-05T00:00:00Z"), false, "America/Los_Angeles", OPEN,
        LocalTime.of(18, 0), Set.of(), 0).eligible()).isTrue();
  }

  @Test
  void holidayAndInvalidExchangeDoNotEvaluate() {
    assertThat(AlgoAlertSchedule.window(Instant.parse("2026-09-04T10:00:00Z"), false, "Europe/Zurich", OPEN, CLOSE,
        Set.of(LocalDate.of(2026, 9, 4)), 0).eligible()).isFalse();
    assertThat(AlgoAlertSchedule.window(Instant.now(), false, "bad-zone", OPEN, CLOSE, Set.of(), 0).eligible())
        .isFalse();
    assertThat(AlgoAlertSchedule.window(Instant.now(), false, null, null, null, Set.of(), 0).eligible()).isFalse();
  }

  @Test
  void overnightSessionHasCorrectDayAndClosedGap() {
    Window night = AlgoAlertSchedule.window(Instant.parse("2026-09-04T01:00:00Z"), false, "UTC", LocalTime.of(22, 0),
        LocalTime.of(6, 0), Set.of(), 0);
    assertThat(night.eligible()).isTrue();
    assertThat(night.open()).isEqualTo(LocalDateTime.parse("2026-09-03T22:00:00"));
    assertThat(night.close()).isEqualTo(LocalDateTime.parse("2026-09-04T06:00:00"));
    Window gap = AlgoAlertSchedule.window(Instant.parse("2026-09-04T12:00:00Z"), false, "UTC", LocalTime.of(22, 0),
        LocalTime.of(6, 0), Set.of(), 0);
    assertThat(gap.closing()).isTrue();
  }

  @Test
  void cryptoDoesNotNeedExchangeAndRunsOnSunday() {
    assertThat(
        AlgoAlertSchedule.window(Instant.parse("2026-09-06T10:00:00Z"), true, null, null, null, Set.of(), 0).eligible())
            .isTrue();
  }

  @Test
  void recentSuccessOrFailureSuppressesUntilExactInterval() {
    LocalDateTime now = LocalDateTime.parse("2026-09-04T12:00:00");
    AlgoAlertEvaluationState state = state();
    for (int hours = 2; hours <= 6; hours++) {
      state.setLastAttempt(now.minusHours(hours).plusSeconds(1));
      assertThat(AlgoAlertSchedule.due(state, "config", now, hours, ALWAYS)).isFalse();
      state.setLastAttempt(now.minusHours(hours));
      assertThat(AlgoAlertSchedule.due(state, "config", now, hours, ALWAYS)).isTrue();
      state.setLastSuccess(now.minusMinutes(1));
      assertThat(AlgoAlertSchedule.due(state, "config", now, hours, ALWAYS)).isFalse();
      state.setLastSuccess(null);
    }
  }

  @Test
  void closingBypassesIntervalOnceUnlessClosingQuoteWasEvaluated() {
    Window closing = window("2026-09-04T15:15:00Z");
    LocalDateTime now = LocalDateTime.parse("2026-09-04T15:15:00");
    AlgoAlertEvaluationState state = state();
    state.setLastSuccess(now.minusMinutes(20));
    assertThat(AlgoAlertSchedule.due(state, "config", now, 4, closing)).isTrue();
    state.setClosingAttempt(closing.close());
    assertThat(AlgoAlertSchedule.due(state, "config", now.plusMinutes(5), 4, closing)).isFalse();
    state.setClosingAttempt(null);
    state.setQuoteTimestamp(closing.close());
    assertThat(AlgoAlertSchedule.due(state, "config", now, 4, closing)).isFalse();
  }

  @Test
  void configChangesResetDueTimeButCannotStealLiveLease() {
    LocalDateTime now = LocalDateTime.parse("2026-09-04T12:00:00");
    AlgoAlertEvaluationState state = state();
    state.setLastSuccess(now);
    assertThat(AlgoAlertSchedule.due(state, "changed", now, 4, ALWAYS)).isTrue();
    state.setLeaseUntil(now.plusMinutes(1));
    assertThat(AlgoAlertSchedule.due(state, "changed", now, 4, ALWAYS)).isFalse();
    assertThat(AlgoAlertSchedule.due(state, "changed", now.plusMinutes(1), 4, ALWAYS)).isTrue();
  }

  @Test
  void quoteMustBeRecentInCurrentSessionAndReachCloseForFinalCheck() {
    LocalDateTime now = LocalDateTime.parse("2026-09-04T15:15:00");
    Window closing = window("2026-09-04T15:15:00Z");
    assertThat(AlgoAlertSchedule.fresh(105.0, closing.close(), now, 4, closing)).isTrue();
    assertThat(AlgoAlertSchedule.fresh(105.0, closing.close().minusSeconds(1), now, 4, closing)).isFalse();
    assertThat(AlgoAlertSchedule.fresh(105.0, now.minusHours(5), now, 4, ALWAYS)).isFalse();
    assertThat(AlgoAlertSchedule.fresh(105.0, now.plusSeconds(1), now, 4, ALWAYS)).isFalse();
    assertThat(AlgoAlertSchedule.fresh(null, now, now, 4, ALWAYS)).isFalse();
    assertThat(AlgoAlertSchedule.fresh(105.0, null, now, 4, ALWAYS)).isFalse();
    assertThat(AlgoAlertSchedule.fresh(Double.NaN, now, now, 4, ALWAYS)).isFalse();
    Window open = window("2026-09-04T07:05:00Z");
    assertThat(AlgoAlertSchedule.fresh(105.0, open.open().minusSeconds(1), open.open().plusMinutes(5), 4, open))
        .isFalse();
  }

  private AlgoAlertEvaluationState state() {
    AlgoAlertEvaluationState state = new AlgoAlertEvaluationState();
    state.setConfigFingerprint("config");
    return state;
  }
}
