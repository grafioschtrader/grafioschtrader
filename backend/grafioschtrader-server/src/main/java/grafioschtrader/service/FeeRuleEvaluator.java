package grafioschtrader.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.config.ExpressionConfiguration;
import com.ezylang.evalex.data.EvaluationValue;

import grafioschtrader.dto.FeeRule;

/** Shared period selection and first-match semantics for commission and FX tariffs. */
final class FeeRuleEvaluator {
  private static final ExpressionConfiguration CONFIG = ExpressionConfiguration.builder()
      .singleQuoteStringLiteralsAllowed(true).build();

  /** Both YAML quoting styles may contain either EvalEx string-literal style. */
  static Expression expression(String text) {
    return new Expression(text, CONFIG);
  }

  record Period(String from, String to, List<FeeRule> rules) {
  }

  record Match(Period period, FeeRule rule, Double value) {
  }

  static Period select(List<FeeRule> rules, List<Period> periods, LocalDate date) {
    if (periods == null || periods.isEmpty())
      return new Period(null, null, rules);
    return periods.stream().filter(
        p -> !date.isBefore(LocalDate.parse(p.from())) && (p.to() == null || !date.isAfter(LocalDate.parse(p.to()))))
        .findFirst().orElse(null);
  }

  static Match evaluate(Period period, Map<String, Object> variables) throws Exception {
    if (period == null)
      return new Match(null, null, null);
    for (FeeRule rule : period.rules()) {
      EvaluationValue condition = expression(rule.getCondition()).withValues(variables).evaluate();
      boolean matched = condition.isBooleanValue() ? condition.getBooleanValue()
          : condition.getNumberValue().signum() != 0;
      if (matched) {
        EvaluationValue value = expression(rule.getExpression()).withValues(variables).evaluate();
        if (!value.isNumberValue())
          throw new IllegalArgumentException("Rule result must be numeric");
        return new Match(period, rule, value.getNumberValue().doubleValue());
      }
    }
    return new Match(period, null, null);
  }
}
