package grafioschtrader.reportviews.performance;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a structured time window for performance analysis, typically spanning a single year or week.
 * 
 * <p>
 * This class organizes performance data into structured time periods, containing individual period steps
 * that represent days (for weekly windows) or months (for yearly windows). Each window provides aggregated
 * performance metrics and detailed breakdowns for comprehensive analysis and reporting.
 * </p>
 * 
 * <p>
 * The window structure supports:
 * </p>
 * <ul>
 * <li>Weekly analysis with 5 daily period steps (Monday to Friday)</li>
 * <li>Yearly analysis with 12 monthly period steps (January to December)</li>
 * <li>Holiday and missing data tracking within each period step</li>
 * <li>Automatic gain calculation and aggregation across all steps</li>
 * </ul>
 */
public class PeriodWindow {

  @Schema(description = "Start date of the performance analysis window")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate startDate;

  @Schema(description = "End date of the performance analysis window")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public LocalDate endDate;

  @Schema(description = "Total gain for this period window in main currency")
  public Double gainPeriodMC;

  @Schema(description = "List of period steps within this window - 5 for weekly analysis or 12 for yearly analysis")
  public List<PeriodStepMissingHoliday> periodStepList;

  /**
   * Constructs a new period window with the specified time boundaries and aggregation level.
   * 
   * <p>
   * Initializes the period step list with the appropriate number of empty steps based on the aggregation level: 5 steps
   * for weekly analysis (representing trading days Monday-Friday) or 12 steps for yearly analysis (representing months
   * January-December).
   * </p>
   * 
   * @param weekYear  the aggregation level determining the number and type of period steps
   * @param startDate the start date of this performance window
   * @param endDate   the end date of this performance window
   */
  public PeriodWindow(WeekYear weekYear, LocalDate startDate, LocalDate endDate) {
    periodStepList = new ArrayList<>(Collections.nCopies(
        weekYear == WeekYear.WM_WEEK ? PerformancePeriod.PERIOD_WEEK : PerformancePeriod.PERIOD_YEAR,
        new PeriodStepMissingHoliday(HolidayMissing.HM_NONE)));
    this.startDate = startDate;
    this.endDate = endDate;
  }

  /**
   * Adds a trading period step with complete financial performance data at the appropriate position.
   * 
   * <p>
   * Calculates the correct offset within the period step list based on the aggregation level:
   * </p>
   * <ul>
   * <li>For weekly analysis: offset = days between start date and step date</li>
   * <li>For yearly analysis: offset = months between start date and step date</li>
   * </ul>
   * 
   * @param weekYear               the aggregation level for offset calculation
   * @param localDate              the date of this period step
   * @param externalCashTransferMC external cash transfers in main currency
   * @param gainMC                 investment gains in main currency
   * @param marginCloseGainMC      realized margin gains in main currency
   * @param cashBalanceMC          total cash balance in main currency
   * @param securitiesMC           securities market value in main currency
   * @param totalBalanceMC         total portfolio balance in main currency
   * @param missingDayCount        number of days with missing data in this step
   */
  public void addPeriodStep(WeekYear weekYear, LocalDate localDate, double externalCashTransferMC, double gainMC,
      double marginCloseGainMC, double cashBalanceMC, double securitiesMC, double totalBalanceMC, int missingDayCount) {
    int offset = (int) (weekYear == WeekYear.WM_WEEK ? ChronoUnit.DAYS.between(startDate, localDate)
        : ChronoUnit.MONTHS.between(startDate, localDate));
    periodStepList.set(offset, new PeriodStep(localDate, externalCashTransferMC, gainMC, marginCloseGainMC,
        cashBalanceMC, securitiesMC, totalBalanceMC, missingDayCount));
  }

  /**
   * Adds a holiday or missing data period step at the appropriate position within the window.
   * 
   * <p>
   * Only processes weekly aggregation periods, as holiday tracking is more granular and relevant for daily analysis.
   * For yearly aggregation, holidays are typically aggregated within monthly summaries.
   * </p>
   * 
   * @param weekYear       the aggregation level (only processes WM_WEEK)
   * @param localDate      the date of the holiday or missing data
   * @param holidayMissing the classification of this non-trading period
   */
  public void addPeriodStepHolidayMissing(WeekYear weekYear, LocalDate localDate, HolidayMissing holidayMissing) {
    if (weekYear == WeekYear.WM_WEEK) {
      int offset = (int) ChronoUnit.DAYS.between(startDate, localDate);
      periodStepList.set(offset, new PeriodStepMissingHoliday(holidayMissing));
    }
  }

  /**
   * Calculates and sets the total period gain by aggregating gains from all trading period steps.
   * 
   * <p>
   * This method is called when the period gain has not been calculated through other means. It iterates through all
   * period steps, identifies trading days (PeriodStep instances), and sums their individual gains to produce the total
   * period performance.
   * </p>
   * 
   * <p>
   * The calculation only considers actual trading period steps and ignores holidays or missing data steps to ensure
   * accurate performance measurement.
   * </p>
   */
  public void fillMissinGainPeriodMCByPeriodStep() {
    if (gainPeriodMC == null) {
      gainPeriodMC = 0.0;
      periodStepList.stream().filter(periodStep -> periodStep instanceof PeriodStep)
          .forEach(periodStep -> gainPeriodMC += ((PeriodStep) periodStep).gainMC);
    }
  }

}
