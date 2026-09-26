package grafioschtrader.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.entities.User;
import grafioschtrader.entities.*;
import grafioschtrader.exceptions.TransactionLimitExceededException;
import grafioschtrader.repository.*;
import grafioschtrader.types.TransactionType;

@DisplayName("Security actions check every new row before mutation")
class SecurityActionTransactionLimitTest {
  private final SecurityActionService service = new SecurityActionService();
  private final TransactionJpaRepository transactions = mock(TransactionJpaRepository.class);
  private final SecurityActionJpaRepository actions = mock(SecurityActionJpaRepository.class);
  private final SecurityActionApplicationJpaRepository applications = mock(
      SecurityActionApplicationJpaRepository.class);
  private final SecurityaccountJpaRepository accounts = mock(SecurityaccountJpaRepository.class);
  private final LocalDate date = LocalDate.of(2020, 6, 15);

  SecurityActionTransactionLimitTest() {
    ReflectionTestUtils.setField(service, "transactionJpaRepository", transactions);
    ReflectionTestUtils.setField(service, "securityActionJpaRepository", actions);
    ReflectionTestUtils.setField(service, "securityActionApplicationJpaRepository", applications);
    ReflectionTestUtils.setField(service, "securityaccountJpaRepository", accounts);
    var auth = new UsernamePasswordAuthenticationToken("limits", "");
    auth.setDetails(new User(7));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void actionCountsPairsOnlyForPositiveHoldingsOnTheActionDate() {
    Security security = new Security();
    security.setIdSecuritycurrency(10);
    SecurityAction action = new SecurityAction();
    action.setSecurityOld(security);
    action.setActionDate(date);
    when(actions.findById(1)).thenReturn(Optional.of(action));
    when(accounts.findByIdSecuritycashAccountAndIdTenant(20, 7)).thenReturn(new Securityaccount());
    when(accounts.findByIdSecuritycashAccountAndIdTenant(21, 7)).thenReturn(new Securityaccount());
    when(transactions.findByIdTenantAndIdSecurity(7, 10))
        .thenReturn(List.of(trade(20, 10, TransactionType.ACCUMULATE, date),
            trade(21, 5, TransactionType.ACCUMULATE, date), trade(22, 5, TransactionType.ACCUMULATE, date),
            trade(22, 5, TransactionType.REDUCE, date), trade(23, 10, TransactionType.ACCUMULATE, date.plusDays(1))));
    var limit = new TransactionLimitExceededException(3);
    doThrow(limit).when(transactions).throwWhenTransactionLimitReached(7, 4);
    assertThatThrownBy(() -> service.applySecurityAction(1)).isSameAs(limit);
    verify(applications, never()).save(any());
    verify(transactions, never()).reassignTransactionsToNewSecurity(any(), any(), any(), any(), any());
    verify(transactions, never()).save(any());
  }

  @Test
  void transferRequiresAllFourRows() {
    var limit = new TransactionLimitExceededException(3);
    doThrow(limit).when(transactions).throwWhenTransactionLimitReached(7, 4);
    assertThatThrownBy(() -> service.createTransfer(new SecurityTransfer())).isSameAs(limit);
    verify(transactions, never()).save(any());
  }

  private Transaction trade(int account, double units, TransactionType type, LocalDate on) {
    Transaction transaction = new Transaction();
    transaction.setIdSecurityaccount(account);
    transaction.setTransactionType(type);
    transaction.setUnits(units);
    transaction.setTransactionTime(on.atTime(12, 0));
    return transaction;
  }
}
