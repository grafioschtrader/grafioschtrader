package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.dto.SpinOffPreviewDto.Account;
import grafioschtrader.dto.SpinOffPreviewDto.Decision;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityAction;
import grafioschtrader.entities.Transaction;
import grafioschtrader.service.SpinOffLegs.Rate;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

class SpinOffLegsTest {
  @Test
  void oneCurrencyBooksMarketValuesAndBalancesExactly() {
    var legs = legs("USD", "USD", "USD", 100, 64, 48, 1, 1, 1, 2);
    assertThat(legs).extracting(Transaction::getCashaccountAmount).containsExactly(7600.0, -6400.0, -1200.0);
    assertThat(legs).extracting(Transaction::getQuotation).containsExactly(76.0, 64.0, 48.0);
    assertThat(legs).extracting(Transaction::getUnits).containsExactly(100.0, 100.0, 25.0);
    assertThat(legs.getLast().getCashaccountRoundingDiff()).isNull();
    legs.forEach(t -> t.validateCashaccountAmount(null, 2));
  }

  @Test
  void convertsThreeCurrenciesOnTheActionDate() {
    var legs = legs("USD", "CHF", "EUR", 100, 64, 48, 1.25, 0.8, 1, 2);
    assertThat(legs).extracting(Transaction::getCashaccountAmount).containsExactly(6320.0, -5120.0, -1200.0);
    assertThat(legs.getFirst().getQuotation()).isEqualTo(79);
    assertThat(legs).extracting(Transaction::getCurrencyExRate).containsExactly(0.8, 0.8, 1.0);
    assertThat(legs.getLast().getCashaccountRoundingDiff()).isNull();
    legs.forEach(t -> t.validateCashaccountAmount(null, 2));
  }

  @Test
  void recordsWholeCurrencyResidualWithoutDerivingTheChildQuoteFromIt() {
    var legs = legs("USD", "USD", "JPY", 1, 100.4, 0.8, 1, 1, 1, 0);
    assertThat(legs.getLast().getQuotation()).isCloseTo(0.8, within(1e-12));
    assertThat(legs.getLast().getCashaccountRoundingDiff()).isEqualTo(-1.0);
    assertThat(legs.stream().mapToDouble(Transaction::getCashaccountAmount).sum()).isZero();
    legs.forEach(t -> t.validateCashaccountAmount(null, 0));
  }

  private static List<Transaction> legs(String parent, String child, String cashCurrency, double n, double p, double c,
      double childToParent, double parentToCash, double childToCash, int precision) {
    SecurityAction action = new SecurityAction();
    action.setSecurityOld(security(parent));
    action.setSecurityNew(security(child));
    action.setFromFactor(4);
    action.setToFactor(1);
    action.setActionDate(LocalDate.of(2026, 3, 2));
    var amounts = SpinOffLegs.amounts(n, p, SpinOffLegs.theoreticalParentClose(p, c, 0.25, childToParent), parentToCash,
        precision);
    var account = new Account(1, "Brokerage", cashCurrency, n, n / 4, 0, parentToCash, childToCash, amounts.sale(),
        amounts.repurchase(), amounts.child(), List.of(), Decision.BOOK);
    return SpinOffLegs.book(action, 1, 42, new Cashaccount(1, cashCurrency), account, p, c, childToParent,
        new Rate(parent.equals(cashCurrency) ? null : 10, parentToCash),
        new Rate(child.equals(cashCurrency) ? null : 11, childToCash), precision);
  }

  private static Security security(String currency) {
    Security s = new Security();
    s.setCurrency(currency);
    Assetclass a = new Assetclass();
    a.setCategoryType(AssetclassType.EQUITIES);
    a.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    s.setAssetClass(a);
    return s;
  }
}
