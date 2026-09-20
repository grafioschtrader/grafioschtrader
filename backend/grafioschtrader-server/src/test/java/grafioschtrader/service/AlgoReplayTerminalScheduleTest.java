package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class AlgoReplayTerminalScheduleTest {
  private static final LocalDate MONDAY = LocalDate.of(2020, 6, 22);

  @Test
  void catchesUpOldAndWeekendExpiriesInOriginalInstrumentOrder() {
    var schedule = new AlgoReplayTerminalSchedule();
    schedule.register(1, MONDAY.minusDays(1));
    schedule.register(2, MONDAY.minusYears(1));
    schedule.register(3, MONDAY);
    schedule.register(4, MONDAY.plusDays(1));
    schedule.register(5, null);

    assertThat(schedule.due(MONDAY)).containsExactly(1, 2, 3);
    schedule.complete(1);
    schedule.complete(3);
    assertThat(schedule.due(MONDAY.plusDays(1))).containsExactly(2, 4);
    schedule.complete(2);
    schedule.complete(4);
    assertThat(schedule.due(MONDAY.plusYears(10))).isEmpty();
  }

  @Test
  void registeringAnInstrumentAgainCannotRescheduleItsCapturedExpiry() {
    var schedule = new AlgoReplayTerminalSchedule();
    schedule.register(1, MONDAY);
    schedule.register(1, MONDAY.minusDays(1));
    assertThat(schedule.due(MONDAY.minusDays(1))).isEmpty();
    assertThat(schedule.due(MONDAY)).containsExactly(1);
    schedule.complete(1);
    schedule.register(1, MONDAY.plusDays(1));
    assertThat(schedule.due(MONDAY.plusDays(1))).isEmpty();
  }

  @Test
  void newlyEncounteredInstrumentsAreScheduledAndRunsRemainIndependent() {
    var first = new AlgoReplayTerminalSchedule();
    var second = new AlgoReplayTerminalSchedule();
    first.register(1, MONDAY);
    second.register(1, MONDAY);
    assertThat(first.due(MONDAY)).containsExactly(1);
    first.complete(1);
    first.register(2, MONDAY);
    assertThat(first.due(MONDAY.plusDays(1))).containsExactly(2);
    assertThat(second.due(MONDAY.plusDays(1))).containsExactly(1);
  }
}
