package grafioschtrader.calendar.rule;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Determines how a holiday that falls on a Saturday or Sunday is transferred to a working day.
 *
 * <p>
 * Exchanges differ in this respect and the choice has a direct effect on the generated calendar. US exchanges move a
 * holiday to the nearest weekday, so 4 July on a Saturday is observed on the preceding Friday. UK exchanges append a
 * substitute bank holiday on the following Monday instead. Most continental European exchanges do not transfer at all
 * -- a holiday on a weekend is simply lost, because the exchange is closed on that weekend anyway.
 * </p>
 */
public enum Observance {

  /**
   * No transfer. A holiday falling on a weekend produces no closure on a working day, which is the behaviour of most
   * continental European exchanges.
   */
  NONE {
    @Override
    public LocalDate apply(LocalDate date) {
      return date;
    }
  },

  /**
   * Saturday moves to the preceding Friday, Sunday to the following Monday. Used by the US exchanges.
   */
  NEAREST_WEEKDAY {
    @Override
    public LocalDate apply(LocalDate date) {
      return switch (date.getDayOfWeek()) {
      case SATURDAY -> date.minusDays(1);
      case SUNDAY -> date.plusDays(1);
      default -> date;
      };
    }
  },

  /**
   * Sunday moves to the following Monday, Saturday is not transferred at all and the closure is lost.
   *
   * <p>
   * This is the exception the US exchanges make for New Year's Day: every other holiday falling on a Saturday is
   * observed on the preceding Friday, but the exchanges do not close on 31 December for a New Year that falls on a
   * Saturday. Modelled by returning the Saturday unchanged, which the weekend filter in
   * {@link HolidayRule#resolve(int)} then discards.
   * </p>
   */
  SUNDAY_TO_MONDAY {
    @Override
    public LocalDate apply(LocalDate date) {
      return date.getDayOfWeek() == DayOfWeek.SUNDAY ? date.plusDays(1) : date;
    }
  },

  /**
   * Both Saturday and Sunday move to the following Monday. This is the UK substitute bank holiday rule and is also used
   * in Australia and New Zealand.
   */
  NEXT_MONDAY {
    @Override
    public LocalDate apply(LocalDate date) {
      return switch (date.getDayOfWeek()) {
      case SATURDAY -> date.plusDays(2);
      case SUNDAY -> date.plusDays(1);
      default -> date;
      };
    }
  },

  /**
   * Moves forward to the next weekday, which for a weekend date is always the following Monday. It differs from
   * {@link #NEXT_MONDAY} only in intent: it is used where a holiday is defined as "the first working day on or after"
   * a given date.
   */
  NEXT_WEEKDAY {
    @Override
    public LocalDate apply(LocalDate date) {
      LocalDate result = date;
      while (result.getDayOfWeek() == DayOfWeek.SATURDAY || result.getDayOfWeek() == DayOfWeek.SUNDAY) {
        result = result.plusDays(1);
      }
      return result;
    }
  };

  /**
   * Applies the transfer rule to the given date.
   *
   * @param date the nominal date of the holiday, which may fall on a weekend
   * @return the date on which the closure is actually observed, never null
   */
  public abstract LocalDate apply(LocalDate date);
}
