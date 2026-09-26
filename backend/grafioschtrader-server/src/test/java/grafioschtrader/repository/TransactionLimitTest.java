package grafioschtrader.repository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.service.EntityLimitService;
import grafioschtrader.config.LimitKeyConfig;
import grafioschtrader.exceptions.TransactionLimitExceededException;

@DisplayName("Transaction operations consume their full row count")
class TransactionLimitTest {
  private final TransactionJpaRepository transactions = mock(TransactionJpaRepository.class);
  private final EntityLimitService limits = mock(EntityLimitService.class);
  private final TransactionJpaRepositoryImpl repository = new TransactionJpaRepositoryImpl();

  TransactionLimitTest() {
    ReflectionTestUtils.setField(repository, "transactionJpaRepository", transactions);
    ReflectionTestUtils.setField(repository, "entityLimitService", limits);
  }

  @Test
  void fullOperationFitsExactlyButCannotExceedTheLimit() {
    when(limits.resolveForCurrentUser(LimitKeyConfig.KEY_TRANSACTION)).thenReturn(Optional.of(10));
    when(transactions.countByIdTenant(7)).thenReturn(8);
    assertThatCode(() -> repository.throwWhenTransactionLimitReached(7, 2)).doesNotThrowAnyException();
    when(transactions.countByIdTenant(7)).thenReturn(9);
    assertThatThrownBy(() -> repository.throwWhenTransactionLimitReached(7, 2))
        .isInstanceOfSatisfying(TransactionLimitExceededException.class, e -> {
          assertThat(e.getMessageKey()).isEqualTo("gt.transaction.limit.exceeded");
          assertThat(e.getArguments()).containsExactly(10);
        });
  }

  @Test
  void loweredLimitPreventsGrowthButNotOperationsAddingNoRows() {
    when(limits.resolveForCurrentUser(LimitKeyConfig.KEY_TRANSACTION)).thenReturn(Optional.of(5));
    when(transactions.countByIdTenant(7)).thenReturn(8);
    assertThatThrownBy(() -> repository.throwWhenTransactionLimitReached(7, 1))
        .isInstanceOf(TransactionLimitExceededException.class);
    assertThatCode(() -> repository.throwWhenTransactionLimitReached(7, 0)).doesNotThrowAnyException();
  }

  @Test
  void missingConfigurationIsUnlimitedAndAdditionCannotOverflow() {
    when(limits.resolveForCurrentUser(LimitKeyConfig.KEY_TRANSACTION)).thenReturn(Optional.empty());
    assertThatCode(() -> repository.throwWhenTransactionLimitReached(7, Integer.MAX_VALUE)).doesNotThrowAnyException();
    verifyNoInteractions(transactions);
    when(limits.resolveForCurrentUser(LimitKeyConfig.KEY_TRANSACTION)).thenReturn(Optional.of(Integer.MAX_VALUE));
    when(transactions.countByIdTenant(7)).thenReturn(Integer.MAX_VALUE - 1);
    assertThatThrownBy(() -> repository.throwWhenTransactionLimitReached(7, 2))
        .isInstanceOf(TransactionLimitExceededException.class);
  }
}
