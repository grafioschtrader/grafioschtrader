package grafioschtrader.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.dto.TransactionCostEstimateRequest;
import grafioschtrader.dto.TransactionCostEstimateResult;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.entities.Transaction;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.TransactionType;
import jakarta.persistence.EntityManager;

/**
 * Manual report test that loads real BUY/SELL transactions from the database,
 * runs the EvalEx fee estimator against each transaction's TradingPlatformPlan YAML,
 * and compares estimated costs with actual recorded transactionCost.
 *
 * Results are printed as a per-plan statistical summary.
 * Requires a populated database — not for CI.
 */
@SpringBootTest(classes = GTforTest.class)
@Transactional
// @Disabled("Manual report — requires database with transaction data")
class TransactionCostEstimatorReportTest {

  /** Default relError filter range: only rows within [min%, max%] are printed. */
  private static final double DEFAULT_MIN_REL_ERROR = 10.0;
  private static final double DEFAULT_MAX_REL_ERROR = 100.0;

  /**
   * Controls which TradingPlatformPlans appear in the report and the relError filter
   * range for each. Key = TradingPlatformPlan ID, value = {min%, max%}.
   * Only plans present in this map are included in the report output.
   * Within each plan, only transactions whose relError falls in [min, max] are listed.
   */
  private final Map<Integer, double[]> reportPlanConfig = new HashMap<>();

  @Autowired
  private TransactionCostEvalExEstimator estimator;

  @Autowired
  private EntityManager entityManager;

  @Test
  @DisplayName("Report: compare estimated vs actual transaction costs per TradingPlatformPlan")
  void generateCostComparisonReport() {
    // ---- Plans to include in the report with relError range {min%, max%} ----
    // Only plans listed here will appear. Adjust ranges per plan as needed.
    double[] defaultRange = {DEFAULT_MIN_REL_ERROR, DEFAULT_MAX_REL_ERROR};
    reportPlanConfig.put(3, defaultRange);   // E-Trading - PostFinance Standard
    reportPlanConfig.put(4, defaultRange);   // Swissquote Flat Fee or Transaction Value
    reportPlanConfig.put(5, defaultRange);   // CornèrTrader Transactions value
    reportPlanConfig.put(6, defaultRange);   // Raiffeisen Switzerland
    reportPlanConfig.put(7, defaultRange);   // TradeDirect Switzerland
    reportPlanConfig.put(23, defaultRange);  // Saxo Trader
    reportPlanConfig.put(1, defaultRange);  // Migros Bank Normal
    reportPlanConfig.put(2, defaultRange);  // Migros Bank Vorsorge


    Map<Integer, TradingPlatformPlan> planBySecurityAccount = loadPlansBySecurityAccount();
    List<Transaction> transactions = loadBuySellTransactions();

    // Group statistics per TradingPlatformPlan id
    Map<Integer, PlanStats> statsByPlan = new HashMap<>();

    for (Transaction tx : transactions) {
      if (tx.getIdSecurityaccount() == null) {
        continue;
      }
      TradingPlatformPlan plan = planBySecurityAccount.get(tx.getIdSecurityaccount());
      if (plan == null || plan.getFeeModelYaml() == null || plan.getFeeModelYaml().isBlank()) {
        continue;
      }
      if (!reportPlanConfig.containsKey(plan.getIdTradingPlatformPlan())) {
        continue;
      }

      PlanStats stats = statsByPlan.computeIfAbsent(plan.getIdTradingPlatformPlan(),
          id -> new PlanStats(plan));
      stats.totalTransactions++;

      if (tx.getTransactionCost() == null || tx.getTransactionCost() == 0.0) {
        stats.skippedNullOrZeroCost++;
        continue;
      }

      TransactionCostEstimateRequest request = buildRequest(tx, plan);
      TransactionCostEstimateResult result;
      try {
        result = estimator.evaluateYaml(plan.getFeeModelYaml(), request);
      } catch (Exception e) {
        stats.errors++;
        stats.errorDetails.add(formatTxRef(tx) + " — exception: " + e.getMessage());
        continue;
      }

      if (result.getError() != null) {
        stats.errors++;
        stats.errorDetails.add(formatTxRef(tx) + " — " + result.getError());
        continue;
      }

      double actual = tx.getTransactionCost();
      double estimated = result.getEstimatedCost();
      double absError = Math.abs(estimated - actual);
      double relError = actual != 0.0 ? (absError / Math.abs(actual)) * 100.0 : (estimated != 0.0 ? 100.0 : 0.0);

      stats.comparedCount++;
      stats.actualCosts.add(actual);
      stats.estimatedCosts.add(estimated);
      stats.absoluteErrors.add(absError);
      stats.relativeErrors.add(relError);
      stats.squaredErrors.add(absError * absError);

      double deviation = estimated - actual;
      if (deviation > stats.maxOverestimation) {
        stats.maxOverestimation = deviation;
      }
      if (deviation < stats.maxUnderestimation) {
        stats.maxUnderestimation = deviation;
      }

      String txType = tx.getTransactionType() == TransactionType.ACCUMULATE ? "BUY" : "SELL";
      String specInvest = "";
      String catType = "";
      if (tx.getSecurity() != null && tx.getSecurity().getAssetClass() != null) {
        if (tx.getSecurity().getAssetClass().getSpecialInvestmentInstrument() != null) {
          specInvest = tx.getSecurity().getAssetClass().getSpecialInvestmentInstrument().name();
        }
        if (tx.getSecurity().getAssetClass().getCategoryType() != null) {
          catType = tx.getSecurity().getAssetClass().getCategoryType().name();
        }
      }
      stats.txDetails.add(new TxDetail(
          tx.getIdTransaction(),
          tx.getTransactionDate(),
          txType,
          tx.getSecurity() != null ? tx.getSecurity().getName() : "?",
          catType, specInvest,
          tx.getQuotation() != null ? tx.getQuotation() : 0.0,
          tx.getUnits() != null ? tx.getUnits() : 0.0,
          request.getTradeValue(),
          actual, estimated, relError,
          result.getMatchedRuleName()));
    }

    printReport(statsByPlan);
  }

  /**
   * Returns the relError filter range for the given plan from reportPlanConfig.
   *
   * @return double[2] with {minRelError, maxRelError}
   */
  private double[] getErrorRange(int planId) {
    return reportPlanConfig.getOrDefault(planId, new double[]{DEFAULT_MIN_REL_ERROR, DEFAULT_MAX_REL_ERROR});
  }

  /**
   * Builds a request with all variables populated (using defaults where data is unavailable).
   * EvalEx requires every variable referenced in a condition to be bound — null fields cause
   * "Variable not found" errors. We therefore default missing values to safe fallbacks.
   */
  private TransactionCostEstimateRequest buildRequest(Transaction tx, TradingPlatformPlan plan) {
    TransactionCostEstimateRequest req = new TransactionCostEstimateRequest();
    req.setIdTradingPlatformPlan(plan.getIdTradingPlatformPlan());

    double units = tx.getUnits() != null ? tx.getUnits() : 0.0;
    double quotation = tx.getQuotation() != null ? tx.getQuotation() : 0.0;
    req.setTradeValue(units * quotation);
    req.setUnits(units);

    if (tx.getSecurity() != null && tx.getSecurity().getAssetClass() != null) {
      req.setSpecInvestInstrument(tx.getSecurity().getAssetClass().getSpecialInvestmentInstrument() != null
          ? (int) tx.getSecurity().getAssetClass().getSpecialInvestmentInstrument().getValue() : 0);
      req.setCategoryType(tx.getSecurity().getAssetClass().getCategoryType() != null
          ? (int) tx.getSecurity().getAssetClass().getCategoryType().getValue() : 0);
    } else {
      req.setSpecInvestInstrument(0);
      req.setCategoryType(0);
    }

    req.setMic(tx.getSecurity() != null && tx.getSecurity().getStockexchange() != null
        && tx.getSecurity().getStockexchange().getMic() != null
            ? tx.getSecurity().getStockexchange().getMic() : "");
    req.setCurrency(tx.getSecurity() != null && tx.getSecurity().getCurrency() != null
        ? tx.getSecurity().getCurrency() : "");
    req.setTradeDirection(tx.getTransactionType() == TransactionType.ACCUMULATE ? 0 : 1);
    // fixedAssets (portfolio value at tx time) cannot be computed retroactively — default to 0.
    req.setFixedAssets(0.0);
    req.setTransactionDate(tx.getTransactionDate() != null ? tx.getTransactionDate().toString() : null);

    return req;
  }

  @SuppressWarnings("unchecked")
  private Map<Integer, TradingPlatformPlan> loadPlansBySecurityAccount() {
    List<Securityaccount> accounts = entityManager
        .createQuery("SELECT sa FROM Securityaccount sa", Securityaccount.class)
        .getResultList();

    Map<Integer, TradingPlatformPlan> map = new HashMap<>();
    for (Securityaccount sa : accounts) {
      if (sa.getTradingPlatformPlan() != null) {
        map.put(sa.getIdSecuritycashAccount(), sa.getTradingPlatformPlan());
      }
    }
    return map;
  }

  private List<Transaction> loadBuySellTransactions() {
    return entityManager.createQuery(
        "SELECT t FROM Transaction t JOIN FETCH t.security s " +
            "JOIN FETCH s.assetClass JOIN FETCH s.stockexchange " +
            "WHERE t.transactionType IN (:buy, :sell) AND t.security IS NOT NULL",
        Transaction.class)
        .setParameter("buy", TransactionType.ACCUMULATE.getValue())
        .setParameter("sell", TransactionType.REDUCE.getValue())
        .getResultList();
  }

  private void printReport(Map<Integer, PlanStats> statsByPlan) {
    System.out.println();
    System.out.println("=".repeat(150));
    System.out.println("  TRANSACTION COST ESTIMATOR REPORT");
    System.out.println("  Compares estimated fees (from YAML model) with actual recorded transactionCost");
    System.out.println("=".repeat(150));

    if (statsByPlan.isEmpty()) {
      System.out.println("  No TradingPlatformPlans with feeModelYaml found, or no BUY/SELL transactions.");
      return;
    }

    for (PlanStats stats : statsByPlan.values()) {
      double[] range = getErrorRange(stats.planId);
      double minErr = range[0];
      double maxErr = range[1];

      System.out.println();
      System.out.println("#".repeat(150));
      System.out.printf("  Plan: %s  (id = %d)%n", stats.planName, stats.planId);
      System.out.println("#".repeat(150));
      System.out.printf("  Total transactions:          %d%n", stats.totalTransactions);
      System.out.printf("  Skipped (null/zero cost):    %d%n", stats.skippedNullOrZeroCost);
      System.out.printf("  Errors (no match / eval):    %d%n", stats.errors);
      System.out.printf("  Successfully compared:       %d%n", stats.comparedCount);

      if (stats.comparedCount > 0) {
        DoubleSummaryStatistics actualStats = stats.actualCosts.stream()
            .mapToDouble(Double::doubleValue).summaryStatistics();
        DoubleSummaryStatistics estimatedStats = stats.estimatedCosts.stream()
            .mapToDouble(Double::doubleValue).summaryStatistics();
        double meanAbsError = stats.absoluteErrors.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double meanRelError = stats.relativeErrors.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double rmse = Math.sqrt(stats.squaredErrors.stream().mapToDouble(Double::doubleValue).average().orElse(0));

        System.out.println();
        System.out.printf("  Mean actual cost:            %.2f%n", actualStats.getAverage());
        System.out.printf("  Mean estimated cost:         %.2f%n", estimatedStats.getAverage());
        System.out.printf("  Mean absolute error:         %.2f%n", meanAbsError);
        System.out.printf("  Mean relative error:         %.1f%%%n", meanRelError);
        System.out.printf("  RMSE:                        %.2f%n", rmse);
        System.out.printf("  Max overestimation:          %.2f%n", stats.maxOverestimation);
        System.out.printf("  Max underestimation:         %.2f%n", stats.maxUnderestimation);
      }

      if (!stats.errorDetails.isEmpty()) {
        System.out.println();
        System.out.println("  Errors:");
        stats.errorDetails.stream().limit(10).forEach(e -> System.out.println("    " + e));
        if (stats.errorDetails.size() > 10) {
          System.out.printf("    ... and %d more%n", stats.errorDetails.size() - 10);
        }
      }

      if (!stats.txDetails.isEmpty()) {
        List<TxDetail> filtered = stats.txDetails.stream()
            .filter(d -> d.relError >= minErr && d.relError <= maxErr)
            .sorted(Comparator.comparing(d -> d.date != null ? d.date : LocalDate.MIN))
            .toList();

        System.out.println();
        System.out.printf("  Transactions with relError in [%.0f%% .. %.0f%%]: %d of %d%n",
            minErr, maxErr, filtered.size(), stats.txDetails.size());

        if (!filtered.isEmpty()) {
          System.out.println();
          String hdr = String.format(
              "  %-10s | %7s | %-4s | %-40s | %-17s | %-20s | %12s | %10s | %14s | %10s | %10s | %7s | %s",
              "Date", "TX-ID", "Type", "Security", "Asset Class", "Instrument",
              "Quotation", "Units", "Trade Value", "Actual", "Estimated", "Err%", "Matched Rule");
          System.out.println(hdr);
          System.out.println("  " + "-".repeat(hdr.length() - 2));
          for (TxDetail d : filtered) {
            String name = d.securityName.length() > 40
                ? d.securityName.substring(0, 37) + "..." : d.securityName;
            String cat = d.categoryType.length() > 17
                ? d.categoryType.substring(0, 14) + "..." : d.categoryType;
            String spec = d.specInvestInstrument.length() > 20
                ? d.specInvestInstrument.substring(0, 17) + "..." : d.specInvestInstrument;
            System.out.printf(
                "  %-10s | %7d | %-4s | %-40s | %-17s | %-20s | %12.4f | %10.2f | %14.2f | %10.2f | %10.2f | %6.1f%% | %s%n",
                d.date != null ? d.date : "n/a", d.txId, d.txType, name,
                cat, spec, d.quotation, d.units, d.tradeValue,
                d.actual, d.estimated, d.relError, d.ruleName);
          }
        }
      }
    }

    System.out.println();
    System.out.println("=".repeat(150));
  }

  private String formatTxRef(Transaction tx) {
    return String.format("TX %d (%s)", tx.getIdTransaction(), tx.getTransactionType().name());
  }

  /**
   * Accumulates statistics for one TradingPlatformPlan.
   */
  private static class PlanStats {
    final int planId;
    final String planName;
    int totalTransactions;
    int skippedNullOrZeroCost;
    int errors;
    int comparedCount;
    double maxOverestimation;
    double maxUnderestimation;
    final List<Double> actualCosts = new ArrayList<>();
    final List<Double> estimatedCosts = new ArrayList<>();
    final List<Double> absoluteErrors = new ArrayList<>();
    final List<Double> relativeErrors = new ArrayList<>();
    final List<Double> squaredErrors = new ArrayList<>();
    final List<String> errorDetails = new ArrayList<>();
    final List<TxDetail> txDetails = new ArrayList<>();

    PlanStats(TradingPlatformPlan plan) {
      this.planId = plan.getIdTradingPlatformPlan();
      this.planName = plan.getPlatformPlanNameNLS() != null
          ? plan.getPlatformPlanNameNLS().getMap().values().stream().findFirst().orElse("(unnamed)")
          : "(unnamed)";
    }
  }

  /**
   * Detail record for a single compared transaction.
   */
  private record TxDetail(int txId, LocalDate date, String txType, String securityName,
      String categoryType, String specInvestInstrument,
      double quotation, double units, double tradeValue,
      double actual, double estimated, double relError, String ruleName) {
  }
}
