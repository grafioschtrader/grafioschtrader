package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import grafiosch.types.OperationType;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.instrument.SecurityGeneralUnitsCheck;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.types.CreateType;
import grafioschtrader.types.SecurityActionRole;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TransactionType;

class SpinOffUnitsTest {
  private static final LocalDate EX_DATE = LocalDate.of(2026, 3, 2);

  @Test
  void measuresEntitlementInExDateShareBasisAndExcludesSameDaySale() {
    var purchase = trade(TransactionType.ACCUMULATE, 50, EX_DATE.minusDays(10), 1);
    var sell = trade(TransactionType.REDUCE, 15, EX_DATE.minusDays(2), 2);
    var sameDay = trade(TransactionType.REDUCE, 10, EX_DATE, 3);
    var split = new Securitysplit(1, EX_DATE, 1, 2, CreateType.CONNECTOR_CREATED);
    var laterSplit = new Securitysplit(1, EX_DATE.plusDays(1), 1, 3, CreateType.CONNECTOR_CREATED);

    assertThat(SpinOffApplyService.entitlement(List.of(purchase, sell, sameDay), List.of(split, laterSplit), EX_DATE))
        .containsEntry(1, 70.0);
  }

  @Test
  void ranksSystemSaleBeforeRepurchaseAndUserSaleAtMidnight() {
    var userSale = trade(TransactionType.REDUCE, 10, EX_DATE, 1);
    var systemSale = trade(TransactionType.REDUCE, 100, EX_DATE, 2);
    var repurchase = trade(TransactionType.ACCUMULATE, 100, EX_DATE, 3);
    systemSale.setSecurityActionRole(SecurityActionRole.SPIN_OFF_SALE);
    repurchase.setSecurityActionRole(SecurityActionRole.SPIN_OFF_REPURCHASE);

    assertThat(List.of(userSale, repurchase, systemSale).stream().sorted(Transaction.CALC_ORDER))
        .containsExactly(systemSale, repurchase, userSale);
    var shares = trade(TransactionType.ACCUMULATE, 50, EX_DATE.minusDays(1), 4);
    var repository = mock(SecuritysplitJpaRepository.class);
    var split = new Securitysplit(1, EX_DATE, 1, 2, CreateType.CONNECTOR_CREATED);
    when(repository.getSecuritysplitMapByIdSecuritycurrency(1)).thenReturn(Map.of(1, List.of(split)));
    assertThatCode(() -> SecurityGeneralUnitsCheck.checkUnitsIntegrity(repository, OperationType.ADD,
        List.of(shares, repurchase, systemSale), userSale, userSale.getSecurity())).doesNotThrowAnyException();
  }

  // An imported bank statement books the delivery of the 25 child units but no sale and repurchase of the parent
  @Test
  void proposesSkipForAnImportedChildDeliveryWithoutTheParentPair() {
    var delivery = trade(TransactionType.ACCUMULATE, 25, EX_DATE.plusDays(1), 1);
    var unrelated = trade(TransactionType.ACCUMULATE, 10, EX_DATE.plusDays(1), 2);

    assertThat(SpinOffApplyService.manuallyRecorded(List.of(), List.of(delivery), 100, 25, List.of(), List.of(),
        EX_DATE)).isTrue();
    assertThat(SpinOffApplyService.manuallyRecorded(List.of(), List.of(unrelated), 100, 25, List.of(), List.of(),
        EX_DATE)).isFalse();
  }

  private static Transaction trade(TransactionType type, double units, LocalDate date, int id) {
    Transaction t = new Transaction();
    var security = new Security();
    security.setIdSecuritycurrency(1);
    var assetClass = new Assetclass();
    assetClass.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    security.setAssetClass(assetClass);
    t.setSecuritycurrency(security);
    t.setIdTransaction(id);
    t.setIdSecurityaccount(1);
    t.setTransactionType(type);
    t.setTransactionTime(date.atStartOfDay());
    t.setUnits(units);
    return t;
  }
}
