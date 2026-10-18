package grafioschtrader.task.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The expected split date travels as text in the old value of the task. The repeated attempt once wrote it in ISO
 * format while the task read only yyyyMMdd, so every repetition failed with "gt.split.date.missing.parse".
 */
class UpdateSplitForSecurityTaskTest {

  private static final LocalDate SPLIT_DATE = LocalDate.of(2026, 10, 16);

  @Test
  @DisplayName("The written split date is read back unchanged")
  void roundTrip() {
    String oldValue = UpdateSplitForSecurityTask.formatRequestedSplitDate(SPLIT_DATE);

    assertEquals("20261016", oldValue);
    assertEquals(SPLIT_DATE, UpdateSplitForSecurityTask.parseRequestedSplitDate(oldValue));
  }

  @Test
  @DisplayName("A waiting task with an ISO split date of an older version is still read")
  void legacyIsoValue() {
    assertEquals(SPLIT_DATE, UpdateSplitForSecurityTask.parseRequestedSplitDate("2026-10-16"));
    assertNull(UpdateSplitForSecurityTask.parseRequestedSplitDate(null));
  }

}
