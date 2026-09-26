package grafioschtrader.common;

import java.time.LocalDate;
import java.util.List;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.SecaccountTradingPeriod;
import grafioschtrader.entities.Securityaccount;

/**
 * Whether a security account may trade an instrument type, according to its {@link SecaccountTradingPeriod} rows. The
 * transaction write path, the account choice of a simulation and the account options of the algo dialogs all ask this
 * one question, so it is answered in one place and the three cannot come to different conclusions.
 *
 * <p>
 * The rule: an account without any trading period may trade everything (backward compatibility). Otherwise at least one
 * period must name the special investment instrument of the asset class exactly, and either leave the category open
 * (null) or name the same one.
 * </p>
 */
public final class SecurityaccountTradingEligibility {

  private SecurityaccountTradingEligibility() {
  }

  /**
   * Whether the account may trade the instrument type on a given day, which is the check of the transaction write path.
   *
   * @param securityaccount the account in question
   * @param assetclass      the asset class of the traded instrument
   * @param date            the day of the trade
   * @return true when no trading period exists or one matches the type and contains the day
   */
  public static boolean allows(Securityaccount securityaccount, Assetclass assetclass, LocalDate date) {
    List<SecaccountTradingPeriod> periods = securityaccount.getTradingPeriods();
    return periods == null || periods.isEmpty()
        || periods.stream().anyMatch(period -> matchesType(period, assetclass) && contains(period, date));
  }

  /**
   * Whether the account may trade the instrument type on any day at all. The algo dialogs ask this rather than
   * {@link #allows}, because a simulation replays past years, so a period that has already ended still counts.
   *
   * @param securityaccount the account in question
   * @param assetclass      the asset class of the instrument, or null when the type is not known, in which case every
   *                        account qualifies
   * @return true when no trading period exists or one matches the type
   */
  public static boolean allowsEver(Securityaccount securityaccount, Assetclass assetclass) {
    List<SecaccountTradingPeriod> periods = securityaccount.getTradingPeriods();
    return assetclass == null || periods == null || periods.isEmpty()
        || periods.stream().anyMatch(period -> matchesType(period, assetclass));
  }

  /**
   * What a simulation may book on the account on a day: the trading periods allow the type, and the account had not
   * been closed yet.
   *
   * @param securityaccount the account in question
   * @param assetclass      the asset class of the traded instrument
   * @param date            the day of the fill
   * @return true when a replayed order may settle there
   */
  public static boolean tradable(Securityaccount securityaccount, Assetclass assetclass, LocalDate date) {
    return (securityaccount.getActiveToDate() == null || !date.isAfter(securityaccount.getActiveToDate()))
        && allows(securityaccount, assetclass, date);
  }

  private static boolean matchesType(SecaccountTradingPeriod period, Assetclass assetclass) {
    return (period.getCategoryType() == null || period.getCategoryType() == assetclass.getCategoryType())
        && period.getSpecInvestInstrument() == assetclass.getSpecialInvestmentInstrument();
  }

  private static boolean contains(SecaccountTradingPeriod period, LocalDate date) {
    return (period.getDateFrom() == null || !date.isBefore(period.getDateFrom()))
        && (period.getDateTo() == null || !date.isAfter(period.getDateTo()));
  }
}
