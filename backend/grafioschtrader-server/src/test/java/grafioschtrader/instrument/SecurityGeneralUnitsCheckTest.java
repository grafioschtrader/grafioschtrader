package grafioschtrader.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafiosch.exceptions.DataViolationException;
import grafiosch.types.OperationType;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.types.TransactionType;

@DisplayName("General-security unit integrity")
class SecurityGeneralUnitsCheckTest {

  private static final double FIRST_PURCHASE = 330.35481168;
  private static final double SECOND_PURCHASE = 379.91845267;

  @Test
  @DisplayName("Accept dividend units that equal the rounded sum of multiple purchases")
  void acceptDividendUnitsAtStoredPrecision() {
    assertThatCode(() -> check(FIRST_PURCHASE + SECOND_PURCHASE)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Reject a dividend that claims one unit more than the purchases sum to")
  void rejectDividendUnitsAboveTheHeldAmount() {
    assertThatThrownBy(() -> check(FIRST_PURCHASE + SECOND_PURCHASE + 1))
        .isInstanceOfSatisfying(DataViolationException.class, error -> assertThat(error.getDataViolation())
            .singleElement().satisfies(violation -> {
              assertThat(violation.getField()).isEqualTo("units");
              assertThat(violation.getMessageKey()).isEqualTo("dividend.units.exceeds");
            }));
  }

  private static void check(double dividendUnits) {
    Security security = mock(Security.class);
    when(security.getIdSecuritycurrency()).thenReturn(3782);
    SecuritysplitJpaRepository splits = mock(SecuritysplitJpaRepository.class);
    when(splits.getSecuritysplitMapByIdSecuritycurrency(3782)).thenReturn(Map.of(3782, List.of()));

    Transaction firstPurchase = transaction(security, LocalDate.of(2020, 2, 6), TransactionType.ACCUMULATE,
        FIRST_PURCHASE);
    Transaction secondPurchase = transaction(security, LocalDate.of(2024, 5, 3), TransactionType.ACCUMULATE,
        SECOND_PURCHASE);
    Transaction dividend = transaction(security, LocalDate.of(2024, 6, 19), TransactionType.DIVIDEND, dividendUnits);
    dividend.setExDate(LocalDate.of(2024, 6, 3));
    SecurityGeneralUnitsCheck.checkUnitsIntegrity(splits, OperationType.ADD, List.of(firstPurchase, secondPurchase),
        dividend, security);
  }

  private static Transaction transaction(Security security, LocalDate date, TransactionType type, double units) {
    Transaction transaction = new Transaction();
    transaction.setSecuritycurrency(security);
    transaction.setTransactionTime(date.atStartOfDay());
    transaction.setTransactionType(type);
    transaction.setUnits(units);
    return transaction;
  }
}
