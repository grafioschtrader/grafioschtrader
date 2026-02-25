package grafioschtrader.evalex;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.data.EvaluationValue;
import com.ezylang.evalex.functions.AbstractFunction;
import com.ezylang.evalex.functions.FunctionParameter;
import com.ezylang.evalex.parser.Token;

import grafioschtrader.entities.Historyquote;
import grafioschtrader.ta.TaIndicatorData;
import grafioschtrader.ta.indicator.calc.RelativeStrengthIndex;

/**
 * Custom EvalEx function that computes the Relative Strength Index (RSI) for a given period. Usage in expressions:
 * {@code RSI(14)}, {@code RSI(7)}. The function returns the most recent RSI value (0-100) computed from historical
 * closing prices. Results are cached by period so that multiple RSI references with the same period compute only once.
 */
@FunctionParameter(name = "period")
public class RsiFunction extends AbstractFunction {

  private static final Logger log = LoggerFactory.getLogger(RsiFunction.class);

  private final List<Historyquote> historyquotes;
  private final Map<Integer, Double> cache = new HashMap<>();

  public RsiFunction(List<Historyquote> historyquotes) {
    this.historyquotes = historyquotes;
  }

  @Override
  public EvaluationValue evaluate(Expression expression, Token functionToken, EvaluationValue... parameterValues) {
    int period = parameterValues[0].getNumberValue().intValue();
    return EvaluationValue.numberValue(BigDecimal.valueOf(cache.computeIfAbsent(period, this::computeRsi)));
  }

  private double computeRsi(int period) {
    if (historyquotes.size() <= period) {
      log.warn("Insufficient history data for RSI({}): have {} quotes, need > {}", period, historyquotes.size(),
          period);
      return 0.0;
    }
    RelativeStrengthIndex rsi = new RelativeStrengthIndex(period, historyquotes.size());
    for (Historyquote hq : historyquotes) {
      rsi.addData(hq.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(), hq.getClose());
    }
    TaIndicatorData[] data = rsi.getTaIndicatorData();
    return data.length > 0 ? data[data.length - 1].value : 0.0;
  }
}
