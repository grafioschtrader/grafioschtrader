package grafioschtrader.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.common.ClientClock;
import grafiosch.entities.User;
import grafiosch.service.EntityLimitService;
import grafioschtrader.dto.QuoteToleranceRange;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.StandingOrder;
import grafioschtrader.entities.StandingOrderCashaccount;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.RepeatUnit;
import grafioschtrader.types.TransactionType;

class ClientDateRepositoryTest {
  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void futureStandingOrderReceivesItsFirstExecutionDateOnSave() throws Exception {
    var repo = new StandingOrderJpaRepositoryImpl();
    var orders = mock(StandingOrderJpaRepository.class);
    var parameters = mock(GlobalparametersService.class);
    ReflectionTestUtils.setField(repo, "standingOrderJpaRepository", orders);
    ReflectionTestUtils.setField(repo, "entityLimitService", mock(EntityLimitService.class));
    ReflectionTestUtils.setField(repo, "tenantJpaRepository", mock(TenantJpaRepository.class));
    ReflectionTestUtils.setField(repo, "transactionJpaRepository", mock(TransactionJpaRepository.class));
    ReflectionTestUtils.setField(repo, "globalparametersService", parameters);
    when(parameters.getStandingOrderQuoteToleranceRange()).thenReturn(new QuoteToleranceRange((byte) -3, (byte) 3));
    when(orders.save(any(StandingOrder.class))).thenAnswer(call -> call.getArgument(0));
    User user = new User();
    user.setIdTenant(1);
    var authentication = new UsernamePasswordAuthenticationToken("user", "unused");
    authentication.setDetails(user);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    var order = new StandingOrderCashaccount();
    order.setIdTenant(1);
    order.setTransactionType(TransactionType.DEPOSIT);
    order.setRepeatUnit(RepeatUnit.DAYS);
    order.setValidFrom(LocalDate.of(2026, 9, 15));
    try (var clock = mockStatic(ClientClock.class)) {
      clock.when(ClientClock::today).thenReturn(LocalDate.of(2026, 9, 1));
      assertEquals(order.getValidFrom(), repo.saveOnlyAttributes(order, null, Set.of()).getNextExecutionDate());
      // A never-executed order created with an older version is repaired on its next save as well.
      order.setNextExecutionDate(null);
      assertEquals(order.getValidFrom(), repo.saveOnlyAttributes(order, order, Set.of()).getNextExecutionDate());
    }
  }

  @Test
  void tradingDayCacheUsesTheSuppliedYesterday() {
    var repo = new TradingDaysPlusJpaRepositoryImpl();
    var days = mock(TradingDaysPlusJpaRepository.class);
    ReflectionTestUtils.setField(repo, "tradingDaysPlusJpaRepository", days);
    LocalDate date = LocalDate.of(2026, 9, 15);
    when(days.countByTradingDateBetween(date, date)).thenReturn(1L);
    assertFalse(repo.hasTradingDayBetweenUntilYesterday(date, date));
    assertTrue(repo.hasTradingDayBetweenUntilYesterday(date, date.plusDays(1)));
    assertFalse(repo.hasTradingDayBetweenUntilYesterday(date, date));
  }

  @Test
  void manualQuoteValidationUsesClientTodayWithoutALoginOffset() {
    var repo = new HistoryquoteJpaRepositoryImpl();
    var quote = new Historyquote();
    LocalDate today = LocalDate.of(2026, 9, 16);
    try (var clock = mockStatic(ClientClock.class)) {
      clock.when(ClientClock::today).thenReturn(today);
      quote.setDate(today.minusDays(1));
      assertEquals(1L, (Long) ReflectionTestUtils.invokeMethod(repo, "checkDatePastMinus1Day", quote));
      quote.setDate(today);
      assertThrows(IllegalArgumentException.class,
          () -> ReflectionTestUtils.invokeMethod(repo, "checkDatePastMinus1Day", quote));
    }
  }
}
