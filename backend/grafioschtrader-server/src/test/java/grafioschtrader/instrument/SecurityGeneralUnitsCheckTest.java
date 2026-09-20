package grafioschtrader.instrument;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafiosch.types.OperationType;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.types.TransactionType;

@DisplayName("General-security unit integrity")
class SecurityGeneralUnitsCheckTest {

  @Test
  @DisplayName("Accept dividend units that equal the rounded sum of multiple purchases")
  void acceptDividendUnitsAtStoredPrecision() {
    Security security = mock(Security.class);
    when(security.getIdSecuritycurrency()).thenReturn(3782);
    SecuritysplitJpaRepository splits = mock(SecuritysplitJpaRepository.class);
    when(splits.getSecuritysplitMapByIdSecuritycurrency(3782)).thenReturn(Map.of(3782, List.of()));

    Transaction firstPurchase = transaction(security, LocalDate.of(2020, 2, 6), TransactionType.ACCUMULATE,
        330.35481168);
    Transaction secondPurchase = transaction(security, LocalDate.of(2024, 5, 3), TransactionType.ACCUMULATE,
        379.91845267);
    Transaction dividend = transaction(security, LocalDate.of(2024, 6, 19), TransactionType.DIVIDEND,
        330.35481168 + 379.91845267);
    dividend.setExDate(LocalDate.of(2024, 6, 3));

    assertThatCode(() -> SecurityGeneralUnitsCheck.checkUnitsIntegrity(splits, OperationType.ADD,
        List.of(firstPurchase, secondPurchase), dividend, security)).doesNotThrowAnyException();
  }

  private Transaction transaction(Security security, LocalDate date, TransactionType type, double units) {
    Transaction transaction = new Transaction();
    transaction.setSecuritycurrency(security);
    transaction.setTransactionTime(date.atStartOfDay());
    transaction.setTransactionType(type);
    transaction.setUnits(units);
    return transaction;
  }
}
