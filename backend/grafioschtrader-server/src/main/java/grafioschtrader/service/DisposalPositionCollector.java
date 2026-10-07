package grafioschtrader.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.service.FeeTradeCounter.FeeTradeCounts;
import grafioschtrader.types.TransactionType;

/**
 * Collects, while a report walks the booked transactions, what the disposal cost estimate needs to know beyond the
 * merged position: how the units of a security are spread over the security accounts, into which cash account each
 * account settles the security, and the trades that count for the trade allowances of a fee model.
 *
 * <p>
 * The position reports merge the same security held in several security accounts into one position, while fee models
 * are set per security account. The estimate therefore splits a position by the units each account holds. Units are
 * brought to the split basis of the present with {@link Securitysplit}, so accounts that bought before and after a
 * split are comparable. The settlement cash account of an account is the cash account of its latest buy or sell of the
 * security, which is where a sale would most likely be booked.
 * </p>
 *
 * <p>
 * One instance per report. Margin instruments are not collected, because their disposal costs are not estimated.
 * </p>
 */
public class DisposalPositionCollector {

  /** Units of one security in one security account and the currency of the cash account it settles into. */
  private static final class Holding {
    double units;
    String settlementCurrency;
  }

  /**
   * Share of a position that one security account would sell.
   *
   * @param idSecurityaccount  the security account
   * @param units              the units of the position sold from this account
   * @param settlementCurrency currency of the cash account the sale settles into, null when unknown
   */
  public record Allocation(Integer idSecurityaccount, double units, String settlementCurrency) {
  }

  private static final double UNITS_EPSILON = 1e-9;

  private final Map<Integer, Map<Integer, Holding>> holdingsBySecurity = new HashMap<>();
  private final FeeTradeCounter tradeCounter = new FeeTradeCounter();

  /**
   * Takes over a booked transaction. Only buys and sells of non-margin securities are relevant; everything else is
   * ignored. Transactions of one security account should arrive in chronological order, so that the latest cash account
   * wins.
   *
   * @param transaction      the booked transaction
   * @param securitysplitMap the splits by security id, may be null when no split is known
   */
  public void accept(Transaction transaction, Map<Integer, List<Securitysplit>> securitysplitMap) {
    Security security = transaction.getSecurity();
    TransactionType type = transaction.getTransactionType();
    if (security == null || security.isMarginInstrument() || transaction.getIdSecurityaccount() == null
        || transaction.getUnits() == null || (type != TransactionType.ACCUMULATE && type != TransactionType.REDUCE)) {
      return;
    }
    LocalDate date = transaction.getTransactionDate();
    double splitFactor = securitysplitMap == null ? 1.0
        : Securitysplit.calcSplitFatorForFromDate(security.getIdSecuritycurrency(), date, securitysplitMap);
    Holding holding = holdingsBySecurity.computeIfAbsent(security.getIdSecuritycurrency(), _ -> new LinkedHashMap<>())
        .computeIfAbsent(transaction.getIdSecurityaccount(), _ -> new Holding());
    holding.units += (type == TransactionType.ACCUMULATE ? 1 : -1) * transaction.getUnits() * splitFactor;
    if (transaction.getCashaccount() != null) {
      holding.settlementCurrency = transaction.getCashaccount().getCurrency();
    }
    tradeCounter.record(transaction.getIdSecurityaccount(), security.getIdSecuritycurrency(), date,
        transaction.getIdTransaction() == null ? null : String.valueOf(transaction.getIdTransaction()));
  }

  /**
   * Splits the units of a merged position over the security accounts in proportion to the units each account holds.
   *
   * @param idSecurity         the security of the position
   * @param units              the units of the merged position
   * @param fallbackAccount    the account taking all units when none was collected, for example the first account of
   *                           the position; may be null
   * @param fallbackSettlement the settlement currency used with the fallback account
   * @return the shares of the accounts with a positive holding, empty when no account is known
   */
  public List<Allocation> allocate(Integer idSecurity, double units, Integer fallbackAccount,
      String fallbackSettlement) {
    List<Allocation> allocations = new ArrayList<>();
    Map<Integer, Holding> holdings = holdingsBySecurity.getOrDefault(idSecurity, Map.of());
    double total = holdings.values().stream().filter(h -> h.units > UNITS_EPSILON).mapToDouble(h -> h.units).sum();
    if (total > UNITS_EPSILON) {
      holdings.forEach((idSecurityaccount, holding) -> {
        if (holding.units > UNITS_EPSILON) {
          allocations.add(new Allocation(idSecurityaccount, units * holding.units / total, holding.settlementCurrency));
        }
      });
    } else if (fallbackAccount != null) {
      allocations.add(new Allocation(fallbackAccount, units, fallbackSettlement));
    }
    return allocations;
  }

  /**
   * @return the earlier trades of the account in the calendar periods of the given day, for the trade allowances of a
   *         fee model
   */
  public FeeTradeCounts tradeCounts(Integer idSecurityaccount, Integer idSecurity, LocalDate date) {
    return tradeCounter.counts(idSecurityaccount, idSecurity, date);
  }
}
