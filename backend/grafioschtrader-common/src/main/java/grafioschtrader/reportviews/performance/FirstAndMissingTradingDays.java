package grafioschtrader.reportviews.performance;

import java.time.LocalDate;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.GlobalConstants;

public class FirstAndMissingTradingDays {
  public final int maxWeekLimit = GlobalConstants.PERFORMANCE_MAX_WEEK_LIMIT;
  public final int minIncludeMonthLimit = GlobalConstants.PERFORMANCE_MIN_INCLUDE_MONTH_LIMIT;
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public final LocalDate firstEverTradingDay;
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public final LocalDate secondEverTradingDay;
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public final LocalDate lastTradingDayOfLastYear;
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public final LocalDate secondLatestTradingDay;
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public final LocalDate latestTradingDay;
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public final LocalDate leatestPossibleTradingDay;

  @JsonIgnore
  public final Set<Date> allHolydays;
  @JsonIgnore
  public final Set<Date> missingQuoteDays;

  public FirstAndMissingTradingDays(LocalDate firstEverTradingDay, LocalDate secondEverTradingDay,
      LocalDate lastTradingDayOfLastYear, LocalDate leatestPossibleTradingDay, LocalDate latestTradingDay,
      LocalDate secondLatestTradingDay, Set<Date> allHolydays, Set<Date> missingQuoteDays) {
    super();
    this.firstEverTradingDay = firstEverTradingDay;
    this.secondEverTradingDay = secondEverTradingDay;
    this.lastTradingDayOfLastYear = lastTradingDayOfLastYear;

    this.leatestPossibleTradingDay = leatestPossibleTradingDay;
    this.latestTradingDay = latestTradingDay;
    this.secondLatestTradingDay = secondLatestTradingDay;

    this.allHolydays = allHolydays;
    this.missingQuoteDays = missingQuoteDays;
  }

  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public List<Date> getHolidayAndMissingQuoteDays() {
    Set<Date> combinedMissingQuoteDaysAndHoliday = new HashSet<>(allHolydays);
    combinedMissingQuoteDaysAndHoliday.addAll(missingQuoteDays);
    return combinedMissingQuoteDaysAndHoliday.stream().sorted().collect(Collectors.toList());
  }

  public boolean isMissingQuoteDayOrHoliday(Date date) {
    return allHolydays.contains(date) || missingQuoteDays.contains(date);
  }

  public boolean isHoliday(Date date) {
    return allHolydays.contains(date);
  }

  public boolean isMissingQuote(Date date) {
    return missingQuoteDays.contains(date);
  }

}
