package grafioschtrader.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.User;
import grafioschtrader.dto.FeeModelComparisonDetail;
import grafioschtrader.dto.FeeModelComparisonResponse;
import grafioschtrader.dto.TransactionCostEstimateRequest;
import grafioschtrader.dto.TransactionCostEstimateResult;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.SecurityaccountJpaRepository;
import grafioschtrader.types.TransactionType;
import jakarta.persistence.EntityManager;

/**
 * Compares actual recorded transaction costs with estimated costs from the EvalEx-based fee model configured on a
 * security account's TradingPlatformPlan.
 */
@Service
public class FeeModelComparisonService {

  @Autowired
  private SecurityaccountJpaRepository securityaccountJpaRepository;

  @Autowired
  private TransactionCostEvalExEstimator estimator;

  @Autowired
  private EntityManager entityManager;

  /**
   * Loads BUY/SELL transactions for the given security account and compares actual costs with the fee model estimates.
   *
   * @param idSecuritycashAccount the security account ID
   * @param excludeZeroCost       if true, skip transactions with null or zero cost
   * @return comparison response with summary statistics and detail rows
   */
  @Transactional(readOnly = true)
  public FeeModelComparisonResponse compare(Integer idSecuritycashAccount, boolean excludeZeroCost) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Securityaccount sa = securityaccountJpaRepository.findByIdSecuritycashAccountAndIdTenant(idSecuritycashAccount,
        user.getIdTenant());
    if (sa == null) {
      return emptyResponse("Security account not found or not owned by current tenant");
    }

    TradingPlatformPlan plan = sa.getTradingPlatformPlan();
    String effectiveYaml;
    String planName;
    if (sa.getFeeModelYaml() != null && !sa.getFeeModelYaml().isBlank()) {
      effectiveYaml = sa.getFeeModelYaml();
      planName = sa.getName() + " (account override)";
    } else {
      if (plan == null || plan.getFeeModelYaml() == null || plan.getFeeModelYaml().isBlank()) {
        return emptyResponse("No fee model configured");
      }
      effectiveYaml = plan.getFeeModelYaml();
      planName = plan.getPlatformPlanNameNLS() != null
          ? plan.getPlatformPlanNameNLS().getMap().values().stream().findFirst().orElse("(unnamed)")
          : "(unnamed)";
    }

    List<Transaction> transactions = loadBuySellTransactions(idSecuritycashAccount);

    FeeModelComparisonResponse response = new FeeModelComparisonResponse();
    response.setPlanName(planName);
    response.setTotalTransactions(transactions.size());

    List<FeeModelComparisonDetail> details = new ArrayList<>();
    List<Double> actualCosts = new ArrayList<>();
    List<Double> estimatedCosts = new ArrayList<>();
    List<Double> absoluteErrors = new ArrayList<>();
    List<Double> relativeErrors = new ArrayList<>();
    List<Double> squaredErrors = new ArrayList<>();
    int skipped = 0;
    int errors = 0;

    for (Transaction tx : transactions) {
      if (tx.getTransactionCost() == null || tx.getTransactionCost() == 0.0) {
        if (excludeZeroCost) {
          skipped++;
          continue;
        }
      }

      TransactionCostEstimateRequest request = buildRequest(tx, plan);
      FeeModelComparisonDetail detail = buildDetail(tx, request);

      TransactionCostEstimateResult result;
      try {
        result = estimator.evaluateYaml(effectiveYaml, request);
      } catch (Exception e) {
        detail.setError("Evaluation exception: " + e.getMessage());
        errors++;
        details.add(detail);
        continue;
      }

      if (result.getError() != null) {
        detail.setError(result.getError());
        errors++;
        details.add(detail);
        continue;
      }

      double actual = detail.getActualCost();
      double estimated = result.getEstimatedCost();
      detail.setEstimatedCost(estimated);
      detail.setMatchedRuleName(result.getMatchedRuleName());

      double absError = Math.abs(estimated - actual);
      double relError = actual != 0.0 ? (absError / Math.abs(actual)) * 100.0 : (estimated != 0.0 ? 100.0 : 0.0);
      detail.setRelativeError(relError);

      actualCosts.add(actual);
      estimatedCosts.add(estimated);
      absoluteErrors.add(absError);
      relativeErrors.add(relError);
      squaredErrors.add(absError * absError);

      details.add(detail);
    }

    response.setSkippedCount(skipped);
    response.setErrorCount(errors);
    response.setComparedCount(actualCosts.size());
    response.setDetails(details);

    if (!actualCosts.isEmpty()) {
      response.setMeanActualCost(actualCosts.stream().mapToDouble(Double::doubleValue).average().orElse(0));
      response.setMeanEstimatedCost(estimatedCosts.stream().mapToDouble(Double::doubleValue).average().orElse(0));
      response.setMeanAbsoluteError(absoluteErrors.stream().mapToDouble(Double::doubleValue).average().orElse(0));
      response.setMeanRelativeError(relativeErrors.stream().mapToDouble(Double::doubleValue).average().orElse(0));
      response.setRmse(Math.sqrt(squaredErrors.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
    }

    return response;
  }

  private FeeModelComparisonDetail buildDetail(Transaction tx, TransactionCostEstimateRequest request) {
    FeeModelComparisonDetail detail = new FeeModelComparisonDetail();
    detail.setTransactionDate(
        tx.getTransactionDate() != null ? tx.getTransactionDate() : tx.getTransactionTime().toLocalDate());
    detail.setTransactionType(tx.getTransactionType().name());
    detail.setSecurityName(tx.getSecurity() != null ? tx.getSecurity().getName() : "?");

    if (tx.getSecurity() != null && tx.getSecurity().getAssetClass() != null) {
      if (tx.getSecurity().getAssetClass().getCategoryType() != null) {
        detail.setCategoryType(tx.getSecurity().getAssetClass().getCategoryType().name());
      }
      if (tx.getSecurity().getAssetClass().getSpecialInvestmentInstrument() != null) {
        detail.setSpecInvestInstrument(tx.getSecurity().getAssetClass().getSpecialInvestmentInstrument().name());
      }
    }

    detail.setMic(tx.getSecurity() != null && tx.getSecurity().getStockexchange() != null
        ? tx.getSecurity().getStockexchange().getMic()
        : "");
    detail.setCurrency(tx.getSecurity() != null ? tx.getSecurity().getCurrency() : "");
    detail.setQuotation(tx.getQuotation() != null ? tx.getQuotation() : 0.0);
    detail.setUnits(tx.getUnits() != null ? tx.getUnits() : 0.0);
    detail.setTradeValue(request.getTradeValue());
    detail.setActualCost(tx.getTransactionCost() != null ? tx.getTransactionCost() : 0.0);
    return detail;
  }

  /**
   * Builds the estimation request from a recorded transaction. The mapping itself lives on the estimator, so that the
   * historical replay evaluates a fee model against exactly the inputs this report calibrates it with.
   *
   * <p>
   * {@code fixedAssets} is 0 here: the report walks years of transactions and the account value of each of those days
   * is not loaded, so a tiered model is graded against an unknown rather than against a value of the wrong day.
   * </p>
   */
  private TransactionCostEstimateRequest buildRequest(Transaction tx, TradingPlatformPlan plan) {
    return TransactionCostEvalExEstimator.buildRequest(tx.getSecurity(), tx.getUnits() != null ? tx.getUnits() : 0.0,
        tx.getQuotation() != null ? tx.getQuotation() : 0.0, tx.getTransactionType(),
        tx.getTransactionDate() != null ? tx.getTransactionDate()
            : (tx.getTransactionTime() != null ? tx.getTransactionTime().toLocalDate() : null),
        plan.getIdTradingPlatformPlan(), 0.0);
  }

  private List<Transaction> loadBuySellTransactions(Integer idSecuritycashAccount) {
    return entityManager
        .createQuery(
            "SELECT t FROM Transaction t JOIN FETCH t.security s "
                + "JOIN FETCH s.assetClass JOIN FETCH s.stockexchange " + "WHERE t.idSecurityaccount = :idSa "
                + "AND t.transactionType IN (:buy, :sell) AND t.security IS NOT NULL " + "ORDER BY t.transactionDate",
            Transaction.class)
        .setParameter("idSa", idSecuritycashAccount).setParameter("buy", TransactionType.ACCUMULATE.getValue())
        .setParameter("sell", TransactionType.REDUCE.getValue()).getResultList();
  }

  private FeeModelComparisonResponse emptyResponse(String planName) {
    FeeModelComparisonResponse response = new FeeModelComparisonResponse();
    response.setPlanName(planName);
    response.setDetails(List.of());
    return response;
  }
}
