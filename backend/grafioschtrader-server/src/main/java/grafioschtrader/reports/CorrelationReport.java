package grafioschtrader.reports;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.descriptive.MultivariateSummaryStatistics;
import org.springframework.jdbc.core.JdbcTemplate;

import grafioschtrader.dto.CorrelationResult;
import grafioschtrader.dto.CorrelationResult.CorrelationInstrument;
import grafioschtrader.dto.CorrelationRollingResult;
import grafioschtrader.entities.CorrelationSet;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.types.SamplingPeriodType;

/**
 * Report for correlation report
 *
 */
public class CorrelationReport {

  private final JdbcTemplate jdbcTemplate;

  public CorrelationReport(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public CorrelationResult calcCorrelationForMatrix(CorrelationSet correlationSet) {
    TreeMap<LocalDate, double[]> closeValuesMap = ReportHelper.loadCloseData(jdbcTemplate,
        correlationSet.getSecuritycurrencyList(), correlationSet.getSamplingPeriod(), correlationSet.getDateFrom(),
        correlationSet.getDateTo());
    RealMatrix realMatrix = new Array2DRowRealMatrix(
        ReportHelper.transformToPercentageChange(closeValuesMap, correlationSet.getSecuritycurrencyList().size()));

    CorrelationResult cr = new CorrelationResult(closeValuesMap.firstKey(), closeValuesMap.lastKey());
    PearsonsCorrelation corrObj = new PearsonsCorrelation(realMatrix);
    RealMatrix corr = corrObj.getCorrelationMatrix();
    CorrelationInstrument[] ci = new CorrelationInstrument[corr.getRowDimension()];
    cr.correlationInstruments = ci;
    for (int i = 0; i < corr.getRowDimension(); i++) {
      ci[i] = new CorrelationInstrument(correlationSet.getSecuritycurrencyList().get(i).getIdSecuritycurrency(),
          corr.getRow(i));
    }
    calcMultivariateSummary(realMatrix, ci, correlationSet.getSamplingPeriod(),
        correlationSet.getSecuritycurrencyList());
    return cr;
  }

  private void calcMultivariateSummary(RealMatrix realMatrix, CorrelationInstrument[] ci,
      SamplingPeriodType samplingPeriodType, List<Securitycurrency<?>> securitycurrencyList) {
    var mss = new MultivariateSummaryStatistics(realMatrix.getColumnDimension(), true);

    for (int i = 0; i < realMatrix.getRowDimension(); i++) {
      mss.addValue(realMatrix.getRow(i));
    }

    for (int i = 0; i < realMatrix.getColumnDimension(); i++) {
      ci[i].name = securitycurrencyList.get(i).getName();
      ci[i].maxPercentageChange = mss.getMax()[i];

      switch (samplingPeriodType) {
      case DAILY_RETURNS:
        ci[i].standardDeviationDay = mss.getStandardDeviation()[i];
        ci[i].standardDeviationMonth = mss.getStandardDeviation()[i] * Math.sqrt(21);
        ci[i].standardDeviationYear = mss.getStandardDeviation()[i] * Math.sqrt(252);
        break;
      case MONTHLY_RETURNS:
        ci[i].standardDeviationMonth = mss.getStandardDeviation()[i];
        ci[i].standardDeviationYear = mss.getStandardDeviation()[i] * Math.sqrt(12);
      }
    }
  }


  public List<CorrelationRollingResult> getRollingCorrelation(CorrelationSet correlationSet,
      Integer[][] securityIdsPairs) {
    List<CorrelationRollingResult> crrList = new ArrayList<>();
    if (correlationSet.getDateFrom() != null) {
      correlationSet.setDateFrom(substractRollingPeriodFromDateFrom(correlationSet.getDateFrom(),
          correlationSet.getSamplingPeriod(), correlationSet.getRolling()));
    }
    TreeMap<LocalDate, double[]> closeValuesMap = ReportHelper.loadCloseData(jdbcTemplate,
        correlationSet.getSecuritycurrencyList(), correlationSet.getSamplingPeriod(), correlationSet.getDateFrom(),
        correlationSet.getDateTo());
    RealMatrix realMatrix = new Array2DRowRealMatrix(
        ReportHelper.transformToPercentageChange(closeValuesMap, correlationSet.getSecuritycurrencyList().size()));

    var rw = new RollingWindow(correlationSet.getRolling());
    for (int i = 0; i < securityIdsPairs.length; i++) {
      int sc1 = getColumnNoOfMatrix(correlationSet, securityIdsPairs[i][0]);
      int sc2 = getColumnNoOfMatrix(correlationSet, securityIdsPairs[i][1]);
      Double[] correlation = rollingCovCorrBeta(realMatrix.getColumn(sc1), realMatrix.getColumn(sc2), rw);
      LocalDate[] dates = closeValuesMap.keySet().toArray(new LocalDate[closeValuesMap.keySet().size()]);
      dates = rw.removeEmptyRollingAtStart ? Arrays.copyOfRange(dates, rw.rolling, dates.length) : dates;
      crrList.add(new CorrelationRollingResult(Arrays.asList(correlationSet.getSecuritycurrencyList().get(sc1),
          correlationSet.getSecuritycurrencyList().get(sc2)), dates, correlation));
    }
//    checkWithA(0, correlationSet, realMatrix);
//    checkWithA(realMatrix.getRowDimension() - correlationSet.getRolling() - 2, correlationSet, realMatrix);
//    checkWithA(realMatrix.getRowDimension() - correlationSet.getRolling() - 1, correlationSet, realMatrix);
//    checkWithA(realMatrix.getRowDimension() - correlationSet.getRolling(), correlationSet, realMatrix);
    return crrList;
  }

  private void checkWithA(int startindex, CorrelationSet correlationSet, RealMatrix realMatrix) {
    RealMatrix rc = realMatrix.getSubMatrix(startindex, startindex + correlationSet.getRolling() - 1, 0, 1);
    PearsonsCorrelation corrObj = new PearsonsCorrelation(rc);
    RealMatrix result = corrObj.getCorrelationMatrix();
    System.out.println(result.getRow(0)[1]);
    System.out.println(result.getRow(1)[0]);
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
