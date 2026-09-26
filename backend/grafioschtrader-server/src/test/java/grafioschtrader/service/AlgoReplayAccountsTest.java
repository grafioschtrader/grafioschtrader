package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.SecaccountTradingPeriod;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Transaction;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Account choice of a replay: security account priorities and the trading periods of the accounts. Two brokers, each a
 * portfolio with one security account and one CHF cash account; the funds answer from a fixed balance per cash account.
 */
class AlgoReplayAccountsTest {

  private static final LocalDate DAY = LocalDate.of(2020, 6, 1);

  private final Securityaccount brokerA = securityaccount(10, 1);
  private final Securityaccount brokerB = securityaccount(20, 2);
  private final Cashaccount cashA = cashaccount(11, brokerA.getPortfolio());
  private final Cashaccount cashB = cashaccount(21, brokerB.getPortfolio());
  private final Security bond = security(100, AssetclassType.FIXED_INCOME);

  /** Broker A is empty, broker B holds all the money. */
  private final AlgoReplayAccounts.SettlementFunds funds = new AlgoReplayAccounts.SettlementFunds() {
    @Override
    public double availableFor(Integer account, Cashaccount cashaccount, LocalDate date, String currency,
        double amount) {
      return cashaccount == cashB ? 1_000_000 : 0;
    }

    @Override
    public double transferable(Integer account, Cashaccount from, Cashaccount to, LocalDate date, double amount) {
      return availableFor(account, from, date, to.getCurrency(), amount);
    }
  };

  @Test
  @DisplayName("Without a priority the purchase goes where the money is")
  void automaticChoiceFollowsTheMoney() {
    var accounts = accounts(Map.of(), List.of());
    assertThat(accounts.resolve(bond, DAY, 5_000, funds).idSecurityaccount()).isEqualTo(brokerB.getId());
  }

  @Test
  @DisplayName("A priority account is chosen although another broker holds the money")
  void priorityWinsOverFunds() {
    var accounts = accounts(Map.of(bond.getId(), List.of(brokerA.getId(), brokerB.getId())), List.of());
    var booking = accounts.resolve(bond, DAY, 5_000, funds);
    assertThat(booking.idSecurityaccount()).isEqualTo(brokerA.getId());
    assertThat(booking.cashaccount()).isSameAs(cashA);
    assertThat(accounts.takePriorityFallback(bond)).isFalse();
  }

  @Test
  @DisplayName("A first priority whose trading periods exclude the instrument hands over to the second")
  void tradingPeriodMovesToSecondPriority() {
    allowOnly(brokerA, AssetclassType.EQUITIES);
    var accounts = accounts(Map.of(bond.getId(), List.of(brokerA.getId(), brokerB.getId())), List.of());
    assertThat(accounts.resolve(bond, DAY, 5_000, funds).idSecurityaccount()).isEqualTo(brokerB.getId());
    assertThat(accounts.takePriorityFallback(bond)).isFalse();
  }

  @Test
  @DisplayName("No eligible priority falls back to the automatic choice, reported once")
  void noEligiblePriorityFallsBack() {
    allowOnly(brokerA, AssetclassType.EQUITIES);
    var accounts = accounts(Map.of(bond.getId(), List.of(brokerA.getId())), List.of());
    assertThat(accounts.resolve(bond, DAY, 5_000, funds).idSecurityaccount()).isEqualTo(brokerB.getId());
    assertThat(accounts.takePriorityFallback(bond)).isTrue();
    accounts.resolve(bond, DAY, 5_000, funds);
    assertThat(accounts.takePriorityFallback(bond)).isFalse();
  }

  @Test
  @DisplayName("The automatic choice skips an account whose trading periods exclude the instrument")
  void automaticChoiceRespectsTradingPeriods() {
    allowOnly(brokerB, AssetclassType.EQUITIES);
    var accounts = accounts(Map.of(), List.of());
    assertThat(accounts.resolve(bond, DAY, 5_000, funds).idSecurityaccount()).isEqualTo(brokerA.getId());
  }

  @Test
  @DisplayName("A trading period that ended before the fill day no longer allows the instrument")
  void endedTradingPeriodExcludes() {
    SecaccountTradingPeriod period = period(AssetclassType.FIXED_INCOME);
    period.setDateTo(DAY.minusDays(1));
    brokerB.replaceTradingPeriods(List.of(period));
    var accounts = accounts(Map.of(), List.of());
    assertThat(accounts.resolve(bond, DAY, 5_000, funds).idSecurityaccount()).isEqualTo(brokerA.getId());
  }

  @Test
  @DisplayName("An instrument already held stays on its account whatever the priority says")
  void establishedHomeWinsOverPriority() {
    Transaction opening = new Transaction();
    opening.setSecuritycurrency(bond);
    opening.setIdSecurityaccount(brokerB.getId());
    opening.setCashaccount(cashB);
    var accounts = accounts(Map.of(bond.getId(), List.of(brokerA.getId())), List.of(opening));
    assertThat(accounts.resolve(bond, DAY, 5_000, funds).idSecurityaccount()).isEqualTo(brokerB.getId());
  }

  @Test
  @DisplayName("No account may trade the instrument: REPLAY_NO_ACCOUNT")
  void noEligibleAccountAtAll() {
    allowOnly(brokerA, AssetclassType.EQUITIES);
    allowOnly(brokerB, AssetclassType.EQUITIES);
    var accounts = accounts(Map.of(), List.of());
    assertThatThrownBy(() -> accounts.resolve(bond, DAY, 5_000, funds)).hasMessage("REPLAY_NO_ACCOUNT");
  }

  @Test
  @DisplayName("A funding target is no source on the same day, but is one again on a later day")
  void fundingTargetLockHoldsForOneDayOnly() {
    var accounts = accounts(Map.of(), List.of());
    accounts.rememberFundingTarget(cashB, DAY);
    assertThat(accounts.fundingSources(cashA, DAY)).isEmpty();
    assertThat(accounts.fundingSources(cashA, DAY.plusDays(91))).containsExactly(cashB);
  }

  private AlgoReplayAccounts accounts(Map<Integer, List<Integer>> priorities, List<Transaction> openingLedger) {
    return new AlgoReplayAccounts(List.of(brokerA, brokerB), List.of(cashA, cashB), openingLedger, "CHF", priorities);
  }

  private static void allowOnly(Securityaccount account, AssetclassType categoryType) {
    account.replaceTradingPeriods(List.of(period(categoryType)));
  }

  private static SecaccountTradingPeriod period(AssetclassType categoryType) {
    SecaccountTradingPeriod period = new SecaccountTradingPeriod();
    period.setCategoryType(categoryType);
    period.setSpecInvestInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    return period;
  }

  private static Securityaccount securityaccount(int id, int idPortfolio) {
    Portfolio portfolio = new Portfolio();
    portfolio.setIdPortfolio(idPortfolio);
    Securityaccount account = new Securityaccount();
    account.setIdSecuritycashAccount(id);
    account.setPortfolio(portfolio);
    return account;
  }

  private static Cashaccount cashaccount(int id, Portfolio portfolio) {
    Cashaccount cash = new Cashaccount();
    cash.setIdSecuritycashAccount(id);
    cash.setPortfolio(portfolio);
    cash.setCurrency("CHF");
    return cash;
  }

  private static Security security(int id, AssetclassType categoryType) {
    Assetclass assetclass = new Assetclass();
    assetclass.setCategoryType(categoryType);
    assetclass.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    Security security = new Security();
    security.setIdSecuritycurrency(id);
    security.setCurrency("CHF");
    security.setAssetClass(assetclass);
    return security;
  }
}
