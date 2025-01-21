package grafioschtrader.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.common.DataBusinessHelper;

public class AnnualisedPerformance {
  public final String securityCurrency;
  public final String mainCurrency;
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public final LocalDate dateFrom;
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public final LocalDate dateTo;

  public final List<LastYears> lastYears = new ArrayList<>();
  public final List<AnnualisedYears> annualisedYears = new ArrayList<>();

  public AnnualisedPerformance(String securityCurrency, String mainCurrency, LocalDate dateFrom, LocalDate dateTo) {
    this.securityCurrency = securityCurrency;
    this.mainCurrency = mainCurrency;
    this.dateFrom = dateFrom;
    this.dateTo = dateTo;
  }

  public static class LastYears {
    public final int year;
    public final double performanceYear;
    public final double performanceYearMC;

    public LastYears(int year, double performanceYear, double performanceYearMC) {
      this.year = year;
      this.performanceYear = performanceYear;
      this.performanceYearMC = performanceYearMC;
    }

    public double getPerformanceYear() {
      return DataBusinessHelper.roundStandard(performanceYear);
    }

    public double getPerformanceYearMC() {
      return DataBusinessHelper.roundStandard(performanceYearMC);
    }

  }

  public static class AnnualisedYears {
    public final int numberOfYears;
    public final double performanceAnnualised;
    public final double performanceAnnualisedMC;

    public AnnualisedYears(int numberOfYears, double performanceAnnualised, double performanceAnnualisedMC) {
      this.numberOfYears = numberOfYears;
      this.performanceAnnualised = performanceAnnualised;
      this.performanceAnnualisedMC = performanceAnnualisedMC;
    }

    public double getPerformanceAnnualised() {
      return DataBusinessHelper.roundStandard(performanceAnnualised);
    }

    public double getPerformanceAnnualisedMC() {
      return DataBusinessHelper.roundStandard(performanceAnnualisedMC);
    }

  }
}
