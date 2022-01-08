package grafioschtrader.reports;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.springframework.jdbc.core.JdbcTemplate;

import grafioschtrader.dto.CorrelationResult;
import grafioschtrader.dto.CorrelationResult.CorrelationInstrument;
import grafioschtrader.dto.CorrelationResult.MinMaxDateHistoryquote;
import grafioschtrader.dto.CorrelationRollingResult;
import grafioschtrader.dto.IMinMaxDateHistoryquote;
import grafioschtrader.entities.CorrelationSet;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.reports.ReportHelper.ClosePricesCurrencyClose;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.types.SamplingPeriodType;

/**
 * Report for correlation report
 *
 */
public class CorrelationReport {

  private final JdbcTemplate jdbcTemplate;
  private final CurrencypairJpaRepository currencypairJpaRepository;
  private final HistoryquoteJpaRepository historyquoteJpaRepository;

  public CorrelationReport(JdbcTemplate jdbcTemplate, CurrencypairJpaRepository currencypairJpaRepository,
      HistoryquoteJpaRepository historyquoteJpaRepository) {
    this.jdbcTemplate = jdbcTemplate;
    this.currencypairJpaRepository = currencypairJpaRepository;
    this.historyquoteJpaRepository = historyquoteJpaRepository;
  }

  public CorrelationResult calcCorrelationForMatrix(CorrelationSet correlationSet) {
    ClosePricesCurrencyClose closePrices = ReportHelper.loadCloseData(jdbcTemplate, currencypairJpaRepository,
        correlationSet.getSecuritycurrencyList(), correlationSet.getSamplingPeriod(), correlationSet.getDateFrom(),
        correlationSet.getDateTo(), correlationSet.isAdjustCurrency());

    if (closePrices.dateCloseTree.isEmpty()) {
      return nonMatchingEODData(correlationSet);
    }
    return calcCorrelationForMatrix(correlationSet, closePrices);
  }

  private CorrelationResult calcCorrelationForMatrix(CorrelationSet correlationSet,
      ClosePricesCurrencyClose closePrices) {
    ReportHelper.adjustCloseToSameCurrency(correlationSet.getSecuritycurrencyList(), closePrices);
    RealMatrix realMatrix = new Array2DRowRealMatrix(ReportHelper.transformToPercentageChange(closePrices.dateCloseTree,
        correlationSet.getSecuritycurrencyList().size()));

    CorrelationResult cr = new CorrelationResult(closePrices.dateCloseTree.firstKey(),
        closePrices.dateCloseTree.lastKey());
    PearsonsCorrelation corrObj = new PearsonsCorrelation(realMatrix);
    RealMatrix corr = corrObj.getCorrelationMatrix();
    CorrelationInstrument[] ci = new CorrelationInstrument[corr.getRowDimension()];
    cr.correlationInstruments = ci;
    for (int i = 0; i < corr.getRowDimension(); i++) {
      ci[i] = new CorrelationInstrument(correlationSet.getSecuritycurrencyList().get(i).getIdSecuritycurrency(),
          corr.getRow(i));
    }
    return cr;
  }

  private CorrelationResult nonMatchingEODData(CorrelationSet correlationSet) {
    CorrelationResult cr = new CorrelationResult(null, null);
    List<IMinMaxDateHistoryquote> mmdhList = historyquoteJpaRepository
        .getMinMaxDateByIdSecuritycurrencyIds(correlationSet.getSecuritycurrencyList().stream()
            .map(Securitycurrency::getIdSecuritycurrency).collect(Collectors.toList()));
    Map<Integer, IMinMaxDateHistoryquote> mmdhMap = mmdhList.stream()
        .collect(Collectors.toMap(IMinMaxDateHistoryquote::getIdSecuritycurrency, Function.identity()));

    for (Securitycurrency<?> sc : correlationSet.getSecuritycurrencyList()) {
      IMinMaxDateHistoryquote mmdh = mmdhMap.get(sc.getIdSecuritycurrency());
      if (mmdh != null) {
        cr.mmdhList.add(new MinMaxDateHistoryquote(sc.getIdSecuritycurrency(), mmdh.getMinDate(), mmdh.getMaxDate()));
      } else {
        cr.mmdhList.add(new MinMaxDateHistoryquote(sc.getIdSecuritycurrency(), null, null));
      }
    }
    return cr;
  }

  public List<CorrelationRollingResult> getRollingCorrelation(CorrelationSet correlationSet,
      Integer[][] securityIdsPairs) {
    List<CorrelationRollingResult> crrList = new ArrayList<>();
    if (correlationSet.getDateFrom() != null) {
      correlationSet.setDateFrom(substractRollingPeriodFromDateFrom(correlationSet.getDateFrom(),
          correlationSet.getSamplingPeriod(), correlationSet.getRolling()));
    }
    ClosePricesCurrencyClose closePrices = ReportHelper.loadCloseData(jdbcTemplate, currencypairJpaRepository,
        correlationSet.getSecuritycurrencyList(), correlationSet.getSamplingPeriod(), correlationSet.getDateFrom(),
        correlationSet.getDateTo(), correlationSet.isAdjustCurrency());
    ReportHelper.adjustCloseToSameCurrency(correlationSet.getSecuritycurrencyList(), closePrices);

    RealMatrix realMatrix = new Array2DRowRealMatrix(ReportHelper.transformToPercentageChange(closePrices.dateCloseTree,
        correlationSet.getSecuritycurrencyList().size()));

    var rw = new RollingWindow(correlationSet.getRolling());
    for (int i = 0; i < securityIdsPairs.length; i++) {
      int sc1 = getColumnNoOfMatrix(correlationSet, securityIdsPairs[i][0]);
      int sc2 = getColumnNoOfMatrix(correlationSet, securityIdsPairs[i][1]);
      Double[] correlation = rollingCovCorrBeta(realMatrix.getColumn(sc1), realMatrix.getColumn(sc2), rw);
      LocalDate[] dates = closePrices.dateCloseTree.keySet()
          .toArray(new LocalDate[closePrices.dateCloseTree.keySet().size()]);
      dates = rw.removeEmptyRollingAtStart ? Arrays.copyOfRange(dates, rw.rolling, dates.length) : dates;
      crrList.add(new CorrelationRollingResult(Arrays.asList(correlationSet.getSecuritycurrencyList().get(sc1),
          correlationSet.getSecuritycurrencyList().get(sc2)), dates, correlation));
    }
    return crrList;
  }

  private LocalDate substractRollingPeriodFromDateFrom(LocalDate dateFrom, SamplingPeriodType samplingPeriodType,
      Byte rolling) {
    switch (samplingPeriodType) {
    case DAILY_RETURNS:
      return dateFrom.minusDays(rolling + 1);
    default: // Monthly
      return dateFrom.minusMonths(rolling + 1);
    }
  }

  private int getColumnNoOfMatrix(CorrelationSet correlationSet, Integer idSecuritycurrency) {
    for (int i = 0; i < correlationSet.getSecuritycurrencyList().size(); i++) {
      if (correlationSet.getSecuritycurrencyList().get(i).getIdSecuritycurrency().equals(idSecuritycurrency)) {
        return i;
      }
    }
    return -1;
  }

  private Double[] rollingCovCorrBeta(double[] x, double[] y, RollingWindow rollingWindow) {

//    if (a.pop) {
//      check_xy1(x, y, a.rolling); 
//    } else {
//      check_xy(x, y, a.rolling);  
//    }

    byte windowSize = rollingWindow.rolling;
    int n_xy = x.length;
    int w = 0;
    int pop_n = rollingWindow.pop ? windowSize : windowSize - 1;
    double avg_x = 0, sumsq_x = 0, delta_x = 0, var_x = 0, sd_x = 0, sum_x = 0;
    double avg_y = 0, sumsq_y = 0, delta_y = 0, var_y = 0, sd_y = 0, sum_y = 0;
    double sum_xy = 0;
    double cov = 0;

    Double[] rollxy = new Double[n_xy];

    if (rollingWindow.expanding) {
      for (int i = 0; i < n_xy; ++i) {
        ++w;
        delta_x = x[i] - avg_x;
        avg_x += delta_x / w;
        sumsq_x += delta_x * (x[i] - avg_x);
        sum_x += x[i];

        delta_y = y[i] - avg_y;
        avg_y += delta_y / w;
        sumsq_y += delta_y * (y[i] - avg_y);
        sum_y += y[i];

        sum_xy += x[i] * y[i];

        pop_n = (rollingWindow.pop ? w : w - 1);
        var_x = sumsq_x / pop_n;
        var_y = sumsq_y / pop_n;
        if (rollingWindow.ctype == CalcType.CORR) {
          sd_x = Math.sqrt(var_x);
          sd_y = Math.sqrt(var_y);
        }
        cov = (sum_xy - sum_x * sum_y / w) / pop_n;

        if (i >= rollingWindow.rolling - 1) {
          switch (rollingWindow.ctype) {
          case BETA:
            rollxy[i] = cov / var_y;
            break;
          case CORR:
            rollxy[i] = cov / (sd_x * sd_y);
            break;
          case COV:
            rollxy[i] = cov;
            break;
          default:
            break;
          }
        } else {
          rollxy[i] = null;
        }
      }
      return rollxy;
    }

    for (int i = 0; i < windowSize; ++i) {
      ++w;
      delta_x = x[i] - avg_x;
      avg_x += delta_x / w;
      sumsq_x += delta_x * (x[i] - avg_x);
      sum_x += x[i];

      delta_y = y[i] - avg_y;
      avg_y += delta_y / w;
      sumsq_y += delta_y * (y[i] - avg_y);
      sum_y += y[i];

      sum_xy += x[i] * y[i];

      rollxy[i] = null;
    }

    var_x = sumsq_x / pop_n;
    var_y = sumsq_y / pop_n;
    if (rollingWindow.ctype == CalcType.CORR) {
      sd_x = Math.sqrt(var_x);
      sd_y = Math.sqrt(var_y);
    }
    cov = (sum_xy - sum_x * sum_y / windowSize) / pop_n;

    switch (rollingWindow.ctype) {
    case BETA:
      rollxy[windowSize - 1] = cov / var_y;
      break;
    case CORR:
      rollxy[windowSize - 1] = cov / (sd_x * sd_y);
      break;
    case COV:
      rollxy[windowSize - 1] = cov;
      break;
    default:
      break;
    }

    // std dev terms
    double xi_old = x[0], xi = 0, avg_old_x = 0;
    double yi_old = y[0], yi = 0, avg_old_y = 0;

    for (int i = windowSize; i < n_xy; ++i) {

      // std dev of x
      xi = x[i];
      if (rollingWindow.ctype == CalcType.BETA || rollingWindow.ctype == CalcType.CORR) {
        avg_old_x = avg_x;
        avg_x = avg_old_x + (xi - xi_old) / windowSize;
        var_x += (xi - xi_old) * (xi - avg_x + xi_old - avg_old_x) / pop_n;
        if (rollingWindow.ctype == CalcType.CORR)
          sd_x = Math.sqrt(var_x);
      }

      // std dev of y
      yi = y[i];
      if (rollingWindow.ctype == CalcType.BETA || rollingWindow.ctype == CalcType.CORR) {
        avg_old_y = avg_y;
        avg_y = avg_old_y + (yi - yi_old) / windowSize;
        var_y += (yi - yi_old) * (yi - avg_y + yi_old - avg_old_y) / pop_n;
        if (rollingWindow.ctype == CalcType.CORR)
          sd_y = Math.sqrt(var_y);
      }

      // cov of x,y
      sum_xy += xi * yi - xi_old * yi_old;
      sum_x += xi - xi_old;
      sum_y += yi - yi_old;
      cov = (sum_xy - sum_x * sum_y / windowSize) / pop_n;

      switch (rollingWindow.ctype) {
      case BETA:
        rollxy[i] = cov / var_y;
        break;
      case CORR:
        rollxy[i] = cov / (sd_x * sd_y);
        break;
      case COV:
        rollxy[i] = cov;
        break;
      default:
        break;
      }

      xi_old = x[i - windowSize + 1];
      yi_old = y[i - windowSize + 1];
    }
    return rollingWindow.removeEmptyRollingAtStart
        ? Arrays.copyOfRange(rollxy, rollingWindow.rolling - 1, rollxy.length)
        : rollxy;
  }

  static class RollingWindow {
    public RollingWindow(byte rolling) {
      this.rolling = rolling;
    }

    boolean removeEmptyRollingAtStart = true;
    byte rolling;
    boolean expanding = false;
    boolean pop = false;
    CalcType ctype = CalcType.CORR;
  };

  enum CalcType {
    BETA, CORR, COV
  }

}
