package grafioschtrader.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Counts the earlier trades of a security account within calendar periods, so that a fee rule can express allowances
 * such as "one free trade per quarter" (UBS key4) or "the first order per instrument and month is free" (Degiro Core
 * Selection). Commission rules are otherwise evaluated trade by trade without any memory.
 *
 * <p>
 * The fee comparison report and the historical replay share this class, so a model is calibrated against exactly the
 * counts the replay charges with. Counting is deliberately per security account: an allowance a broker grants per
 * banking relationship across several accounts is not modelled.
 * </p>
 *
 * <p>
 * One instance per report or replay run. Estimates only read; a trade is added with {@link #record} after it was
 * booked, and a repeated identity is ignored so a retried booking cannot count twice.
 * </p>
 */
public class FeeTradeCounter {

  /**
   * Earlier trades in the calendar period of the trade being priced; the trade itself is never included.
   *
   * @param month         trades of the account in the same calendar month
   * @param quarter       trades of the account in the same calendar quarter
   * @param year          trades of the account in the same calendar year
   * @param securityMonth trades of the same security in the account in the same calendar month
   */
  public record FeeTradeCounts(int month, int quarter, int year, int securityMonth) {
    public static final FeeTradeCounts NONE = new FeeTradeCounts(0, 0, 0, 0);
  }

  private record Trade(Integer idSecurity, LocalDate date) {
  }

  private final Map<Integer, List<Trade>> tradesByAccount = new HashMap<>();
  private final Set<String> identities = new HashSet<>();

  /**
   * Adds a booked trade.
   *
   * @param idSecurityaccount the security account the trade settled in
   * @param idSecurity        the traded instrument
   * @param date              the trade day
   * @param identity          a stable key of the trade, such as the transaction id or the replay fill id; a repeated
   *                          identity is ignored
   */
  public void record(Integer idSecurityaccount, Integer idSecurity, LocalDate date, String identity) {
    if (identity == null || identities.add(identity)) {
      tradesByAccount.computeIfAbsent(idSecurityaccount, _ -> new ArrayList<>()).add(new Trade(idSecurity, date));
    }
  }

  /**
   * Counts the recorded trades that lie in the calendar month, quarter and year of the given day, up to and including
   * that day. A trade recorded later on the same day therefore counts for the next trade of that day.
   *
   * @param idSecurityaccount the security account the new trade settles in
   * @param idSecurity        the instrument of the new trade
   * @param date              the day of the new trade
   * @return the counts, all zero when nothing was recorded for the account
   */
  public FeeTradeCounts counts(Integer idSecurityaccount, Integer idSecurity, LocalDate date) {
    int month = 0, quarter = 0, year = 0, securityMonth = 0;
    for (Trade t : tradesByAccount.getOrDefault(idSecurityaccount, List.of())) {
      if (t.date().getYear() != date.getYear() || t.date().isAfter(date)) {
        continue;
      }
      year++;
      if ((t.date().getMonthValue() - 1) / 3 == (date.getMonthValue() - 1) / 3) {
        quarter++;
      }
      if (t.date().getMonthValue() == date.getMonthValue()) {
        month++;
        if (t.idSecurity() != null && t.idSecurity().equals(idSecurity)) {
          securityMonth++;
        }
      }
    }
    return new FeeTradeCounts(month, quarter, year, securityMonth);
  }
}
