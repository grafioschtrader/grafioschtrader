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
 * Generates reports for calculating correlations between financial instruments. It supports both overall correlation
 * matrix calculation and rolling correlation analysis.
 */
public class CorrelationReport {

  private final JdbcTemplate jdbcTemplate;
  private final CurrencypairJpaRepository currencypairJpaRepository;
  private final HistoryquoteJpaRepository historyquoteJpaRepository;

  /**
   * Constructs a CorrelationReport instance.
   *
   * @param jdbcTemplate              The JdbcTemplate for database access.
   * @param currencypairJpaRepository Repository for currency pair data.
   * @param historyquoteJpaRepository Repository for historical quote data.
   */
  public CorrelationReport(JdbcTemplate jdbcTemplate, CurrencypairJpaRepository currencypairRepository,
      HistoryquoteJpaRepository historyquoteJpaRepository) {
    this.jdbcTemplate = jdbcTemplate;
    this.currencypairJpaRepository = currencypairRepository;
    this.historyquoteJpaRepository = historyquoteJpaRepository;
  }

  /**
   * Calculates the correlation matrix for a given set of security/currency instruments.
   *
   * @param correlationSet The set of parameters defining the correlation calculation.
   * @return A {@link CorrelationResult} containing the correlation matrix and related data.
   */
  public CorrelationResult calcCorrelationForMatrix(CorrelationSet correlationSet) {
    ClosePricesCurrencyClose closePrices = ReportHelper.loadCloseData(jdbcTemplate, currencypairJpaRepository,
        correlationSet.getSecuritycurrencyList(), correlationSet.getSamplingPeriod(), correlationSet.getDateFrom(),
        correlationSet.getDateTo(), correlationSet.isAdjustCurrency());

    if (closePrices.dateCloseTree.isEmpty()) {
      return createNonMatchingEODDataResult(correlationSet);
    }
    return calculateCorrelationMatrixInternal(correlationSet, closePrices);
  }

  /**
   * Internal method to calculate the correlation matrix once close prices are loaded.
   *
   * @param correlationSet The correlation set parameters.
   * @param closePrices    The loaded close prices and currency data.
   * @return A {@link CorrelationResult} with the calculated correlation matrix.
   */
  private CorrelationResult calculateCorrelationMatrixInternal(CorrelationSet correlationSet,
      ClosePricesCurrencyClose closePrices) {
    ReportHelper.adjustCloseToSameCurrency(correlationSet.getSecuritycurrencyList(), closePrices);
    double[][] percentageChanges = ReportHelper.transformToPercentageChange(closePrices.dateCloseTree,
        correlationSet.getSecuritycurrencyList().size());
    RealMatrix realMatrix = new Array2DRowRealMatrix(percentageChanges);

    CorrelationResult correlationResult = new CorrelationResult(closePrices.dateCloseTree.firstKey(),
        closePrices.dateCloseTree.lastKey());
    PearsonsCorrelation pearsonsCorrelation = new PearsonsCorrelation(realMatrix);
    RealMatrix correlationMatrix = pearsonsCorrelation.getCorrelationMatrix();

    CorrelationInstrument[] correlationInstruments = new CorrelationInstrument[correlationMatrix.getRowDimension()];
    correlationResult.correlationInstruments = correlationInstruments;

    for (int i = 0; i < correlationMatrix.getRowDimension(); i++) {
      correlationInstruments[i] = new CorrelationInstrument(
          correlationSet.getSecuritycurrencyList().get(i).getIdSecuritycurrency(), correlationMatrix.getRow(i));
    }
    return correlationResult;
  }

  /**
   * Creates a CorrelationResult indicating that no matching End-Of-Day (EOD) data was found. It populates the result
   * with the min/max available dates for each instrument.
   *
   * @param correlationSet The correlation set for which data was requested.
   * @return A {@link CorrelationResult} with min/max date information.
   */
  private CorrelationResult createNonMatchingEODDataResult(CorrelationSet correlationSet) {
    CorrelationResult correlationResult = new CorrelationResult(null, null);
    List<Integer> ids = correlationSet.getSecuritycurrencyList().stream().map(Securitycurrency::getIdSecuritycurrency)
        .collect(Collectors.toList());
    List<IMinMaxDateHistoryquote> minMaxDateList = historyquoteJpaRepository.getMinMaxDateByIdSecuritycurrencyIds(ids);

    Map<Integer, IMinMaxDateHistoryquote> minMaxDateMap = minMaxDateList.stream()
        .collect(Collectors.toMap(IMinMaxDateHistoryquote::getIdSecuritycurrency, Function.identity()));

    for (Securitycurrency<?> sc : correlationSet.getSecuritycurrencyList()) {
      IMinMaxDateHistoryquote mmdh = minMaxDateMap.get(sc.getIdSecuritycurrency());
      if (mmdh != null) {
        correlationResult.mmdhList
            .add(new MinMaxDateHistoryquote(sc.getIdSecuritycurrency(), mmdh.getMinDate(), mmdh.getMaxDate()));
      } else {
        correlationResult.mmdhList.add(new MinMaxDateHistoryquote(sc.getIdSecuritycurrency(), null, null));
      }
    }
    return correlationResult;
  }

  /**
   * Calculates rolling correlations for specified pairs of security/currency instruments.
   *
   * @param correlationSet   The set of parameters defining the correlation calculation, including the rolling window
   *                         size.
   * @param securityIdsPairs An array of pairs of security IDs for which to calculate rolling correlations.
   * @return A list of {@link CorrelationRollingResult}, each containing the rolling correlation for a pair.
   */
  public List<CorrelationRollingResult> getRollingCorrelation(CorrelationSet correlationSet,
      Integer[][] securityIdsPairs) {
    List<CorrelationRollingResult> rollingResultList = new ArrayList<>();
    LocalDate adjustedDateFrom = correlationSet.getDateFrom();
    if (adjustedDateFrom != null) {
      adjustedDateFrom = subtractRollingPeriodFromDateFrom(adjustedDateFrom, correlationSet.getSamplingPeriod(),
          correlationSet.getRolling());
    }

    ClosePricesCurrencyClose closePrices = ReportHelper.loadCloseData(jdbcTemplate, currencypairJpaRepository,
        correlationSet.getSecuritycurrencyList(), correlationSet.getSamplingPeriod(), adjustedDateFrom,
        correlationSet.getDateTo(), correlationSet.isAdjustCurrency());
    ReportHelper.adjustCloseToSameCurrency(correlationSet.getSecuritycurrencyList(), closePrices);

    double[][] percentageChanges = ReportHelper.transformToPercentageChange(closePrices.dateCloseTree,
        correlationSet.getSecuritycurrencyList().size());
    RealMatrix realMatrix = new Array2DRowRealMatrix(percentageChanges);

    RollingWindow rollingWindowConfig = new RollingWindow(correlationSet.getRolling());
    for (Integer[] securityIdPair : securityIdsPairs) {
      int colIndexSc1 = getColumnIndexOfMatrix(correlationSet, securityIdPair[0]);
      int colIndexSc2 = getColumnIndexOfMatrix(correlationSet, securityIdPair[1]);
      Double[] correlations = calculateRollingCovCorrBeta(realMatrix.getColumn(colIndexSc1),
          realMatrix.getColumn(colIndexSc2), rollingWindowConfig);

      LocalDate[] datesArray = closePrices.dateCloseTree.keySet()
          .toArray(new LocalDate[closePrices.dateCloseTree.keySet().size()]);
      if (rollingWindowConfig.removeEmptyRollingAtStart) {
        datesArray = Arrays.copyOfRange(datesArray, rollingWindowConfig.windowSize, datesArray.length);
      }
      rollingResultList
          .add(new CorrelationRollingResult(Arrays.asList(correlationSet.getSecuritycurrencyList().get(colIndexSc1),
              correlationSet.getSecuritycurrencyList().get(colIndexSc2)), datesArray, correlations));
    }
    return rollingResultList;
  }

  /**
   * Adjusts the 'dateFrom' by subtracting the rolling period to ensure enough data for the first rolling calculation.
   *
   * @param dateFrom           The original start date.
   * @param samplingPeriodType The sampling period (daily or monthly).
   * @param rollingWindowSize  The size of the rolling window.
   * @return The adjusted start date.
   */
  private LocalDate subtractRollingPeriodFromDateFrom(LocalDate dateFrom, SamplingPeriodType samplingPeriodType,
      byte rollingWindowSize) {
    switch (samplingPeriodType) {
    case DAILY_RETURNS:
      return dateFrom.minusDays(rollingWindowSize + 1);
    default: // MONTHLY_RETURNS
      return dateFrom.minusMonths(rollingWindowSize + 1);
    }
  }

  /**
   * Gets the column index in the data matrix for a given security/currency ID.
   *
   * @param correlationSet     The correlation set.
   * @param idSecuritycurrency The ID of the security/currency.
   * @return The column index, or -1 if not found.
   */
  private int getColumnIndexOfMatrix(CorrelationSet correlationSet, Integer idSecuritycurrency) {
    for (int i = 0; i < correlationSet.getSecuritycurrencyList().size(); i++) {
      if (correlationSet.getSecuritycurrencyList().get(i).getIdSecuritycurrency().equals(idSecuritycurrency)) {
        return i;
      }
    }
    return -1; // Should not happen if IDs are correct
  }

  /**
   * Calculates rolling covariance, correlation, or beta between two time series. This method implements both expanding
   * window and fixed-size rolling window calculations.
   *
   * @param seriesX       The first time series (e.g., percentage changes).
   * @param seriesY       The second time series (e.g., percentage changes).
   * @param rollingWindow The configuration for the rolling window calculation.
   * @return An array of Double values representing the calculated rolling statistic. The length of the array matches
   *         the input series. Initial values might be null if {@code removeEmptyRollingAtStart} is false and the window
   *         is not yet full.
   */
  private Double[] calculateRollingCovCorrBeta(double[] seriesX, double[] seriesY, RollingWindow rollingWindow) {
    int windowSize = rollingWindow.windowSize;
    int nElements = seriesX.length;
    int currentWindowElements = 0;

    // Determine population adjustment factor (n or n-1)
    int populationAdjustFactor = rollingWindow.usePopulationVariance ? windowSize : windowSize - 1;

    double averageX = 0.0;
    double sumOfSquaresX = 0.0;
    double deltaX = 0.0;
    double varianceX = 0.0;
    double stdDevX = 0.0;
    double sumX = 0.0;

    double averageY = 0.0;
    double sumOfSquaresY = 0.0;
    double deltaY = 0.0;
    double varianceY = 0.0;
    double stdDevY = 0.0;
    double sumY = 0.0;

    double sumXY = 0.0;
    double covariance = 0.0;

    Double[] rollingValues = new Double[nElements];

    if (rollingWindow.isExpanding) {
      for (int i = 0; i < nElements; ++i) {
        currentWindowElements++;
        // Update statistics for series X
        deltaX = seriesX[i] - averageX;
        averageX += deltaX / currentWindowElements;
        sumOfSquaresX += deltaX * (seriesX[i] - averageX); // Welford's algorithm component
        sumX += seriesX[i];

        // Update statistics for series Y
        deltaY = seriesY[i] - averageY;
        averageY += deltaY / currentWindowElements;
        sumOfSquaresY += deltaY * (seriesY[i] - averageY); // Welford's algorithm component
        sumY += seriesY[i];

        sumXY += seriesX[i] * seriesY[i];

        int currentPopulationAdjust = rollingWindow.usePopulationVariance ? currentWindowElements
            : currentWindowElements - 1;
        if (currentPopulationAdjust > 0) { // Avoid division by zero for very small windows
          varianceX = sumOfSquaresX / currentPopulationAdjust;
          varianceY = sumOfSquaresY / currentPopulationAdjust;
          if (rollingWindow.calculationType == CalculationType.CORRELATION) {
            stdDevX = Math.sqrt(varianceX);
            stdDevY = Math.sqrt(varianceY);
          }
          covariance = (sumXY - sumX * sumY / currentWindowElements) / currentPopulationAdjust;
        } else {
          varianceX = Double.NaN;
          varianceY = Double.NaN;
          stdDevX = Double.NaN;
          stdDevY = Double.NaN;
          covariance = Double.NaN;
        }

        if (i >= windowSize - 1) { // Start recording once the minimum window size is met
          assignRollingValue(rollingValues, i, covariance, varianceX, varianceY, stdDevX, stdDevY,
              rollingWindow.calculationType);
        } else {
          rollingValues[i] = null;
        }
      }
      return rollingValues; // For expanding window, result has same length as input
    }

    // Fixed-size rolling window
    for (int i = 0; i < windowSize; ++i) {
      currentWindowElements++;
      // Update statistics for series X
      deltaX = seriesX[i] - averageX;
      averageX += deltaX / currentWindowElements;
      sumOfSquaresX += deltaX * (seriesX[i] - averageX);
      sumX += seriesX[i];

      // Update statistics for series Y
      deltaY = seriesY[i] - averageY;
      averageY += deltaY / currentWindowElements;
      sumOfSquaresY += deltaY * (seriesY[i] - averageY);
      sumY += seriesY[i];

      sumXY += seriesX[i] * seriesY[i];
      rollingValues[i] = null; // Not enough data for full window yet
    }

    // First full window calculation
    if (populationAdjustFactor > 0) {
      varianceX = sumOfSquaresX / populationAdjustFactor;
      varianceY = sumOfSquaresY / populationAdjustFactor;
      if (rollingWindow.calculationType == CalculationType.CORRELATION) {
        stdDevX = Math.sqrt(varianceX);
        stdDevY = Math.sqrt(varianceY);
      }
      covariance = (sumXY - sumX * sumY / windowSize) / populationAdjustFactor;
    } else {
      varianceX = Double.NaN;
      varianceY = Double.NaN;
      stdDevX = Double.NaN;
      stdDevY = Double.NaN;
      covariance = Double.NaN;
    }
    assignRollingValue(rollingValues, windowSize - 1, covariance, varianceX, varianceY, stdDevX, stdDevY,
        rollingWindow.calculationType);

    // Subsequent rolling windows
    double valueXOld = 0.0;
    double valueYOld = 0.0;
    double averageXOld = 0.0;
    double averageYOld = 0.0;

    for (int i = windowSize; i < nElements; ++i) {
      valueXOld = seriesX[i - windowSize];
      valueYOld = seriesY[i - windowSize];

      // Update X statistics
      averageXOld = averageX;
      averageX += (seriesX[i] - valueXOld) / windowSize;
      sumOfSquaresX += (seriesX[i] - valueXOld) * (seriesX[i] - averageX + valueXOld - averageXOld);
      sumX += seriesX[i] - valueXOld;
      varianceX = sumOfSquaresX / populationAdjustFactor;
      if (rollingWindow.calculationType == CalculationType.CORRELATION) {
        stdDevX = Math.sqrt(varianceX);
      }

      // Update Y statistics
      averageYOld = averageY;
      averageY += (seriesY[i] - valueYOld) / windowSize;
      sumOfSquaresY += (seriesY[i] - valueYOld) * (seriesY[i] - averageY + valueYOld - averageYOld);
      sumY += seriesY[i] - valueYOld;
      varianceY = sumOfSquaresY / populationAdjustFactor;
      if (rollingWindow.calculationType == CalculationType.CORRELATION) {
        stdDevY = Math.sqrt(varianceY);
      }

      // Update Covariance
      sumXY += seriesX[i] * seriesY[i] - valueXOld * valueYOld;
      covariance = (sumXY - sumX * sumY / windowSize) / populationAdjustFactor;

      assignRollingValue(rollingValues, i, covariance, varianceX, varianceY, stdDevX, stdDevY,
          rollingWindow.calculationType);
    }

    return rollingWindow.removeEmptyRollingAtStart
        ? Arrays.copyOfRange(rollingValues, windowSize - 1, rollingValues.length)
        : rollingValues;
  }

  /**
   * Helper method to assign the calculated value based on the calculation type.
   */
  private void assignRollingValue(Double[] targetArray, int index, double covariance, double varianceX,
      double varianceY, double stdDevX, double stdDevY, CalculationType calcType) {
    switch (calcType) {
    case BETA:
      targetArray[index] = (varianceY != 0) ? covariance / varianceY : Double.NaN;
      break;
    case CORRELATION:
      targetArray[index] = (stdDevX * stdDevY != 0) ? covariance / (stdDevX * stdDevY) : Double.NaN;
      break;
    case COVARIANCE:
      targetArray[index] = covariance;
      break;
    default:
      targetArray[index] = Double.NaN; // Should not happen
      break;
    }
  }

  /**
   * Configuration for rolling window calculations.
   */
  private static class RollingWindow {
    final int windowSize;
    boolean removeEmptyRollingAtStart = true;
    boolean isExpanding = false;
    boolean usePopulationVariance = false; // if false, uses sample variance (n-1 denominator)
    CalculationType calculationType = CalculationType.CORRELATION;

    /**
     * Constructor for RollingWindow.
     *
     * @param windowSize The size of the rolling window.
     */
    public RollingWindow(byte windowSize) {
      this.windowSize = windowSize;
    }
  }

  /**
   * Defines the type of calculation to be performed in the rolling window (Beta, Correlation, Covariance).
   */
  private enum CalculationType {
    BETA, CORRELATION, COVARIANCE
  }

}