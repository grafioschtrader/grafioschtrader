package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.common.DataHelper;
import grafiosch.entities.EntityLimit;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.EntityLimitJpaRepository;
import grafiosch.service.EntityLimitService;
import grafioschtrader.algo.SimulationTenantCreateDTO;
import grafioschtrader.algo.strategy.model.AlgoStrategyImplementationType;
import grafioschtrader.config.LimitKeyConfig;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoEventLog;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoSimulationResult;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityBondTerms;
import grafioschtrader.entities.SecuritySimulationMetadata;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.StandingOrderCashaccount;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.repository.AlgoEventLogJpaRepository;
import grafioschtrader.repository.AlgoSimulationResultJpaRepository;
import grafioschtrader.repository.HoldCashaccountBalanceJpaRepository;
import grafioschtrader.repository.SimulationSourceRepository;
import grafioschtrader.repository.SimulationTenantService;
import grafioschtrader.repository.StandingOrderJpaRepository;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.types.AlgoEventType;
import grafioschtrader.types.AlgoSimulationRunStatus;
import grafioschtrader.types.HistoryquoteCreateType;
import grafioschtrader.types.PeriodDayPosition;
import grafioschtrader.types.RepeatUnit;
import grafioschtrader.types.SimulationInitializationMode;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.TransactionType;
import grafioschtrader.types.WeekendAdjustType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * The run contract of a historical replay, against an environment that holds nothing but cash.
 *
 * <p>
 * A cash-only environment is deliberate rather than a shortcut: it is the one case where the expected result of the
 * whole date loop is known without a single price. Equity cannot move, so the run must complete with a total return of
 * zero, no drawdown, no trades and no Sharpe ratio - and if the loop, the valuation or the restore step were wrong,
 * none of those would hold. What a replay does with prices is fixed by the pure tests of the decision engine and by the
 * browser test; what is fixed here is that a run starts from the recorded opening state, ends, and can be repeated.
 * </p>
 *
 * <p>
 * The fixture is isolated and rolled back after each test; this class never starts the numbered resource suites.
 * </p>
 */
@GTIntegrationTestContext
@Transactional
class AlgoHistoricalReplayIntegrationTest {

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.EnumSource(value = grafioschtrader.types.SpecialInvestmentInstruments.class, names = {
      "CFD", "FOREX" })
  void importedMarginLotsCloseThroughRealPortfolioPath(grafioschtrader.types.SpecialInvestmentInstruments type)
      throws Exception {
    Security permitted = allocation();
    Security margin = em
        .createQuery("SELECT s FROM Security s WHERE s.assetClass.specialInvestmentInstrument = ?1"
            + " AND s.stockexchange IS NOT NULL ORDER BY s.idSecuritycurrency", Security.class)
        .setParameter(1, type.getValue()).setMaxResults(1).getSingleResult();
    int marginId = margin.getId();
    margin.setCurrency("CHF");
    margin.setLeverageFactor(1);
    margin.setActiveFromDate(opening.minusYears(1));
    margin.setActiveToDate(end.plusYears(1));
    em.createQuery("DELETE FROM Securitysplit s WHERE s.idSecuritycurrency = ?1").setParameter(1, marginId)
        .executeUpdate();
    clearDividendHistory(margin);
    priceEveryDay(marginId, 110);
    em.createQuery("UPDATE Historyquote h SET h.close = 100 WHERE h.idSecuritycurrency = ?1 AND h.date = ?2")
        .setParameter(1, marginId).setParameter(2, opening).executeUpdate();
    Securityaccount account = securityaccount();
    Cashaccount cash = em.find(Cashaccount.class, cashId);
    for (TransactionType direction : List.of(TransactionType.ACCUMULATE, TransactionType.REDUCE)) {
      double units = direction == TransactionType.ACCUMULATE ? 300 : 200;
      Transaction lot = new Transaction(account.getId(), cash, margin, 0d, units, 100d, direction, 0d, 0d, null,
          opening.atTime(16, 0), null, null, null, false);
      lot.setIdTenant(tenantId);
      lot.setAssetInvestmentValue2(2d);
      em.persist(lot);
    }
    em.flush();
    em.clear();
    var environment = copiedEnvironment();
    var result = run(environment);
    assertThat(result.getStatus()).as("%s", result.getFailureMessage()).isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    var closes = generatedTrades(result).stream().filter(t -> t.getSecurity().getId().equals(marginId)).toList();
    assertThat(closes).hasSize(2).allSatisfy(t -> {
      assertThat(t.getConnectedIdTransaction()).isNotNull();
      assertThat(t.getAssetInvestmentValue2()).isEqualTo(2d);
      assertThat(t.getTransactionDate()).isEqualTo(opening.plusDays(1));
    });
    assertThat(closes).extracting(Transaction::getCashaccountAmount).containsExactlyInAnyOrder(6000d, -4000d);
    assertThat(closes).extracting(Transaction::getTransactionType).containsExactlyInAnyOrder(TransactionType.REDUCE,
        TransactionType.ACCUMULATE);
    assertThat(generatedTrades(result).stream().filter(t -> t.getSecurity().getId().equals(permitted.getId())))
        .isNotEmpty().allSatisfy(t -> assertThat(t.getTransactionDate()).isAfter(opening.plusDays(1)));
    assertThat(em
        .createQuery(
            "SELECT h FROM HoldSecurityaccountSecurity h WHERE h.idTenant = ?1"
                + " AND h.hssk.idSecuritycurrency = ?2 AND h.toHoldDate IS NULL",
            grafioschtrader.entities.HoldSecurityaccountSecurity.class)
        .setParameter(1, environment.getId()).setParameter(2, marginId).getResultList()).isEmpty();
    assertThat(result.getTotalReturn()).isCloseTo(0.02, within(1e-8));
  }

  @Test
  void importedInverseFundIsFullySoldThenOnlyPermittedAllocationIsPurchased() throws Exception {
    Security permitted = allocation();
    Security inverse = em
        .createQuery("SELECT s FROM Security s WHERE s.currency = 'CHF' AND s.idSecuritycurrency <> ?1"
            + " AND s.idLinkSecuritycurrency IS NULL AND s.leverageFactor = 1 AND s.activeToDate >= ?2"
            + " AND s.stockexchange IS NOT NULL ORDER BY s.idSecuritycurrency", Security.class)
        .setParameter(1, permitted.getId()).setParameter(2, end).setMaxResults(50).getResultList().stream()
        .filter(s -> !s.isMarginInstrument()).findFirst().orElseThrow();
    inverse.setLeverageFactor(-1);
    inverse.setActiveFromDate(opening.minusYears(1));
    clearDividendHistory(inverse);
    priceEveryDay(inverse);
    int inverseId = inverse.getId();
    var originalMember = em.createQuery("SELECT m FROM AlgoSecurity m WHERE m.idTenant = ?1", AlgoSecurity.class)
        .setParameter(1, tenantId).getSingleResult();
    originalMember.setPercentage(50f);
    var inverseMember = new AlgoSecurity();
    inverseMember.setIdTenant(tenantId);
    inverseMember.setIdAlgoSecurityParent(originalMember.getIdAlgoSecurityParent());
    inverseMember.setSecurity(inverse);
    inverseMember.setPercentage(50f);
    em.persist(inverseMember);
    holdInMainTenant(inverse, 2.75);
    Tenant environment = copiedEnvironment();
    AlgoSimulationResult result = run(environment);
    assertThat(result.getStatus()).as("%s", result.getFailureMessage()).isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    var generated = generatedTrades(result);
    var sale = generated.stream().filter(t -> t.getSecurity().getId().equals(inverseId)).toList();
    assertThat(sale).singleElement().satisfies(t -> {
      assertThat(t.getTransactionType()).isEqualTo(TransactionType.REDUCE);
      assertThat(t.getUnits()).isEqualTo(2.75);
      assertThat(t.getCashaccountAmount()).isCloseTo(275, within(0.01));
      assertThat(t.getTransactionDate()).isEqualTo(opening.plusDays(1));
    });
    assertThat(generated.stream().filter(t -> t.getTransactionType() == TransactionType.ACCUMULATE)).allSatisfy(t -> {
      assertThat(t.getSecurity().getId()).isEqualTo(permitted.getId());
      assertThat(t.getTransactionDate()).isAfter(sale.getFirst().getTransactionDate());
    });
    assertThat(trailOf(result)).contains(AlgoEventType.OPENING_EXCLUDED_CLOSE, AlgoEventType.ALLOCATION_FILL);
    assertThat(netUnits(source.transactions(environment.getId(), end.plusDays(1)), em.find(Security.class, inverseId)))
        .isZero();
    assertThat(em.createQuery("SELECT m.percentage FROM AlgoSecurity m WHERE m.idTenant = ?1", Float.class)
        .setParameter(1, tenantId).getResultList()).containsExactlyInAnyOrder(50f, 50f);
    var first = generated.stream().map(t -> List.of(t.getSecurity().getId(), t.getTransactionType(), t.getUnits(),
        t.getQuotation(), t.getCashaccountAmount(), t.getTransactionDate())).toList();
    var rerun = run(environment);
    assertThat(rerun.getStatus()).as("%s", rerun.getFailureMessage()).isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    assertThat(generatedTrades(rerun).stream().map(t -> List.of(t.getSecurity().getId(), t.getTransactionType(),
        t.getUnits(), t.getQuotation(), t.getCashaccountAmount(), t.getTransactionDate())).toList()).isEqualTo(first);
  }

  @Test
  void generatedCouponsWithoutTaxesBookAccruedPurchaseAndRedeemAtPar() throws Exception {
    Security security = asDirectBond(allocation());
    security.setDenomination(5_000);
    setBondTerms(security, 12d, grafioschtrader.types.CouponDayCount.ACT_ACT_ICMA);
    security.setActiveFromDate(opening.minusDays(1));
    security.setActiveToDate(end);
    em.createQuery("DELETE FROM Securitysplit s WHERE s.idSecuritycurrency = ?1").setParameter(1, security.getId())
        .executeUpdate();
    priceEveryDay(security.getId(), 95);
    em.flush();
    Tenant environment = environment();
    var request = new grafioschtrader.algo.SimulationRunRequestDTO();
    request.setEndDate(end);
    request.setGenerateBondCoupons(true);
    var prepared = replay.prepare(environment.getId(), request, user);
    replay.execute(prepared.getIdTenant(), prepared.getIdSimulationResult(), user);
    login();
    var result = results.findById(prepared.getIdSimulationResult()).orElseThrow();
    assertThat(result.getStatus()).as("failure: %s", result.getFailureMessage())
        .isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    var ledger = source.transactions(environment.getId(), end.plusDays(1));
    var buy = ledger.stream().filter(t -> t.getTransactionType() == TransactionType.ACCUMULATE).findFirst()
        .orElseThrow();
    assertThat(buy.getAssetInvestmentValue1()).isGreaterThan(0.0);
    assertThat(buy.getQuotation()).isCloseTo(95, within(0.01));
    assertThat(buy.getUnits() % 50).isCloseTo(0, within(1e-8));
    assertThat(ledger.stream().filter(t -> t.getTransactionType() == TransactionType.DIVIDEND).toList()).singleElement()
        .satisfies(payment -> {
          assertThat(payment.getCashaccountAmount()).isCloseTo(buy.getUnits(), within(0.011));
          assertThat(payment.getTaxCost()).isZero();
        });
    var redeem = ledger.stream().filter(t -> t.getTransactionType() == TransactionType.REDUCE).findFirst()
        .orElseThrow();
    assertThat(redeem.getQuotation()).isCloseTo(100d, within(0.01));
    assertThat(redeem.getUnits()).isCloseTo(buy.getUnits(), within(1e-8));
    assertThat(redeem.getTransactionCost()).isNull();
    assertThat(redeem.getTaxCost()).isZero();
    assertThat(redeem.getAssetInvestmentValue1()).isNull();
    assertThat(redeem.getCashaccountAmount()).isCloseTo(buy.getUnits() * 100, within(0.01));
    assertThat(redeem.getTransactionDate()).isEqualTo(end);
    assertThat(netUnits(ledger, security)).isZero();
    assertThat(result.getPaidDividends()).isZero();
    assertThat(result.getTaxIncomeSummaryJson()).contains("SECURITY_INTEREST")
        .doesNotContain("COUPON_PRINCIPAL_NOT_MODELED");
    assertThat(result.getConventions()).contains("REPLAY_BOND_REDEEM_AT_PAR");
    assertThat(trailOf(result)).contains(AlgoEventType.MATURITY_REDEMPTION);
    assertThat(em.createQuery("SELECT COUNT(d) FROM Dividend d WHERE d.idSecuritycurrency = ?1", Long.class)
        .setParameter(1, security.getId()).getSingleResult()).isZero();
  }

  @Test
  void maturityAnchorsTheFinalCouponAndRedemptionDoesNotInventAccruedInterest() throws Exception {
    Security security = asDirectBond(allocation());
    setBondTerms(security, 12d, grafioschtrader.types.CouponDayCount.ACT_ACT_ICMA);
    security.setActiveFromDate(opening.minusDays(1));
    security.setActiveToDate(LocalDate.of(2020, 6, 20));
    em.flush();
    Tenant environment = environment();
    var request = new grafioschtrader.algo.SimulationRunRequestDTO();
    request.setEndDate(end);
    request.setGenerateBondCoupons(true);
    var prepared = replay.prepare(environment.getId(), request, user);

    replay.execute(prepared.getIdTenant(), prepared.getIdSimulationResult(), user);
    login();

    var result = results.findById(prepared.getIdSimulationResult()).orElseThrow();
    var ledger = source.transactions(environment.getId(), end.plusDays(1));
    assertThat(result.getStatus()).as("failure: %s", result.getFailureMessage())
        .isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    assertThat(ledger.stream().filter(t -> t.getTransactionType() == TransactionType.DIVIDEND).toList()).singleElement()
        .satisfies(payment -> assertThat(payment.getTransactionDate()).isEqualTo(LocalDate.of(2020, 6, 22)));
    var redeem = ledger.stream().filter(t -> t.getTransactionType() == TransactionType.REDUCE).findFirst()
        .orElseThrow();
    assertThat(redeem.getTransactionDate()).isEqualTo(LocalDate.of(2020, 6, 22));
    assertThat(redeem.getAssetInvestmentValue1()).isNull();
    assertThat(redeem.getCashaccountAmount()).isCloseTo(redeem.getUnits() * redeem.getQuotation(), within(0.01));
    assertThat(netUnits(ledger, security)).isZero();
    assertThat(eventsOf(result).stream().filter(e -> e.getEventType() == AlgoEventType.MATURITY_REDEMPTION)
        .map(AlgoEventLog::getRationale)).contains("REPLAY_REDEMPTION_CATCH_UP");
  }

  @Test
  void maturedBondCopiedIntoTheEnvironmentIsRedeemedOnTheFirstRunDate() throws Exception {
    Security security = asDirectBond(allocation());
    security.setActiveFromDate(opening.minusYears(1));
    security.setActiveToDate(opening);
    em.flush();
    holdInMainTenant(security, 10);
    Tenant environment = copiedEnvironment();
    var result = run(environment);
    assertThat(result.getStatus()).as("failure: %s", result.getFailureMessage())
        .isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    var redeem = source.transactions(environment.getId(), end.plusDays(1)).stream()
        .filter(t -> t.getTransactionType() == TransactionType.REDUCE && !t.isSimulationOpening()).findFirst()
        .orElseThrow();
    assertThat(redeem.getQuotation()).isEqualTo(100d);
    assertThat(redeem.getUnits()).isEqualTo(10);
    assertThat(trailOf(result)).contains(AlgoEventType.MATURITY_REDEMPTION);
    assertThat(eventsOf(result).stream().filter(e -> e.getEventType() == AlgoEventType.MATURITY_REDEMPTION)
        .map(AlgoEventLog::getRationale)).contains("REPLAY_REDEMPTION_CATCH_UP");
    assertThat(generatedTrades(result)).noneMatch(t -> t.getTransactionType() == TransactionType.ACCUMULATE);
  }

  @Test
  void maturityRedemptionFailsTheRunWhenTheTransactionLimitIsReached() throws Exception {
    Security security = asDirectBond(allocation());
    security.setActiveFromDate(opening.minusYears(1));
    security.setActiveToDate(opening);
    em.flush();
    holdInMainTenant(security, 10);
    Tenant environment = copiedEnvironment();
    int openingTransactions = source.transactions(environment.getId(), end.plusDays(1)).size();
    EntityLimit transactionLimit = entityLimits
        .findByLimitTypeAndEntityName(LimitKeyConfig.KEY_TRANSACTION.limitType().getValue(),
            LimitKeyConfig.KEY_TRANSACTION.entityName())
        .stream().filter(limit -> LimitKeyConfig.KEY_TRANSACTION.equals(limit.getLimitKey()))
        .filter(limit -> limit.getIdRole() == null && limit.getIdUser() == null).findFirst().orElseThrow();
    transactionLimit.setLimitValue(openingTransactions);
    em.flush();
    limitService.evictUser(user.getIdUser());

    var result = run(environment);

    assertThat(result.getStatus()).isEqualTo(AlgoSimulationRunStatus.RUN_FAILED);
    assertThat(result.getFailureMessage()).isEqualTo("REPLAY_FILL_REJECTED");
    assertThat(source.transactions(environment.getId(), end.plusDays(1))).hasSize(openingTransactions)
        .noneMatch(t -> t.getTransactionType() == TransactionType.REDUCE && !t.isSimulationOpening());
  }

  @Test
  void notYetActiveInstrumentDoesNotFailTheInitialPurchase() throws Exception {
    Security security = allocation();
    LocalDate activeFrom = opening.plusDays(2);
    security.setActiveFromDate(activeFrom);
    security.setActiveToDate(end.plusYears(1));
    setRebalancingFrequency(53);
    em.flush();
    var result = run(environment());
    assertThat(result.getStatus()).as("failure: %s", result.getFailureMessage())
        .isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    assertThat(eventsOf(result)).noneMatch(e -> "REPLAY_PURCHASE_INCOMPLETE".equals(e.getRationale()));
    assertThat(generatedTrades(result)).filteredOn(t -> t.getTransactionType() == TransactionType.ACCUMULATE)
        .isNotEmpty().allSatisfy(t -> assertThat(t.getTransactionDate()).isAfterOrEqualTo(activeFrom));
  }

  @Test
  void leftoverStockIsClosedAtLastPriceOnActiveToDate() throws Exception {
    Security security = allocation();
    LocalDate last = opening.plusDays(2);
    security.setActiveToDate(last);
    securityaccount().setFeeModelYaml("""
        rules:
          - name: "Flat"
            condition: "true"
            expression: "7.0"
        """);
    em.flush();
    var result = run(environment());
    assertThat(result.getStatus()).as("failure: %s", result.getFailureMessage())
        .isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    List<Transaction> trades = generatedTrades(result);
    var buy = trades.stream().filter(t -> t.getTransactionType() == TransactionType.ACCUMULATE).findFirst()
        .orElseThrow();
    var close = trades.stream().filter(t -> t.getTransactionType() == TransactionType.REDUCE).findFirst().orElseThrow();
    assertThat(buy.getTransactionDate()).isEqualTo(last);
    assertThat(close.getQuotation()).isEqualTo(PRICE);
    assertThat(close.getTransactionDate()).isEqualTo(last);
    assertThat(close.getTransactionCost()).isEqualTo(7.0);
    assertThat(netUnits(trades, security)).isZero();
    assertThat(trailOf(result)).contains(AlgoEventType.TERMINAL_CLOSE);
  }

  @Test
  void enabledTaxesUseCapturedModelsAndStoredIncomeWhileQueued() throws Exception {
    Security security = allocation();
    var dividend = new grafioschtrader.entities.Dividend(security.getId(), opening.plusDays(3), opening.plusDays(6),
        2.0, 2.0, "CHF", grafioschtrader.types.CreateType.ADD_MODIFIED_USER);
    dividend.setCreateModifyTime(opening.atStartOfDay());
    em.persist(dividend);
    var countries = em.createQuery("SELECT c FROM TaxCountry c", grafioschtrader.entities.TaxCountry.class)
        .getResultList();
    countries.forEach(country -> country.setTaxModelYaml(null));
    var country = countries.getFirst();
    country.setTaxModelYaml("""
        version: 1
        transactionTaxes:
          rules:
            - name: synthetic trade
              expression: cleanValue * 0.01
        incomeWithholding:
          rules:
            - name: synthetic withholding
              expression: grossIncome * 0.25
        """);
    em.flush();
    Tenant environment = environment();
    var request = new grafioschtrader.algo.SimulationRunRequestDTO();
    request.setEndDate(end);
    request.setApplyTaxModels(true);
    var prepared = replay.prepare(environment.getId(), request, user);
    String captured = prepared.getInputAssumptionsJson();
    em.find(grafioschtrader.entities.TaxCountry.class, country.getIdTaxCountry())
        .setTaxModelYaml("invalid model after submission");
    em.find(grafioschtrader.entities.Dividend.class, dividend.getId()).setAmount(100.0);
    em.flush();
    replay.execute(prepared.getIdTenant(), prepared.getIdSimulationResult(), user);
    login();
    var result = results.findById(prepared.getIdSimulationResult()).orElseThrow();
    assertThat(result.getStatus()).as("failure: %s", result.getFailureMessage())
        .isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    assertThat(result.getInputAssumptionsJson()).isEqualTo(captured);
    assertThat(result.getConventions()).contains("REPLAY_NET_INCOME").doesNotContain("REPLAY_GROSS_DIVIDENDS");
    var ledger = source.transactions(environment.getId(), end.plusDays(1));
    var buy = ledger.stream().filter(t -> t.getTransactionType() == TransactionType.ACCUMULATE).findFirst()
        .orElseThrow();
    assertThat(buy.getTaxCost()).isGreaterThan(0.0);
    var payment = ledger.stream().filter(t -> t.getTransactionType() == TransactionType.DIVIDEND).findFirst()
        .orElseThrow();
    assertThat(payment.getCashaccountAmount()).isCloseTo(buy.getUnits() * 1.5, within(0.011));
    assertThat(payment.getTaxCost()).isCloseTo(buy.getUnits() * 0.5, within(0.011));
    assertThat(result.getTaxIncomeSummaryJson()).contains("withholdingPaid", "netPaid");
    var income = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getTaxIncomeSummaryJson())
        .get("income");
    income.forEach(row -> {
      for (String field : List.of("grossPaid", "withholdingPaid", "netPaid", "grossReceivables", "estimatedWithholding",
          "netReceivables", "reconciliation")) {
        double value = row.get(field).asDouble();
        assertThat(value).isEqualTo(DataHelper.round(value, BaseConstants.FID_STANDARD_FRACTION_DIGITS));
      }
      assertThat(row.get("estimatedWithholding").asDouble()).isCloseTo(
          row.get("grossReceivables").asDouble() - row.get("netReceivables").asDouble(), within(0.000000001));
      assertThat(row.get("reconciliation").asDouble()).isCloseTo(
          row.get("netPaid").asDouble() - (row.get("grossPaid").asDouble() - row.get("withholdingPaid").asDouble()),
          within(0.000000001));
    });
  }

  @Test
  void enabledTaxesWithoutModelsCompleteWithOnePersistentRunWarning() throws Exception {
    em.createQuery("SELECT c FROM TaxCountry c", grafioschtrader.entities.TaxCountry.class).getResultList()
        .forEach(country -> country.setTaxModelYaml(null));
    em.flush();
    Tenant environment = environment();
    var request = new grafioschtrader.algo.SimulationRunRequestDTO();
    request.setEndDate(end);
    request.setApplyTaxModels(true);
    var prepared = replay.prepare(environment.getId(), request, user);
    replay.execute(prepared.getIdTenant(), prepared.getIdSimulationResult(), user);
    login();
    var result = results.findById(prepared.getIdSimulationResult()).orElseThrow();
    assertThat(result.getStatus()).isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    var summary = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getTaxIncomeSummaryJson());
    assertThat(summary.get("warnings")).hasSize(1);
    assertThat(summary.get("warnings").get(0).get("code").asText()).isEqualTo("TAX_NO_MODELS");
    assertThat(summary.get("warnings").get(0).get("count").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName("Dividends settle on non-trading payment dates and repeat runs replace the payments")
  void dividendPaymentsAreCashAndRepeatable() throws Exception {
    Security security = allocation();
    var dividend = new grafioschtrader.entities.Dividend(security.getId(), opening.plusDays(3),
        LocalDate.of(2020, 6, 21), 2.0, 2.0, "CHF", grafioschtrader.types.CreateType.ADD_MODIFIED_USER);
    dividend.setCreateModifyTime(opening.atStartOfDay());
    em.persist(dividend);
    em.flush();
    Tenant environment = environment();
    for (int iteration = 0; iteration < 2; iteration++) {
      AlgoSimulationResult result = run(environment);
      assertThat(result.getStatus()).as("failure: %s", result.getFailureMessage())
          .isEqualTo(AlgoSimulationRunStatus.COMPLETED);
      List<Transaction> ledger = source.transactions(environment.getId(), end.plusDays(1));
      Transaction buy = ledger.stream().filter(t -> t.getTransactionType() == TransactionType.ACCUMULATE).findFirst()
          .orElseThrow();
      List<Transaction> payments = ledger.stream().filter(t -> t.getTransactionType() == TransactionType.DIVIDEND)
          .toList();
      assertThat(payments).singleElement().satisfies(payment -> {
        assertThat(payment.getTransactionDate()).isEqualTo(LocalDate.of(2020, 6, 21));
        assertThat(payment.getCashaccountAmount()).isEqualTo(buy.getUnits() * 2);
        assertThat(payment.isSimulationOpening()).isFalse();
        assertThat(payment.getExDate()).isEqualTo(opening.plusDays(3));
      });
      assertThat(result.getPaidDividends()).isEqualTo(buy.getUnits() * 2);
      assertThat(result.getDividendReceivables()).isZero();
      assertThat(result.getTotalReturn()).isCloseTo(0.02, within(1e-8));
      assertThat(trailOf(result)).containsOnlyOnce(AlgoEventType.DIVIDEND_ENTITLEMENT, AlgoEventType.DIVIDEND_PAYMENT);
    }
  }

  @Test
  @DisplayName("The captured fallback leaves unpaid income in equity without creating spendable cash")
  void missingPaymentDateCreatesTerminalReceivable() throws Exception {
    Security security = allocation();
    var dividend = new grafioschtrader.entities.Dividend(security.getId(), opening.plusDays(3), null, 2.0, 2.0, "CHF",
        grafioschtrader.types.CreateType.ADD_MODIFIED_USER);
    dividend.setCreateModifyTime(opening.atStartOfDay());
    em.persist(dividend);
    em.flush();
    Tenant environment = environment();
    var prepared = replay.prepare(environment.getId(), end, user);
    prepared.setDividendPaymentDelayDays(16);
    replay.execute(prepared.getIdTenant(), prepared.getIdSimulationResult(), user);
    login();
    AlgoSimulationResult result = results.findById(prepared.getIdSimulationResult()).orElseThrow();
    assertThat(result.getStatus()).as("failure: %s", result.getFailureMessage())
        .isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    List<Transaction> ledger = source.transactions(environment.getId(), end.plusDays(1));
    Transaction buy = ledger.stream().filter(t -> t.getTransactionType() == TransactionType.ACCUMULATE).findFirst()
        .orElseThrow();
    assertThat(ledger).noneMatch(t -> t.getTransactionType() == TransactionType.DIVIDEND);
    assertThat(result.getPaidDividends()).isZero();
    assertThat(result.getDividendReceivables()).isEqualTo(buy.getUnits() * 2);
    assertThat(result.getTotalReturn()).isCloseTo(0.02, within(1e-8));
  }

  @Test
  @DisplayName("Foreign dividend payments use historical settlement and reporting exchange rates")
  void foreignDividendPayment() throws Exception {
    Security security = foreignAllocation();
    var dividend = new grafioschtrader.entities.Dividend(security.getId(), opening.plusDays(3), opening.plusDays(7),
        2.0, 2.0, "USD", grafioschtrader.types.CreateType.ADD_MODIFIED_USER);
    dividend.setCreateModifyTime(opening.atStartOfDay());
    em.persist(dividend);
    em.flush();
    AlgoSimulationResult result = run(environment());
    assertThat(result.getStatus()).as("failure: %s", result.getFailureMessage())
        .isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    List<Transaction> ledger = source.transactions(result.getIdTenant(), end.plusDays(1));
    Transaction buy = ledger.stream().filter(t -> t.getTransactionType() == TransactionType.ACCUMULATE).findFirst()
        .orElseThrow();
    Transaction payment = ledger.stream().filter(t -> t.getTransactionType() == TransactionType.DIVIDEND).findFirst()
        .orElseThrow();
    assertThat(payment.getCashaccount().getCurrency()).isEqualTo("CHF");
    assertThat(payment.getCurrencyExRate()).isCloseTo(USD_CHF, within(1e-8));
    assertThat(result.getPaidDividends()).isCloseTo(buy.getUnits() * 2 * USD_CHF, within(0.01));
    assertThat(result.getDividendReceivables()).isZero();
  }

  @PersistenceContext
  private EntityManager em;
  @Autowired
  private SimulationTenantService simulations;
  @Autowired
  private AlgoHistoricalReplayService replay;
  @Autowired
  private AlgoSimulationResultJpaRepository results;
  @Autowired
  private AlgoEventLogJpaRepository events;
  @Autowired
  private SimulationSourceRepository source;
  @Autowired
  private HoldCashaccountBalanceJpaRepository balances;
  @Autowired
  private EntityLimitJpaRepository entityLimits;
  @Autowired
  private EntityLimitService limitService;
  @Autowired
  private StandingOrderJpaRepository standingOrders;

  /** One closing price for the whole window, so a checkpoint can only be reached by the interval, not by a move. */
  private static final double PRICE = 100.0;

  /** One exchange rate for the whole window, for the same reason and so that an amount can be asserted exactly. */
  private static final double USD_CHF = 0.9;

  private final LocalDate opening = LocalDate.of(2020, 6, 15);
  private final LocalDate end = LocalDate.of(2020, 6, 30);
  private Integer tenantId;
  private Integer topId;
  private Integer cashId;
  private Integer usdCashId;
  private User user;

  @BeforeEach
  void fixture() {
    Tenant tenant = new Tenant("Replay integration", "CHF", 0, TenantKindType.MAIN, false);
    em.persist(tenant);
    tenantId = tenant.getId();
    user = new User(tenantId);
    user.setIdUser(0);
    login();
    Portfolio portfolio = new Portfolio(tenantId, "Replay portfolio", "CHF");
    em.persist(portfolio);
    Cashaccount cash = new Cashaccount("Replay CHF", 0.0, "CHF", portfolio);
    cash.setIdTenant(tenantId);
    em.persist(cash);
    cashId = cash.getId();
    // A second account, in another currency and left empty unless a test funds it. Neither may be overdrawn - the
    // constructor takes an opening balance, not a borrowing rate - which is what makes the funding rules observable.
    Cashaccount usdCash = new Cashaccount("Replay USD", 0.0, "USD", portfolio);
    usdCash.setIdTenant(tenantId);
    em.persist(usdCash);
    usdCashId = usdCash.getId();
    Transaction deposit = new Transaction(cash, 100000.0, TransactionType.DEPOSIT, opening.atTime(23, 59));
    deposit.setIdTenant(tenantId);
    em.persist(deposit);
    Watchlist watchlist = new Watchlist(tenantId, "Replay universe");
    watchlist.setSecuritycurrencyList(new ArrayList<>());
    em.persist(watchlist);
    AlgoTop top = new AlgoTop();
    top.setIdTenant(tenantId);
    top.setName("Replay shared");
    top.setPercentage(100f);
    top.setIdWatchlist(watchlist.getId());
    em.persist(top);
    topId = top.getId();
    em.flush();
    em.clear();
    seedTradingDays();
    balances.createCashaccountBalanceEntireByTenant(tenantId);
  }

  /**
   * The application wide trading calendar is written by a scheduled task rather than seeded with the test data, so
   * without this the date loop of a run visits no day and every assertion about it would hold vacuously.
   */
  private void seedTradingDays() {
    for (LocalDate date = opening; !date.isAfter(end); date = date.plusDays(1)) {
      if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY
          || em.find(TradingDaysPlus.class, date) != null) {
        continue;
      }
      em.persist(new TradingDaysPlus(date));
    }
    em.flush();
  }

  @AfterEach
  void clearAuthentication() {
    if (user != null) {
      limitService.evictUser(user.getIdUser());
    }
    SecurityContextHolder.clearContext();
  }

  /** The worker clears the security context when it finishes, so the assertions afterwards restore it. */
  private void login() {
    var authentication = new UsernamePasswordAuthenticationToken("replay", "", List.of());
    authentication.setDetails(user);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private Tenant environment() throws Exception {
    return environment(Map.of(cashId, 100000.0));
  }

  private Tenant environment(Map<Integer, Double> cashBalances) throws Exception {
    SimulationTenantCreateDTO dto = new SimulationTenantCreateDTO();
    dto.setIdAlgoTop(topId);
    dto.setTenantName("Replay environment");
    dto.setInitializationMode(SimulationInitializationMode.MANUAL_CASH);
    dto.setSimulationStartDate(opening);
    dto.setCashBalances(cashBalances);
    return simulations.createSimulationTenant(dto);
  }

  private AlgoSimulationResult run(Tenant environment) {
    var prepared = replay.prepare(environment.getId(), end, user);
    replay.execute(prepared.getIdTenant(), prepared.getIdSimulationResult(), user);
    login();
    return results.findById(prepared.getIdSimulationResult()).orElseThrow();
  }

  /**
   * A rejected request carries its field and its message key in the violation list rather than in the exception
   * message, which stays null; asserting on the message would pass whatever the service actually refused.
   */
  private void assertViolation(ThrowableAssert.ThrowingCallable request, String field, String messageKey) {
    DataViolationException thrown = catchThrowableOfType(DataViolationException.class, request);
    assertThat(thrown).isNotNull();
    assertThat(thrown.getDataViolation()).singleElement().satisfies(violation -> {
      assertThat(violation.getField()).isEqualTo(field);
      assertThat(violation.getMessageKey()).isEqualTo(messageKey);
    });
  }

  @Test
  @DisplayName("A cash-only environment replays to completion without moving, trading or losing its opening ledger")
  void completeRunOfAnUnmovedEnvironment() throws Exception {
    Tenant environment = environment();

    AlgoSimulationResult run = run(environment);

    assertThat(run.getStatus()).as("failure was: %s", run.getFailureMessage())
        .isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    assertThat(run.getFailureMessage()).isNull();
    assertThat(run.getFinishedAt()).isNotNull();
    assertThat(run.getOpeningDate()).isEqualTo(opening);
    assertThat(run.getEndDate()).isEqualTo(end);
    assertThat(run.getTradingDaysDone()).isEqualTo(run.getTradingDaysTotal());
    assertThat(run.getConventions()).startsWith(AlgoHistoricalReplayService.CONVENTIONS)
        .endsWith(AlgoHistoricalReplayService.NO_TRANSACTION_COST);
    assertThat(run.getTotalTrades()).isZero();
    assertThat(run.getWinningTrades()).isZero();
    assertThat(run.getLosingTrades()).isZero();
    assertThat(run.getMaxDrawdown()).as("cash alone cannot fall below its own peak").isEqualTo(0.0);
    assertThat(run.getSharpeRatio()).as("an equity that never moved has no deviation to divide by").isNull();

    var ledger = source.transactions(environment.getId(), end.plusDays(1));
    assertThat(ledger).as("only the opening deposit, and it stays marked as opening").hasSize(1)
        .allMatch(Transaction::isSimulationOpening);
    assertThat(source.transactions(tenantId, end.plusDays(1))).as("the main tenant is untouched").hasSize(1);

    var trail = events.findByIdSimulationResultOrderByEventDateDescIdAlgoEventDesc(run.getIdSimulationResult(),
        PageRequest.of(0, 200)).getContent();
    assertThat(trail).extracting(entry -> entry.getEventType()).contains(AlgoEventType.RUN_START,
        AlgoEventType.RUN_END);
    assertThat(trail).allMatch(entry -> environment.getId().equals(entry.getIdTenant()));
  }

  @Test
  @DisplayName("A repeat run replaces the previous one instead of appending a second replay to it")
  void repeatRunStartsFromTheRecordedOpeningState() throws Exception {
    Tenant environment = environment();

    AlgoSimulationResult first = run(environment);
    AlgoSimulationResult second = run(environment);

    assertThat(second.getIdSimulationResult()).as("one run record per environment")
        .isEqualTo(first.getIdSimulationResult());
    assertThat(results.findByIdTenant(environment.getId())).isPresent();
    assertThat(second.getStatus()).isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    assertThat(source.transactions(environment.getId(), end.plusDays(1))).as("no second copy of the opening ledger")
        .hasSize(1);
    assertThat(events.countByIdTenant(environment.getId()))
        .as("the trail of the previous run is removed rather than continued")
        .isEqualTo(events.findByIdSimulationResultOrderByEventDateDescIdAlgoEventDesc(second.getIdSimulationResult(),
            PageRequest.of(0, 500)).getTotalElements());
  }

  @Test
  @DisplayName("Simulation cash standing orders are replayed, cash-flow neutral, repeatable and hidden from the live scheduler")
  void replayCashStandingOrders() throws Exception {
    Tenant environment = environment(Map.of(cashId, 1_000.0));
    Cashaccount simulationCash = source.cashaccounts(environment.getId()).getFirst();
    StandingOrderCashaccount order = new StandingOrderCashaccount();
    order.setIdTenant(environment.getId());
    order.setCashaccount(simulationCash);
    order.setCashaccountAmount(100.0);
    order.setTransactionType(TransactionType.DEPOSIT);
    order.setRepeatUnit(RepeatUnit.DAYS);
    order.setRepeatInterval((short) 5);
    order.setPeriodDayPosition(PeriodDayPosition.SPECIFIC_DAY);
    order.setWeekendAdjust(WeekendAdjustType.AFTER);
    order.setQuoteToleranceDays((byte) -3);
    order.setValidFrom(opening.plusDays(1));
    order.setValidTo(opening.plusDays(11));
    order.setNextExecutionDate(order.getValidFrom());
    order.setNote("Replay savings plan");
    em.persist(order);
    em.flush();

    assertThat(standingOrders.findDueForLiveTenants(end))
        .noneMatch(candidate -> candidate.getIdTenant().equals(environment.getId()));

    AlgoSimulationResult first = run(environment);
    List<Transaction> firstLedger = source.transactions(environment.getId(), end.plusDays(1));

    assertThat(first.getStatus()).as("failure: %s", first.getFailureMessage())
        .isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    assertThat(first.getTotalReturn()).as("deposits are external capital flows, not investment returns").isCloseTo(0.0,
        within(1e-8));
    assertThat(firstLedger).filteredOn(transaction -> order.getId().equals(transaction.getIdStandingOrder())).hasSize(3)
        .allSatisfy(transaction -> {
          assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.DEPOSIT);
          assertThat(transaction.getCashaccountAmount()).isEqualTo(100.0);
          assertThat(transaction.isSimulationOpening()).isFalse();
        });
    assertThat(firstLedger.stream().filter(transaction -> order.getId().equals(transaction.getIdStandingOrder()))
        .map(Transaction::getTransactionDate).toList()).containsExactly(opening.plusDays(1), opening.plusDays(7),
            opening.plusDays(11));
    assertThat(events.findByIdSimulationResultOrderByEventDateDescIdAlgoEventDesc(first.getIdSimulationResult(),
        PageRequest.of(0, 200)).getContent())
            .filteredOn(event -> event.getEventType() == AlgoEventType.CASH_STANDING_ORDER).hasSize(3);

    AlgoSimulationResult second = run(environment);
    assertThat(second.getIdSimulationResult()).isEqualTo(first.getIdSimulationResult());
    assertThat(source.transactions(environment.getId(), end.plusDays(1)))
        .filteredOn(transaction -> order.getId().equals(transaction.getIdStandingOrder())).hasSize(3);
  }

  @Test
  @DisplayName("A cash-only environment buys its instruments once and does not rebalance inside the interval")
  void cashOnlyEnvironmentPurchasesBeforeItRebalances() throws Exception {
    Security security = allocation();

    AlgoSimulationResult run = run(environment());

    assertThat(run.getStatus()).as("failure was: %s", run.getFailureMessage())
        .isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    List<AlgoEventType> trail = trailOf(run);
    assertThat(trail).as("the buy-in is a purchase of its own, raised exactly once")
        .containsOnlyOnce(AlgoEventType.ALLOCATION_PLAN).contains(AlgoEventType.ALLOCATION_FILL);
    assertThat(trail).as("four redeployments per year reach no checkpoint within a fortnight")
        .doesNotContain(AlgoEventType.REBALANCE_PLAN, AlgoEventType.REBALANCE_FILL);
    assertThat(generatedTrades(run)).as("the instrument of the allocation was actually bought").anySatisfy(trade -> {
      assertThat(trade.getTransactionType()).isEqualTo(TransactionType.ACCUMULATE);
      assertThat(trade.getSecurity().getId()).isEqualTo(security.getId());
    });
  }

  @Test
  @DisplayName("An environment that opens holding securities is rebalanced instead of bought in")
  void investedEnvironmentRebalancesWithoutAPurchase() throws Exception {
    Security security = allocation();
    holdInMainTenant(security, 400);

    AlgoSimulationResult run = run(copiedEnvironment());

    assertThat(run.getStatus()).as("failure was: %s", run.getFailureMessage())
        .isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    List<AlgoEventType> trail = trailOf(run);
    assertThat(trail).as("it already holds positions, so there is nothing to buy in")
        .doesNotContain(AlgoEventType.ALLOCATION_PLAN, AlgoEventType.ALLOCATION_FILL);
    assertThat(trail).as("the first checkpoint is due at once and is the only one of the run")
        .containsOnlyOnce(AlgoEventType.REBALANCE_PLAN);
  }

  @Test
  @DisplayName("An order no single account can pay is funded out of the other cash account of the environment")
  void aShortfallIsFundedFromTheOtherCashAccountOfTheEnvironment() throws Exception {
    Security security = foreignAllocation();

    AlgoSimulationResult run = run(environment(Map.of(cashId, 100000.0, usdCashId, 100000.0)));

    assertThat(run.getStatus()).as("failure was: %s", run.getFailureMessage())
        .isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    assertThat(generatedTrades(run)).as("neither account holds the whole order, yet it was bought")
        .anySatisfy(trade -> {
          assertThat(trade.getTransactionType()).isEqualTo(TransactionType.ACCUMULATE);
          assertThat(trade.getSecurity().getId()).isEqualTo(security.getId());
        });
    assertThat(eventsOf(run)).as("the money that was moved is on the record, against the instrument it paid for")
        .anySatisfy(entry -> {
          assertThat(entry.getEventType()).isEqualTo(AlgoEventType.FUNDING_TRANSFER);
          assertThat(entry.getIdSecuritycurrency()).isEqualTo(security.getId());
          assertThat(entry.getCurrency()).isEqualTo("CHF");
          assertThat(entry.getAmount()).isPositive();
          assertThat(entry.getRationale()).isEqualTo("REPLAY_FUNDING_TRANSFER");
          assertThat(entry.getDetails()).contains("Replay USD");
        });
    assertThat(eventsOf(run)).filteredOn(entry -> entry.getUnits() != null)
        .allSatisfy(entry -> assertThat(entry.getUnits()).isEqualTo(Math.floor(entry.getUnits())));
    assertThat(eventsOf(run)).filteredOn(entry -> entry.getPrice() != null)
        .allSatisfy(entry -> assertThat(entry.getPrice())
            .isEqualTo(DataHelper.round(entry.getPrice(), BaseConstants.FID_MAX_FRACTION_DIGITS)));
    assertThat(eventsOf(run)).filteredOn(entry -> entry.getAmount() != null && "CHF".equals(entry.getCurrency()))
        .allSatisfy(entry -> assertThat(entry.getAmount())
            .isEqualTo(DataHelper.round(entry.getAmount(), BaseConstants.FID_STANDARD_FRACTION_DIGITS)));

    List<Transaction> movements = generatedCashMovements(run);
    Transaction withdrawal = oneOfType(movements, TransactionType.WITHDRAWAL);
    Transaction deposit = oneOfType(movements, TransactionType.DEPOSIT);
    assertThat(withdrawal.getCashaccount().getId()).as("the money leaves the account that cannot pay in its own right")
        .isEqualTo(source.cashaccounts(run.getIdTenant()).stream().filter(a -> a.getCurrency().equals("USD"))
            .findFirst().orElseThrow().getId());
    assertThat(deposit.getCashaccount().getId())
        .as("and arrives on the account of the environment currency, which is"
            + " the only direction the exchange rate of a tenant exists in")
        .isEqualTo(source.cashaccounts(run.getIdTenant()).stream().filter(a -> a.getCurrency().equals("CHF"))
            .findFirst().orElseThrow().getId());
    assertThat(withdrawal.getConnectedIdTransaction()).isEqualTo(deposit.getIdTransaction());
    assertThat(deposit.getConnectedIdTransaction()).isEqualTo(withdrawal.getIdTransaction());
    assertThat(withdrawal.getCashaccountAmount()).isNegative();
    assertThat(deposit.getCashaccountAmount()).isEqualTo(-withdrawal.getCashaccountAmount() * USD_CHF, within(0.01));
    assertThat(withdrawal.getCurrencyExRate()).isCloseTo(USD_CHF, within(1e-4));
    assertThat(withdrawal.getAlgoFillId()).as("a transfer has no fill identity, and the unique key permits many nulls")
        .isNull();

    Transaction purchase = generatedTrades(run).getFirst();
    assertThat(withdrawal.getTransactionTime().toLocalDate())
        .as("money that arrives on the day of the order does not pay for it: the overdraft guard takes the balance"
            + " before the day, so the transfer has to be dated earlier than the fill")
        .isBefore(purchase.getTransactionTime().toLocalDate());
  }

  @Test
  @DisplayName("Nothing is moved while one cash account can pay the order outright")
  void nothingIsMovedWhileOneAccountCanPayTheOrder() throws Exception {
    allocation();

    AlgoSimulationResult run = run(environment());

    assertThat(run.getStatus()).as("failure was: %s", run.getFailureMessage())
        .isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    assertThat(trailOf(run)).as("the environment settles from one account, so its cash stays where it was put")
        .doesNotContain(AlgoEventType.FUNDING_TRANSFER);
    assertThat(generatedCashMovements(run)).isEmpty();
  }

  @Test
  @DisplayName("A repeat run removes the funding transfers of the previous one")
  void aRepeatRunRemovesTheFundingTransfersOfThePreviousOne() throws Exception {
    foreignAllocation();
    Tenant environment = environment(Map.of(cashId, 100000.0, usdCashId, 100000.0));

    AlgoSimulationResult first = run(environment);
    int afterFirst = source.transactions(environment.getId(), end.plusDays(1)).size();
    AlgoSimulationResult second = run(environment);

    assertThat(first.getIdSimulationResult()).isEqualTo(second.getIdSimulationResult());
    assertThat(second.getStatus()).as("failure was: %s", second.getFailureMessage())
        .isEqualTo(AlgoSimulationRunStatus.COMPLETED);
    assertThat(source.transactions(environment.getId(), end.plusDays(1))).as("both sides of a transfer are generated,"
        + " so both are deleted again and the second run neither doubles nor orphans them").hasSize(afterFirst);
    assertThat(
        source.transactions(environment.getId(), end.plusDays(1)).stream().filter(Transaction::isSimulationOpening))
            .as("the opening ledger is the two deposits and nothing else").hasSize(2);
  }

  @Test
  @DisplayName("An environment without a recoverable opening definition is refused rather than replayed")
  void openingDefinitionIsRequired() throws Exception {
    Tenant environment = environment();
    em.createQuery("UPDATE Tenant t SET t.simulationStartDate = NULL WHERE t.idTenant = ?1")
        .setParameter(1, environment.getId()).executeUpdate();
    em.clear();

    assertViolation(() -> replay.prepare(environment.getId(), end, user), "simulation.start.date",
        "gt.simulation.run.no.opening");
  }

  @Test
  @DisplayName("An end date that does not lie after the opening date is refused")
  void endDateMustFollowTheOpeningDate() throws Exception {
    Tenant environment = environment();

    assertViolation(() -> replay.prepare(environment.getId(), opening, user), "end.date",
        "gt.simulation.run.date.invalid");
  }

  // -----------------------------------------------------------------------------------------------------------------
  // Allocation fixture: one bucket, one instrument, one constant price
  // -----------------------------------------------------------------------------------------------------------------

  /**
   * Completes the hierarchy of the fixture into a full allocation: one strategic bucket holding one instrument at 100%
   * of a 100% ceiling, with a rebalancing strategy of four redeployments per year and a two point tolerance.
   *
   * <p>
   * The instrument keeps the same closing price on every day of the window. A constant price is what makes the
   * assertions about the number of checkpoints mean something: the allocation reaches its target exactly and stays
   * there, so anything that still rebalances does so because the interval was ignored and not because prices moved.
   * </p>
   *
   * @return the instrument of the allocation
   */
  private Security asDirectBond(Security security) {
    var bondClass = em.createQuery("SELECT a FROM Assetclass a", grafioschtrader.entities.Assetclass.class)
        .getResultList().stream()
        .filter(a -> a.getCategoryType() == grafioschtrader.types.AssetclassType.FIXED_INCOME && a
            .getSpecialInvestmentInstrument() == grafioschtrader.types.SpecialInvestmentInstruments.DIRECT_INVESTMENT)
        .findFirst().orElseThrow();
    security.setAssetClass(bondClass);
    security.setDistributionFrequency(grafioschtrader.types.DistributionFrequency.DF_MONTHLY);
    return security;
  }

  private void setBondTerms(Security security, double rate, grafioschtrader.types.CouponDayCount dayCount) {
    SecurityBondTerms bondTerms = new SecurityBondTerms();
    bondTerms.setCouponRate(rate);
    bondTerms.setCouponDayCount(dayCount);
    SecuritySimulationMetadata metadata = new SecuritySimulationMetadata();
    metadata.setBondTerms(bondTerms);
    security.setSimulationMetadata(metadata);
  }

  private Security allocation() {
    securityaccount();
    Security security = tradeableChfInstrument();
    clearDividendHistory(security);
    priceEveryDay(security);
    em.find(Watchlist.class, em.find(AlgoTop.class, topId).getIdWatchlist())
        .setSecuritycurrencyList(new ArrayList<>(List.of(security)));
    bucketWith(security);
    em.flush();
    em.clear();
    return em.find(Security.class, security.getId());
  }

  /** One strategic bucket holding one instrument at 100% of a 100% ceiling, with the rebalancing strategy on top. */
  private void bucketWith(Security security) {
    AlgoAssetclass bucket = new AlgoAssetclass();
    bucket.setIdTenant(tenantId);
    bucket.setIdAlgoAssetclassParent(topId);
    bucket.setPercentage(100f);
    bucket.setName("Replay equities");
    bucket.setActivatable(true);
    em.persist(bucket);
    em.flush();
    AlgoSecurity member = new AlgoSecurity();
    member.setIdTenant(tenantId);
    member.setIdAlgoSecurityParent(bucket.getId());
    member.setPercentage(100f);
    member.setSecurity(security);
    member.setActivatable(true);
    em.persist(member);
    AlgoStrategy strategy = new AlgoStrategy();
    strategy.setIdTenant(tenantId);
    strategy.setIdAlgoAssetclassSecurity(topId);
    strategy.setAlgoStrategyImplementations(AlgoStrategyImplementationType.AS_HOLDING_TOP_REBALANCING);
    strategy.setStrategyConfig("{\"timePeriodPerYear\":4,\"thresholdPercentage\":2}");
    strategy.setActivatable(true);
    em.persist(strategy);
  }

  private void setRebalancingFrequency(int timePeriodPerYear) {
    AlgoStrategy strategy = em
        .createQuery("SELECT s FROM AlgoStrategy s WHERE s.idTenant = ?1 AND s.idAlgoAssetclassSecurity = ?2",
            AlgoStrategy.class)
        .setParameter(1, tenantId).setParameter(2, topId).getSingleResult();
    strategy.setStrategyConfig("{\"timePeriodPerYear\":" + timePeriodPerYear + ",\"thresholdPercentage\":2}");
  }

  /**
   * The same allocation, but around an instrument in a foreign currency and with the pair the environment needs to
   * value and to settle it. Two accounts funded with 100 000 each and a rate of 0.9 make an equity of 190 000 CHF,
   * which the single line of the hierarchy wants to deploy whole - more than either account holds on its own.
   *
   * @return the instrument of the allocation
   */
  private Security foreignAllocation() {
    usdChfPair();
    securityaccount();
    Security security = tradeableInstrument("USD");
    clearDividendHistory(security);
    priceEveryDay(security);
    em.find(Watchlist.class, em.find(AlgoTop.class, topId).getIdWatchlist())
        .setSecuritycurrencyList(new ArrayList<>(List.of(security)));
    bucketWith(security);
    em.flush();
    em.clear();
    return em.find(Security.class, security.getId());
  }

  /**
   * One instrument of the test database that can carry the whole window: priced in the tenant currency, not derived,
   * not a margin product, attached to an exchange and active on every day of the run.
   */
  private Security tradeableChfInstrument() {
    return tradeableInstrument("CHF");
  }

  /** Reference instruments are shared fixtures; rolled back with the test, so income is explicit in each scenario. */
  private void clearDividendHistory(Security security) {
    em.createQuery("DELETE FROM Dividend d WHERE d.idSecuritycurrency = ?1").setParameter(1, security.getId())
        .executeUpdate();
  }

  private Security tradeableInstrument(String currency) {
    List<Security> candidates = em
        .createQuery("SELECT s FROM Security s WHERE s.currency = ?3 AND s.idLinkSecuritycurrency IS NULL"
            + " AND s.assetClass IS NOT NULL AND s.stockexchange IS NOT NULL"
            + " AND (s.activeFromDate IS NULL OR s.activeFromDate <= ?1) AND s.activeToDate >= ?2"
            + " ORDER BY s.idSecuritycurrency", Security.class)
        .setParameter(1, opening).setParameter(2, end).setParameter(3, currency).setMaxResults(50).getResultList();
    return candidates.stream().filter(candidate -> !candidate.isMarginInstrument()).findFirst()
        .orElseThrow(() -> new IllegalStateException("no tradeable " + currency + " instrument in the test database"));
  }

  private void priceEveryDay(Security security) {
    priceEveryDay(security.getId(), PRICE);
  }

  /**
   * Gives one instrument or one currency pair the same closing price on every day of the window. A pair is priced the
   * same way an instrument is, because both are a securitycurrency, and both need a quote on every day: a funding
   * transfer is dated on the day the order was decided and the order itself on the next one.
   *
   * @param idSecuritycurrency the instrument or currency pair to price
   * @param price              the close every day of the window carries
   */
  private void priceEveryDay(Integer idSecuritycurrency, double price) {
    for (LocalDate date = opening; !date.isAfter(end); date = date.plusDays(1)) {
      List<Historyquote> existing = em
          .createQuery("SELECT h FROM Historyquote h WHERE h.idSecuritycurrency = ?1 AND h.date = ?2",
              Historyquote.class)
          .setParameter(1, idSecuritycurrency).setParameter(2, date).getResultList();
      if (existing.isEmpty()) {
        em.persist(new Historyquote(idSecuritycurrency, HistoryquoteCreateType.CONNECTOR_CREATED, date, price));
      } else {
        existing.getFirst().setClose(price);
      }
    }
    em.flush();
  }

  /** The pair the environment needs to value its USD account and to price a transfer out of it. */
  private Currencypair usdChfPair() {
    Currencypair pair = em
        .createQuery("SELECT c FROM Currencypair c WHERE c.fromCurrency = 'USD' AND c.toCurrency = 'CHF'",
            Currencypair.class)
        .setMaxResults(1).getResultList().stream().findFirst().orElse(null);
    if (pair == null) {
      pair = new Currencypair("USD", "CHF");
      em.persist(pair);
      em.flush();
    }
    priceEveryDay(pair.getIdSecuritycurrency(), USD_CHF);
    return pair;
  }

  /** The custody account a replayed order is booked into; without one a run has nowhere to put a position. */
  private Securityaccount securityaccount() {
    List<Securityaccount> existing = em
        .createQuery("SELECT a FROM Securityaccount a WHERE a.idTenant = ?1", Securityaccount.class)
        .setParameter(1, tenantId).getResultList();
    if (!existing.isEmpty()) {
      return existing.getFirst();
    }
    Securityaccount account = new Securityaccount("Replay custody", em.find(Cashaccount.class, cashId).getPortfolio());
    account.setIdTenant(tenantId);
    account.setLowestTransactionCost(0f);
    account.setTradingPlatformPlan(em.createQuery("SELECT p FROM TradingPlatformPlan p", TradingPlatformPlan.class)
        .setMaxResults(1).getSingleResult());
    em.persist(account);
    em.flush();
    return account;
  }

  /**
   * Puts a position into the main tenant on the opening day, so that a copied environment starts invested. Booked
   * directly rather than through the transaction repository: this is the opening state of a fixture and not a trade
   * under test.
   *
   * @param security the instrument to hold
   * @param units    how many are held
   */
  private void holdInMainTenant(Security security, double units) {
    Securityaccount account = securityaccount();
    Transaction trade = new Transaction(account.getId(), em.find(Cashaccount.class, cashId),
        em.find(Security.class, security.getId()), -units * PRICE, units, PRICE, TransactionType.ACCUMULATE, 0.0, 0.0,
        null, opening.atTime(16, 0), null, null, null, false);
    trade.setIdTenant(tenantId);
    em.persist(trade);
    em.flush();
    em.clear();
    balances.createCashaccountBalanceEntireByTenant(tenantId);
  }

  private Tenant copiedEnvironment() throws Exception {
    SimulationTenantCreateDTO dto = new SimulationTenantCreateDTO();
    dto.setIdAlgoTop(topId);
    dto.setTenantName("Replay copied environment");
    dto.setInitializationMode(SimulationInitializationMode.COPY_PORTFOLIO);
    dto.setSimulationStartDate(opening);
    return simulations.createSimulationTenant(dto);
  }

  private List<AlgoEventType> trailOf(AlgoSimulationResult run) {
    return events.findByIdSimulationResultOrderByEventDateDescIdAlgoEventDesc(run.getIdSimulationResult(),
        PageRequest.of(0, 500)).getContent().stream().map(entry -> entry.getEventType()).toList();
  }

  private List<Transaction> generatedTrades(AlgoSimulationResult run) {
    return source.transactions(run.getIdTenant(), end.plusDays(1)).stream()
        .filter(transaction -> !transaction.isSimulationOpening() && transaction.getSecurity() != null).toList();
  }

  private double netUnits(List<Transaction> transactions, Security security) {
    return transactions.stream()
        .filter(t -> t.getSecurity() != null && t.getSecurity().getId().equals(security.getId()))
        .filter(t -> t.getTransactionType() == TransactionType.ACCUMULATE
            || t.getTransactionType() == TransactionType.REDUCE)
        .mapToDouble(t -> t.getUnits() * (t.getTransactionType() == TransactionType.ACCUMULATE ? 1 : -1)).sum();
  }

  /** The cash movements a run generated: everything it wrote that carries no instrument. */
  private List<Transaction> generatedCashMovements(AlgoSimulationResult run) {
    return source.transactions(run.getIdTenant(), end.plusDays(1)).stream()
        .filter(transaction -> !transaction.isSimulationOpening() && transaction.getSecurity() == null).toList();
  }

  private List<AlgoEventLog> eventsOf(AlgoSimulationResult run) {
    return events.findByIdSimulationResultOrderByEventDateDescIdAlgoEventDesc(run.getIdSimulationResult(),
        PageRequest.of(0, 500)).getContent();
  }

  private Transaction oneOfType(List<Transaction> transactions, TransactionType type) {
    return transactions.stream().filter(transaction -> transaction.getTransactionType() == type).findFirst()
        .orElseThrow(() -> new AssertionError("no " + type + " among " + transactions.size() + " cash movements"));
  }
}
