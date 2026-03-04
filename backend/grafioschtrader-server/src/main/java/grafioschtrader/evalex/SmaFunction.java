package grafioschtrader.evalex;

import java.math.BigDecimal;
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
import grafioschtrader.ta.indicator.calc.SimpleMovingAverage;

/**
 * Custom EvalEx function that computes the Simple Moving Average (SMA) for a given period. Usage in expressions:
 * {@code SMA(200)}, {@code SMA(50)}. The function returns the most recent SMA value computed from historical closing
 * prices. Results are cached by period so that {@code SMA(200) > SMA(50)} computes each SMA only once per evaluation.
 */
@FunctionParameter(name = "period")
public class SmaFunction extends AbstractFunction {

  private static final Logger log = LoggerFactory.getLogger(SmaFunction.class);

  private final List<Historyquote> historyquotes;
  private final Map<Integer, Double> cache = new HashMap<>();

  public SmaFunction(List<Historyquote> historyquotes) {
    this.historyquotes = historyquotes;
  }

  @Override
  public EvaluationValue evaluate(Expression expression, Token functionToken, EvaluationValue... parameterValues) {
    int period = parameterValues[0].getNumberValue().intValue();
    return EvaluationValue.numberValue(BigDecimal.valueOf(cache.computeIfAbsent(period, this::computeSma)));
  }

  private double computeSma(int period) {
    if (historyquotes.size() <= period) {
      log.warn("Insufficient history data for SMA({}): have {} quotes, need > {}", period, historyquotes.size(),
          period);
      return 0.0;
    }
    SimpleMovingAverage sma = new SimpleMovingAverage(period, historyquotes.size());
    for (Historyquote hq : historyquotes) {
      sma.addData(hq.getDate(), hq.getClose());
    }
    TaIndicatorData[] data = sma.getTaIndicatorData();
    return data.length > 0 ? data[data.length - 1].value : 0.0;
  }
}
