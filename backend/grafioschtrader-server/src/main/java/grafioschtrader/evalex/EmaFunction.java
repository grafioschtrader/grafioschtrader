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
import grafioschtrader.ta.indicator.calc.ExponentialMovingAverage;

/**
 * Custom EvalEx function that computes the Exponential Moving Average (EMA) for a given period. Usage in expressions:
 * {@code EMA(50)}, {@code EMA(200)}. The function returns the most recent EMA value computed from historical closing
 * prices. Results are cached by period so that {@code EMA(50) > EMA(200)} computes each EMA only once per evaluation.
 */
@FunctionParameter(name = "period")
public class EmaFunction extends AbstractFunction {

  private static final Logger log = LoggerFactory.getLogger(EmaFunction.class);

  private final List<Historyquote> historyquotes;
  private final Map<Integer, Double> cache = new HashMap<>();

  public EmaFunction(List<Historyquote> historyquotes) {
    this.historyquotes = historyquotes;
  }

  @Override
  public EvaluationValue evaluate(Expression expression, Token functionToken, EvaluationValue... parameterValues) {
    int period = parameterValues[0].getNumberValue().intValue();
    return EvaluationValue.numberValue(BigDecimal.valueOf(cache.computeIfAbsent(period, this::computeEma)));
  }

  private double computeEma(int period) {
    if (historyquotes.size() <= period) {
      log.warn("Insufficient history data for EMA({}): have {} quotes, need > {}", period, historyquotes.size(),
          period);
      return 0.0;
    }
    ExponentialMovingAverage ema = new ExponentialMovingAverage(period, historyquotes.size());
    for (Historyquote hq : historyquotes) {
      ema.addData(hq.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(), hq.getClose());
    }
    TaIndicatorData[] data = ema.getTaIndicatorData();
    return data.length > 0 ? data[data.length - 1].value : 0.0;
  }
}
