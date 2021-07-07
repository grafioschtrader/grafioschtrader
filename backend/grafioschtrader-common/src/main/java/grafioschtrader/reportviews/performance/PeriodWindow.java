package grafioschtrader.reportviews.performance;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;

/**
 * Normally used for period of single year or week.
 *
 * @author Hugo Graf
 *
 */
public class PeriodWindow {
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public LocalDate startDate;
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public LocalDate endDate;
  public Double gainPeriodMC;
  public List<PeriodStepMissingHoliday> periodStepList;

  public PeriodWindow(WeekYear weekYear, LocalDate startDate, LocalDate endDate) {
    periodStepList = new ArrayList<>(Collections.nCopies(
        weekYear == WeekYear.WM_WEEK ? PerformancePeriod.PERIOD_WEEK : PerformancePeriod.PERIOD_YEAR,
        new PeriodStepMissingHoliday(HolidayMissing.HM_NONE)));

    this.startDate = startDate;
    this.endDate = endDate;
  }

  public void addPeriodStep(WeekYear weekYear, LocalDate localDate, double externalCashTransferMC, double gainMC,
      double marginCloseGainMC, double cashBalanceMC, double securitiesMC, double totalBalanceMC, int missingDayCount) {
    int offset = (int) (weekYear == WeekYear.WM_WEEK ? ChronoUnit.DAYS.between(startDate, localDate)
        : ChronoUnit.MONTHS.between(startDate, localDate));
    periodStepList.set(offset, new PeriodStep(localDate, externalCashTransferMC, gainMC, marginCloseGainMC,
        cashBalanceMC, securitiesMC, totalBalanceMC, missingDayCount));
  }

  public void addPeriodStepHolidayMissing(WeekYear weekYear, LocalDate localDate, HolidayMissing holidayMissing) {
    if (weekYear == WeekYear.WM_WEEK) {
      int offset = (int) ChronoUnit.DAYS.between(startDate, localDate);
      periodStepList.set(offset, new PeriodStepMissingHoliday(holidayMissing));
    }
  }

  public void fillMissinGainPeriodMCByPeriodStep() {
    if (gainPeriodMC == null) {
      gainPeriodMC = 0.0;
      periodStepList.stream().filter(periodStep -> periodStep instanceof PeriodStep)
          .forEach(periodStep -> gainPeriodMC += ((PeriodStep) periodStep).gainMC);
    }

  }

}
