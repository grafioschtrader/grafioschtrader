package grafioschtrader.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import grafioschtrader.dto.FeeModelConfig;
import grafioschtrader.dto.TransactionCostEstimateRequest;
import grafioschtrader.dto.TransactionCostEstimateResult;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.repository.TradingPlatformPlanJpaRepository;
import grafioschtrader.service.FeeTradeCounter.FeeTradeCounts;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TransactionType;

/**
 * Evaluates rule-based fee models configured as YAML with EvalEx expressions on TradingPlatformPlan. Supports two
 * formats: flat rules (always applicable) or time-based periods with nested rules. Rules within a format are evaluated
 * top-to-bottom; the first rule whose condition is true determines the fee.
 */
@Service
public class TransactionCostEvalExEstimator {

  private static final Logger log = LoggerFactory.getLogger(TransactionCostEvalExEstimator.class);

  @Autowired
  private TradingPlatformPlanJpaRepository tradingPlatformPlanJpaRepository;

  private final YAMLMapper yamlMapper = new YAMLMapper();

  /**
   * Estimates transaction costs using inline YAML if provided, otherwise falls back to the DB-based plan lookup.
   *
   * @param request the estimation request, optionally containing inline YAML
   * @return the estimation result with the matched rule and calculated cost
   */
  public TransactionCostEstimateResult estimateWithOptionalYaml(TransactionCostEstimateRequest request) {
    if (request.getYaml() != null && !request.getYaml().isBlank()) {
      return evaluateYaml(request.getYaml(), request);
    }
    return estimate(request);
  }

  /**
   * Estimates transaction costs by evaluating the YAML fee model on the given TradingPlatformPlan.
   *
   * @param request the estimation request containing trade parameters and the plan ID
   * @return the estimation result with the matched rule and calculated cost
   */
  public TransactionCostEstimateResult estimate(TransactionCostEstimateRequest request) {
    try {
      TradingPlatformPlan plan = tradingPlatformPlanJpaRepository.findById(request.getIdTradingPlatformPlan())
          .orElse(null);
      if (plan == null) {
        return TransactionCostEstimateResult
            .error("TradingPlatformPlan not found: " + request.getIdTradingPlatformPlan());
      }
      if (plan.getFeeModelYaml() == null || plan.getFeeModelYaml().isBlank()) {
        return TransactionCostEstimateResult.error("No fee model YAML configured on this plan");
      }
      return evaluateYaml(plan.getFeeModelYaml(), request);
    } catch (Exception e) {
      log.error("Fee estimation failed", e);
      return TransactionCostEstimateResult.error("Estimation failed: " + e.getMessage());
    }
  }

  /**
   * Evaluates a fee model YAML string directly (without loading from DB), useful for testing. Supports both flat rules
   * and time-based periods format.
   *
   * @param yaml    the YAML fee model string
   * @param request the estimation request with trade parameters and optional transactionDate
   * @return the estimation result
   */
  public TransactionCostEstimateResult evaluateYaml(String yaml, TransactionCostEstimateRequest request) {
    try {
      List<String> errors = validate(yaml);
      if (!errors.isEmpty())
        return TransactionCostEstimateResult.error(String.join("; ", errors));
      FeeModelConfig config = yamlMapper.readValue(yaml, FeeModelConfig.class);

      LocalDate date = parseTransactionDate(request.getTransactionDate());
      var periods = config.getPeriods() == null ? null
          : config.getPeriods().stream()
              .map(p -> new FeeRuleEvaluator.Period(p.getValidFrom(), p.getValidTo(), p.getRules())).toList();
      var period = FeeRuleEvaluator.select(config.getRules(), periods, date);
      var match = FeeRuleEvaluator.evaluate(period, bindVariables(request));
      if (match.period() == null)
        return TransactionCostEstimateResult.error("No fee period applies for date " + date);
      if (match.rule() == null)
        return TransactionCostEstimateResult.error("No rule matched the given parameters");
      return TransactionCostEstimateResult.success(match.value(), match.rule().getName());
    } catch (Exception e) {
      log.error("Fee model evaluation failed", e);
      return TransactionCostEstimateResult.error("Evaluation error: " + e.getMessage());
    }
  }

  /**
   * Maps one trade onto the request the fee rules are evaluated against. Both the fee comparison report and the
   * historical replay use it, so that a simulated cost is produced from exactly the inputs the user calibrated the
   * model with; a second, independent mapping would drift from the report without anyone noticing.
   *
   * @param security              the traded instrument, supplying instrument type, asset class, MIC and currency
   * @param units                 number of units traded, sign ignored
   * @param quotation             price per unit in the currency of the instrument
   * @param transactionType       ACCUMULATE becomes trade direction 0 (buy), everything else 1 (sell)
   * @param date                  the day the fee applies on, selecting the matching period of a time-based model
   * @param idTradingPlatformPlan the plan whose model is evaluated when no inline YAML is supplied
   * @param fixedAssets           the account or portfolio value a tiered model grades the fee by, 0 when unknown
   * @return a request carrying every variable {@code bindVariables} can bind
   */
  public static TransactionCostEstimateRequest buildRequest(Security security, double units, double quotation,
      TransactionType transactionType, LocalDate date, Integer idTradingPlatformPlan, double fixedAssets) {
    return buildRequest(security, units, quotation, transactionType, date, idTradingPlatformPlan, fixedAssets, null,
        FeeTradeCounts.NONE);
  }

  /**
   * Like {@link #buildRequest(Security, double, double, TransactionType, LocalDate, Integer, double)}, additionally
   * supplying what a rule needs for currency conversion mark-ups and trade-count allowances.
   *
   * @param settlementCurrency the currency of the cash account the trade settles in, null when unknown
   * @param counts             earlier trades in the calendar periods of this trade, from a {@link FeeTradeCounter}
   */
  public static TransactionCostEstimateRequest buildRequest(Security security, double units, double quotation,
      TransactionType transactionType, LocalDate date, Integer idTradingPlatformPlan, double fixedAssets,
      String settlementCurrency, FeeTradeCounts counts) {
    TransactionCostEstimateRequest req = new TransactionCostEstimateRequest();
    req.setSettlementCurrency(settlementCurrency);
    req.setTradesInMonth(counts.month());
    req.setTradesInQuarter(counts.quarter());
    req.setTradesInYear(counts.year());
    req.setSecurityTradesInMonth(counts.securityMonth());
    req.setIdTradingPlatformPlan(idTradingPlatformPlan);
    req.setUnits(units);
    req.setTradeValue(units * quotation);
    Assetclass assetclass = security == null ? null : security.getAssetClass();
    req.setSpecInvestInstrument(assetclass != null && assetclass.getSpecialInvestmentInstrument() != null
        ? (int) assetclass.getSpecialInvestmentInstrument().getValue()
        : 0);
    req.setCategoryType(
        assetclass != null && assetclass.getCategoryType() != null ? (int) assetclass.getCategoryType().getValue() : 0);
    req.setMic(security != null && security.getStockexchange() != null && security.getStockexchange().getMic() != null
        ? security.getStockexchange().getMic()
        : "");
    req.setCurrency(security != null && security.getCurrency() != null ? security.getCurrency() : "");
    req.setTradeDirection(transactionType == TransactionType.ACCUMULATE ? 0 : 1);
    req.setFixedAssets(fixedAssets);
    req.setTransactionDate(date == null ? null : date.toString());
    return req;
  }

  private LocalDate parseTransactionDate(String transactionDateStr) {
    if (transactionDateStr == null || transactionDateStr.isBlank()) {
      return LocalDate.now();
    }
    return LocalDate.parse(transactionDateStr);
  }

  private Map<String, Object> bindVariables(TransactionCostEstimateRequest req) {
    Map<String, Object> bindings = new HashMap<>();
    if (req.getTradeValue() != null) {
      bindings.put("tradeValue", req.getTradeValue());
    }
    if (req.getUnits() != null) {
      bindings.put("units", req.getUnits());
    }
    if (req.getSpecInvestInstrument() != null) {
      bindings.put("specInvestInstrument", req.getSpecInvestInstrument());
      SpecialInvestmentInstruments sii = SpecialInvestmentInstruments
          .getSpecialInvestmentInstrumentsByValue(req.getSpecInvestInstrument().byteValue());
      bindings.put("instrument", sii != null ? sii.name() : "");
    }
    if (req.getCategoryType() != null) {
      bindings.put("categoryType", req.getCategoryType());
      AssetclassType act = AssetclassType.getAssetClassTypeByValue(req.getCategoryType().byteValue());
      bindings.put("assetclass", act != null ? act.name() : "");
    }
    if (req.getMic() != null) {
      bindings.put("mic", req.getMic());
    }
    if (req.getCurrency() != null) {
      bindings.put("currency", req.getCurrency());
    }
    if (req.getFixedAssets() != null) {
      bindings.put("fixedAssets", req.getFixedAssets());
    }
    if (req.getTradeDirection() != null) {
      bindings.put("tradeDirection", req.getTradeDirection());
    }
    // Always bound, so a rule using them never fails on a request that does not know them (preview, older callers).
    bindings.put("settlementCurrency", req.getSettlementCurrency() == null ? "" : req.getSettlementCurrency());
    bindings.put("tradesInMonth", count(req.getTradesInMonth()));
    bindings.put("tradesInQuarter", count(req.getTradesInQuarter()));
    bindings.put("tradesInYear", count(req.getTradesInYear()));
    bindings.put("securityTradesInMonth", count(req.getSecurityTradesInMonth()));
    return bindings;
  }

  private static int count(Integer value) {
    return value == null ? 0 : value;
  }

  /** Uses the same validation contract as configuration saves. */
  private static final FeeModelYamlValidator VALIDATOR = new FeeModelYamlValidator();

  public List<String> validate(String yaml) {
    return VALIDATOR.validate(yaml);
  }
}
