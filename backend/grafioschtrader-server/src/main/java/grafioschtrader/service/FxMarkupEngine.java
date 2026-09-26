package grafioschtrader.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import grafioschtrader.dto.FeeRule;
import grafioschtrader.dto.FxFeeConfig;
import grafioschtrader.dto.FxMarkupRequest;
import grafioschtrader.dto.FxOutcome;
import grafioschtrader.dto.FxQuote;

/** Pure FX tariff evaluation. Market data is supplied by the caller, never fetched or persisted here. */
@Service
public class FxMarkupEngine {
  private static final Set<String> VARIABLES = Set.of("PAYCURRENCY", "RECEIVECURRENCY", "KIND", "AMOUNT", "TIERAMOUNT",
      "PAYCLASS", "RECEIVECLASS", "MIC");

  /** Returns units of receiveCurrency per unit of payCurrency, or null when no close exists on that date. */
  @FunctionalInterface
  public interface TierRate {
    Double close(String payCurrency, String receiveCurrency, LocalDate date);
  }

  public FxQuote quote(FxFeeConfig config, FxMarkupRequest request, TierRate tierRate) {
    if (config == null)
      return result(FxOutcome.NO_SECTION, null, null);
    FxFeeConfig.Period selected = null;
    try {
      var errors = validate(config);
      if (!errors.isEmpty())
        return result(FxOutcome.INVALID, null, String.join("; ", errors));
      if (request == null || request.date() == null || request.kind() == null || request.payCurrency() == null
          || request.receiveCurrency() == null || !Double.isFinite(request.amount()) || request.amount() < 0)
        return result(FxOutcome.INVALID, null, "Invalid FX request");
      var periods = config.periods() == null ? null
          : config.periods().stream().map(p -> new FeeRuleEvaluator.Period(p.validFrom(), p.validTo(), p.rules()))
              .toList();
      var period = FeeRuleEvaluator.select(config.rules(), periods, request.date());
      if (period == null)
        return result(FxOutcome.NO_PERIOD, null, null);
      if (period.from() != null)
        selected = config.periods().stream().filter(p -> p.validFrom().equals(period.from())).findFirst().orElseThrow();
      Map<String, Object> bindings = new HashMap<>();
      bindings.put("payCurrency", request.payCurrency());
      bindings.put("receiveCurrency", request.receiveCurrency());
      bindings.put("kind", request.kind().name());
      bindings.put("amount", request.amount());
      bindings.put("payClass", currencyClass(config, request.payCurrency()));
      bindings.put("receiveClass", currencyClass(config, request.receiveCurrency()));
      bindings.put("mic",
          request.kind() == FxMarkupRequest.Kind.TRANSFER || request.mic() == null ? "" : request.mic());
      if (usesTier(period.rules())) {
        Double rate = request.payCurrency().equals(config.amountCurrency()) ? Double.valueOf(1)
            : tierRate.close(request.payCurrency(), config.amountCurrency(), request.date());
        if (rate == null || !Double.isFinite(rate) || rate <= 0)
          return result(FxOutcome.NO_TIER_RATE, selected, null);
        bindings.put("tierAmount", request.amount() * rate);
      }
      var match = FeeRuleEvaluator.evaluate(period, bindings);
      if (match.rule() == null)
        return result(FxOutcome.NO_RULE, selected, null);
      double percent = match.value();
      if (!Double.isFinite(percent) || percent < 0 || percent >= 5)
        return new FxQuote(0, FxOutcome.INVALID, period.from(), match.rule().getName(),
            selected == null ? null : selected.status(), "FX markup must be finite and in [0, 5)");
      return new FxQuote(percent, FxOutcome.MATCHED, period.from(), match.rule().getName(),
          selected == null ? null : selected.status(), null);
    } catch (Exception e) {
      return result(FxOutcome.INVALID, selected, e.getMessage());
    }
  }

  private static FxQuote result(FxOutcome outcome, FxFeeConfig.Period period, String error) {
    return new FxQuote(0, outcome, period == null ? null : period.validFrom(), null,
        period == null ? null : period.status(), error);
  }

  private static String currencyClass(FxFeeConfig config, String currency) {
    if (config.currencyClasses() == null)
      return "";
    return config.currencyClasses().entrySet().stream().filter(e -> e.getValue().contains(currency))
        .map(Map.Entry::getKey).findFirst().orElse("");
  }

  private static boolean usesTier(List<FeeRule> rules) throws Exception {
    for (var rule : rules) {
      for (String text : List.of(rule.getCondition(), rule.getExpression()))
        if (FeeRuleEvaluator.expression(text).getUsedVariables().stream().anyMatch("tierAmount"::equalsIgnoreCase))
          return true;
    }
    return false;
  }

  /** Also validates captured record configurations, which need not originate from the YAML editor. */
  static List<String> validate(FxFeeConfig config) {
    List<String> errors = new ArrayList<>();
    if (config == null)
      return errors;
    if ((config.rules() == null) == (config.periods() == null))
      errors.add("fx: supply rules or periods exclusively");
    if (config.amountCurrency() != null && !config.amountCurrency().matches("[A-Z]{3}"))
      errors.add("fx.amountCurrency: expected three upper-case letters");
    Set<String> seen = new HashSet<>();
    if (config.currencyClasses() != null)
      config.currencyClasses().forEach((name, currencies) -> {
        if (currencies == null)
          errors.add("fx.currencyClasses: missing currencies for " + name);
        else
          for (String currency : currencies)
            if (currency == null || !currency.matches("[A-Z]{3}") || !seen.add(currency))
              errors.add("fx.currencyClasses: invalid or duplicate currency " + currency);
      });
    if (config.rules() != null)
      validateRules(config.rules(), config, "fx.", errors);
    if (config.periods() != null) {
      if (config.periods().isEmpty())
        errors.add("fx.periods: must not be empty");
      List<LocalDate[]> ranges = new ArrayList<>();
      for (var period : config.periods()) {
        if (period == null) {
          errors.add("fx.periods: null period");
          continue;
        }
        String prefix = "fx.periods[" + period.validFrom() + "]: ";
        if (period.status() == null || period.source() == null || period.source().isBlank())
          errors.add(prefix + "status and source are required");
        if (period.status() == FxFeeConfig.Status.ASSUMED && (period.note() == null || period.note().isBlank()))
          errors.add(prefix + "ASSUMED requires a note");
        try {
          LocalDate from = LocalDate.parse(period.validFrom());
          LocalDate to = period.validTo() == null ? LocalDate.MAX : LocalDate.parse(period.validTo());
          if (to.isBefore(from))
            errors.add(prefix + "reversed date range");
          for (var range : ranges)
            if (!to.isBefore(range[0]) && !from.isAfter(range[1]))
              errors.add(prefix + "overlapping periods");
          ranges.add(new LocalDate[] { from, to });
        } catch (Exception e) {
          errors.add(prefix + "invalid date");
        }
        validateRules(period.rules(), config, prefix, errors);
      }
    }
    return errors;
  }

  private static void validateRules(List<FeeRule> rules, FxFeeConfig config, String prefix, List<String> errors) {
    if (rules == null || rules.isEmpty()) {
      errors.add(prefix + "rules are required");
      return;
    }
    FeeModelYamlValidator.validateRulesSyntax(rules, prefix, errors);
    for (var rule : rules) {
      try {
        for (String text : List.of(rule.getCondition(), rule.getExpression())) {
          for (String variable : FeeRuleEvaluator.expression(text).getUsedVariables()) {
            if (!VARIABLES.contains(variable.toUpperCase(Locale.ROOT)))
              errors.add(prefix + "unknown variable " + variable);
            if (variable.equalsIgnoreCase("tierAmount") && config.amountCurrency() == null)
              errors.add(prefix + "tierAmount requires amountCurrency");
          }
        }
      } catch (Exception e) {
        errors.add(prefix + "invalid rule: " + e.getMessage());
      }
    }
  }
}
