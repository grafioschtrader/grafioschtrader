package grafioschtrader.reportviews.securitycurrency;

import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.temporal.ChronoUnit;

import grafioschtrader.types.InterestCalculationMethod;

/**
 * DOTO Finish it.
 */
public class BondAccruedInterest {

  /**
   *
   * @param faceValue The face value of the bond
   * @param interestRate The annual interest rate of the bond in percent
   * @param startDate The date of the last interest payment of the bond
   * @param endDate The date for which the accrued interest should be calculated
   * @param calcMethod The interest calculation method that was agreed for the bond
   */
  public static double calculateAccruedInterest(double faceValue, double interestRate, LocalDate startDate,
      LocalDate endDate, InterestCalculationMethod calcMethod) {
    // The number of interest days between the last interest date and the target
    // date
    int interestDays = 0;
    // The year fraction for calculating the accrued interest
    double yearFraction = 0.0;
    // The accrued interest of the bond
    double accruedInterest = 0.0;

    switch (calcMethod) {
    case GERMAN_30_360:
      yearFraction = getDaycount30E_360(startDate,  endDate, false) / 360.0;
      break;
    case ENGLISH_ACT_365:
      interestDays = (int) ChronoUnit.DAYS.between(startDate, endDate);
      yearFraction = interestDays / 365.0;
      break;
    case ACT_ACT:
      interestDays = (int) ChronoUnit.DAYS.between(startDate, endDate);
      yearFraction = (double) interestDays / Year.of(startDate.getYear()).length();
      break;
    case GERMAN:
      interestDays = (12 * (endDate.getYear() - startDate.getYear())
          + (endDate.getMonthValue() - startDate.getMonthValue())) * 30
          + (endDate.getDayOfMonth() - startDate.getDayOfMonth());
      yearFraction = interestDays / 360.0;
      break;
    case US:
      interestDays = (12 * (endDate.getYear() - startDate.getYear())
          + (endDate.getMonthValue() - startDate.getMonthValue())) * 30
          + (endDate.getDayOfMonth() - startDate.getDayOfMonth());
      // If the last interest date is the last day in February, it is set to 30
      if (startDate.getMonth() == Month.FEBRUARY
          && startDate.getDayOfMonth() == startDate.lengthOfMonth()) {
        interestDays += 30 - startDate.getDayOfMonth();
      }
      // If the target date is the last day in February, it is set to 30
      if (endDate.getMonth() == Month.FEBRUARY && endDate.getDayOfMonth() == endDate.lengthOfMonth()) {
        interestDays += 30 - endDate.getDayOfMonth();
      }
      yearFraction = interestDays / 360.0;
      break;
    default:
      // An invalid interest calculation method was specified
      throw new IllegalArgumentException("Invalid interest calculation method: " + calcMethod);
    }

    accruedInterest = yearFraction * faceValue * interestRate / 100.0;

    // The accrued interest is returned
    return accruedInterest;
  }

  private static double getDaycount30E_360(final LocalDate startDate, final LocalDate endDate,
      boolean isTreatEndDateAsTerminationDate) {
    if (startDate.isAfter(endDate)) {
      return -getDaycount30E_360(endDate, startDate, isTreatEndDateAsTerminationDate);
    }

    int startDateDay = startDate.getDayOfMonth();
    final int startDateMonth = startDate.getMonthValue();
    final int startDateYear = startDate.getYear();

    int endDateDay = endDate.getDayOfMonth();
    final int endDateMonth = endDate.getMonthValue();
    final int endDateYear = endDate.getYear();

    // Check if we have last day of February
    final boolean isStartDateLastDayOfFebruary = (startDateMonth == Month.FEBRUARY.getValue()
        && startDateDay == startDate.lengthOfMonth());
    final boolean isEndDateLastDayOfFebruary = (endDateMonth == Month.FEBRUARY.getValue()
        && endDateDay == endDate.lengthOfMonth());

    // Last day of February and 31st of a month are both treated as "30".
    if (isStartDateLastDayOfFebruary || startDateDay == 31) {
      startDateDay = 30;
    }
    if ((isEndDateLastDayOfFebruary && !isTreatEndDateAsTerminationDate) || endDateDay == 31) {
      endDateDay = 30;
    }

    return (endDateYear - startDateYear) * 360.0 + (endDateMonth - startDateMonth) * 30.0
        + (endDateDay - Math.min(startDateDay, 30.0));
  }




}
