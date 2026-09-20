package grafioschtrader.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.dto.TransactionCostEstimateRequest;
import grafioschtrader.dto.TransactionCostEstimateResult;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.types.TransactionType;

/**
 * What a fill of one historical replay costs, taken from the fee model the security account it settles in already
 * carries.
 *
 * <p>
 * A simulation environment is a copy of the portfolio, and the copy keeps the trading platform plan and the optional
 * account level fee model of every security account. The replay therefore needs no configuration of its own: it charges
 * what the real account would have charged, and an environment whose accounts have no model keeps costing nothing,
 * exactly as every run did before.
 * </p>
 *
 * <p>
 * One instance per run, not a bean. The resolution is cached, and two runs execute at the same time on the worker pool,
 * so a shared cache would let one environment answer for the accounts of another.
 * </p>
 */
public class AlgoReplayFees {

  /** The effective YAML per security account of the environment; an account without a model is absent. */
  private final Map<Integer, String> modelByAccount = new HashMap<>();
  /** The plan the request names, so a model that reaches for it resolves the same plan the account belongs to. */
  private final Map<Integer, Integer> planByAccount = new HashMap<>();
  private final TransactionCostEvalExEstimator estimator;

  /**
   * @param securityaccounts the security accounts of the environment, already loaded with their trading platform plan
   * @param estimator        evaluates the rules of a model
   */
  public AlgoReplayFees(List<Securityaccount> securityaccounts, TransactionCostEvalExEstimator estimator) {
    this.estimator = estimator;
    for (Securityaccount sa : securityaccounts) {
      TradingPlatformPlan plan = sa.getTradingPlatformPlan();
      if (plan != null) {
        planByAccount.put(sa.getId(), plan.getIdTradingPlatformPlan());
      }
      String yaml = effectiveYaml(sa);
      if (yaml != null) {
        modelByAccount.put(sa.getId(), yaml);
      }
    }
  }

  /**
   * The model that applies to one security account: an account level override wins over the model of its trading
   * platform plan, which is the precedence of the fee comparison report.
   *
   * @param sa the security account
   * @return its effective YAML, or null when neither carries one
   */
  public static String effectiveYaml(Securityaccount sa) {
    String yaml = sa.getFeeModelYaml();
    if (yaml != null && !yaml.isBlank()) {
      return yaml;
    }
    TradingPlatformPlan plan = sa.getTradingPlatformPlan();
    yaml = plan == null ? null : plan.getFeeModelYaml();
    return yaml == null || yaml.isBlank() ? null : yaml;
  }

  /**
   * Whether the environment charges anything at all, which is what the conventions of its run state. Asked before a run
   * exists, so it works on the accounts rather than on an instance.
   *
   * @param securityaccounts the security accounts of the environment
   * @return true when at least one of them resolves a fee model
   */
  public static boolean anyModelActive(List<Securityaccount> securityaccounts) {
    return securityaccounts.stream().anyMatch(sa -> effectiveYaml(sa) != null);
  }

  /**
   * What the given order costs in the currency of the instrument, which is the currency
   * {@code Transaction.transactionCost} is expressed in - the costs enter the amount before it is converted into the
   * currency of the cash account.
   *
   * <p>
   * A model that cannot answer is an error rather than a zero. A period based model whose periods do not reach back
   * over the replayed years, or a rule set no rule of which matches, would otherwise make exactly the trades it does
   * not describe the cheapest ones of the run.
   * </p>
   *
   * @param idSecurityaccount where the order settles, deciding which model applies
   * @param security          the traded instrument
   * @param units             number of units, sign ignored
   * @param quotation         price per unit in the currency of the instrument
   * @param type              direction of the order
   * @param date              the fill day, selecting the period of a time based model
   * @param fixedAssets       what the environment is worth on that day, for a model whose fee is graded by size
   * @return the cost, rounded the way money is rounded, and 0 where no model applies
   */
  public double cost(Integer idSecurityaccount, Security security, double units, double quotation, TransactionType type,
      LocalDate date, double fixedAssets) {
    String yaml = modelByAccount.get(idSecurityaccount);
    if (yaml == null) {
      return 0;
    }
    TransactionCostEstimateRequest request = TransactionCostEvalExEstimator.buildRequest(security, Math.abs(units),
        quotation, type, date, planByAccount.get(idSecurityaccount), fixedAssets);
    TransactionCostEstimateResult result = estimator.evaluateYaml(yaml, request);
    if (result.getError() != null || result.getEstimatedCost() == null || !Double.isFinite(result.getEstimatedCost())) {
      throw new IllegalArgumentException(
          "REPLAY_FEE_MODEL_FAILED: " + (result.getError() == null ? "no cost for " + date : result.getError()));
    }
    double cost = DataBusinessHelper.roundStandard(result.getEstimatedCost());
    if (cost < 0) {
      throw new IllegalArgumentException("REPLAY_FEE_MODEL_FAILED: negative cost " + cost + " on " + date);
    }
    return cost;
  }
}
