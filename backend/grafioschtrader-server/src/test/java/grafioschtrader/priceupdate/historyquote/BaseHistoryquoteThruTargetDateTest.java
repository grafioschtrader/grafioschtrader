package grafioschtrader.priceupdate.historyquote;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaseHistoryquoteThruTargetDateTest {

  private static final LocalDate SATURDAY = LocalDate.of(2026, 11, 7);

  @Test
  @DisplayName("Sunday and Monday target Saturday, so a Friday quote missed by the Saturday run is still loaded")
  void sundayAndMondayGoBackToSaturday() {
    assertEquals(SATURDAY, BaseHistoryquoteThru.moveSundayMondayBackToSaturday(LocalDate.of(2026, 11, 8)));
    assertEquals(SATURDAY, BaseHistoryquoteThru.moveSundayMondayBackToSaturday(LocalDate.of(2026, 11, 9)));
  }

  @Test
  @DisplayName("Tuesday to Saturday remain their own target date")
  void otherDaysStayUnchanged() {
    LocalDate tuesday = LocalDate.of(2026, 11, 10);
    assertEquals(tuesday, BaseHistoryquoteThru.moveSundayMondayBackToSaturday(tuesday));
    assertEquals(SATURDAY, BaseHistoryquoteThru.moveSundayMondayBackToSaturday(SATURDAY));
  }
}
