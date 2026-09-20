package grafioschtrader.evalex;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  private final List<Historyquote> historyquotes;
  private final Map<Integer, Double> cache = new HashMap<>();

  public SmaFunction(List<Historyquote> historyquotes) {
    this.historyquotes = historyquotes;
  }

  @Override
  public EvaluationValue evaluate(Expression expression, Token functionToken, EvaluationValue... parameterValues) {
    int period = AlertExpressionSupport.period(parameterValues[0].getNumberValue());
    return EvaluationValue.numberValue(BigDecimal.valueOf(cache.computeIfAbsent(period, this::computeSma)));
  }

  private double computeSma(int period) {
    if (historyquotes.size() <= period) {
      throw new IndicatorUnavailableException("SMA", period, historyquotes.size());
    }
    SimpleMovingAverage sma = new SimpleMovingAverage(period, historyquotes.size());
    for (Historyquote hq : historyquotes) {
      sma.addData(hq.getDate(), hq.getClose());
    }
    TaIndicatorData[] data = sma.getTaIndicatorData();
    if (data.length == 0) {
      throw new IndicatorUnavailableException("SMA", period, historyquotes.size());
    }
    return data[data.length - 1].value;
  }
}
