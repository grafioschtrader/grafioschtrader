package grafioschtrader.service;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.*;
import grafioschtrader.instrument.SecurityCalcService;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.repository.*;
import grafioschtrader.types.TransactionType;

/**
 * Historical closing equity and gross exposure. Each account and each margin opening remains separate until valuation
 * is complete. No live quote or missing-data substitution is permitted.
 */
@Service
@Transactional(readOnly = true)
public class AlgoHistoricalValuationService {
  @Autowired
  private SimulationSourceRepository source;
  @Autowired
  private TenantJpaRepository tenants;
  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdings;
  @Autowired
  private HoldCashaccountBalanceJpaRepository balances;
  @Autowired
  private HistoryquoteJpaRepository quotes;
  @Autowired
  private CurrencypairJpaRepository currencies;
  @Autowired
  private SecuritysplitJpaRepository splits;
  @Autowired
  private SecurityCalcService calculator;
  @Autowired
  private GlobalparametersService parameters;

  /** Values are in security currency, except exposure/equity totals on the snapshot. */
  public record Position(String key, Security security, Integer idSecurityaccount, double units, double closingValue,
      double grossExposure, Integer idCashaccount) {
  }

  /** Detached calculation result retaining the source accounts for opening cash distribution. */
  public record Snapshot(String currency, List<Position> positions, Map<Integer, Double> cashBalances,
      Map<String, Double> fx, double equity, double grossExposure, List<String> errors) {
    public void requireAvailable() {
      if (!errors.isEmpty())
        throw invalid("algo.valuation.unavailable", String.join(", ", errors));
    }
  }

  /** Field of a validation error that concerns the date an allocation is reconstructed from. */
  public static final String FIELD_REFERENCE_DATE = "reference.date";
  /** Field of a validation error that concerns the opening date of a simulation environment. */
  public static final String FIELD_SIMULATION_START_DATE = "simulation.start.date";
  /** Field of a validation error that concerns the chosen initialization mode. */
  public static final String FIELD_INITIALIZATION_MODE = "initialization.mode";

  /** A completed application day is required independently of any strategy provenance. */
  public static void validateDate(LocalDate date) {
    validateDate(date, FIELD_REFERENCE_DATE);
  }

  /**
   * A completed application day is required independently of any strategy provenance.
   *
   * @param date  the day whose closing state is requested
   * @param field the form field the error is reported against, so the dialog that asked for the date highlights it
   */
  public static void validateDate(LocalDate date, String field) {
    if (date == null || !date.isBefore(LocalDate.now()))
      throw invalid(field, "algo.completed.date.required", "");
  }

  /** Reports against the reference date, which is the field of every allocation error. */
  public static DataViolationException invalid(String key, Object detail) {
    return invalid(FIELD_REFERENCE_DATE, key, detail);
  }

  /**
   * Builds the validation error of the historical valuation layer.
   *
   * @param field  dot-separated property key of the form field to highlight; a simulation dialog has no reference date,
   *               so it reports against its own opening date or mode instead
   * @param key    message key of the explanation
   * @param detail single argument of that message
   * @return the exception to throw
   */
  public static DataViolationException invalid(String field, String key, Object detail) {
    return new DataViolationException(field, key, new Object[] { detail });
  }

  /** Supplied observations must belong to the requested closing date or an earlier observation. */
  @FunctionalInterface
  public interface ClosingPrices {
    Double close(Integer idSecuritycurrency, LocalDate asOf);

    /** Live callers retain their configured hierarchy; replay supplies frozen effective weights. */
    default AlgoReplayAllocation allocation() {
      return null;
    }

    /** Only simulation observations exclude instruments from trading. */
    default boolean tradingExcluded(Security security) {
      return false;
    }

    /** Volume of the latest observation on or before the requested day; no older non-null substitution. */
    default Long volume(Integer idSecuritycurrency, LocalDate asOf) {
      return null;
    }

    /** Earned income that belongs to equity but cannot yet fund an order, grouped by currency. */
    default Map<String, Double> receivables(LocalDate asOf) {
      return Map.of();
    }
  }

  /** Reads the opening ledger using the historical repository as its only price source. */
  public Snapshot value(Integer idTenant, LocalDate date) {
    return value(idTenant, date, (id, asOf) -> {
      var result = quotes.getIdDateCloseByIdsAndDate(List.of(id), asOf);
      return result.isEmpty() ? null : result.getFirst().getClose();
    });
  }

  /** Values the ledger against supplied observations; missing data never triggers a fallback lookup. */
  public Snapshot value(Integer idTenant, LocalDate date, ClosingPrices market) {
    return value(idTenant, date, market, Set.of());
  }

  /** Includes conversion rates needed for proposed entries in currencies with no existing holdings or cash. */
  public Snapshot value(Integer idTenant, LocalDate date, ClosingPrices market, Set<String> additionalCurrencies) {
    validateDate(date);
    var references = new References(idTenant, Map.of());
    return value(date, market, additionalCurrencies, references, source.transactions(idTenant, date.plusDays(1)),
        holdings.findOpenPositionsAtDate(idTenant, date), balances.findCashBalancesAtDate(idTenant, date), false);
  }

  /**
   * Computes reporting equity after all bookings have finished. Holding periods and cash balances are each read once;
   * settlement-account attribution is unnecessary for these totals. Margin lots retain their existing calculation,
   * using one bounded ledger read for the entire series. Prices remain bounded by each observation date.
   */
  public Map<LocalDate, Snapshot> valueSeries(Integer idTenant, Collection<LocalDate> dates, ClosingPrices market,
      Map<Integer, Security> securities) {
    TreeSet<LocalDate> ordered = new TreeSet<>(dates);
    if (ordered.isEmpty())
      return Map.of();
    ordered.forEach(AlgoHistoricalValuationService::validateDate);
    var references = new References(idTenant, securities);
    var positionPeriods = new Periods<>(holdings.findPositionsInPeriod(idTenant, ordered.first(), ordered.last()),
        h -> h.getHssk().getFromHoldDate());
    var cashPeriods = new Periods<>(balances.findBalancesInPeriod(idTenant, ordered.first(), ordered.last()),
        h -> h.getIdEm().getFromHoldDate());
    List<Transaction> marginLedger = securities.values().stream().anyMatch(Security::isMarginInstrument)
        ? source.transactions(idTenant, ordered.last().plusDays(1)).stream()
            .filter(t -> t.getSecurity() != null && t.getSecurity().isMarginInstrument()).toList()
        : List.of();
    Map<LocalDate, Snapshot> result = new LinkedHashMap<>();
    for (LocalDate date : ordered) {
      List<Transaction> marginThroughDate = marginLedger.stream()
          .filter(t -> !t.getTransactionDateAsLocalDate().isAfter(date)).toList();
      result.put(date, value(date, market, Set.of(), references, marginThroughDate, positionPeriods.at(date),
          cashPeriods.at(date), true));
    }
    return result;
  }

  private Snapshot value(LocalDate date, ClosingPrices market, Set<String> additionalCurrencies, References references,
      List<Transaction> transactions, Collection<HoldSecurityaccountSecurity> holdingRows,
      Collection<HoldCashaccountBalance> cashRows, boolean aggregate) {
    Tenant tenant = references.tenant;
    List<Cashaccount> accounts = references.accounts;
    Map<Integer, Double> cash = new LinkedHashMap<>();
    accounts.forEach(a -> cash.put(a.getId(), 0.0));
    cashRows.forEach(b -> cash.put(b.getIdEm().getIdSecuritycashAccount(), b.getBalance()));
    Map<String, List<Transaction>> groups = transactions.stream()
        .filter(t -> t.getSecurity() != null && t.getIdSecurityaccount() != null).collect(Collectors.groupingBy(
            t -> t.getIdSecurityaccount() + ":" + t.getSecurity().getId(), TreeMap::new, Collectors.toList()));
    Map<String, HoldSecurityaccountSecurity> held = new HashMap<>();
    holdingRows
        .forEach(h -> held.put(h.getHssk().getIdSecuritycashAccount() + ":" + h.getHssk().getIdSecuritycurrency(), h));
    if (aggregate) {
      held.forEach((key, h) -> {
        Security security = Objects.requireNonNull(references.securities.get(h.getHssk().getIdSecuritycurrency()),
            "Missing replay holding instrument");
        if (!security.isMarginInstrument())
          groups.put(key, List.of());
      });
    }
    List<Position> positions = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    for (var entry : groups.entrySet()) {
      List<Transaction> txs = entry.getValue();
      HoldSecurityaccountSecurity holding = held.get(entry.getKey());
      Security security = txs.isEmpty() ? references.securities.get(holding.getHssk().getIdSecuritycurrency())
          : txs.getFirst().getSecurity();
      Integer account = txs.isEmpty() ? holding.getHssk().getIdSecuritycashAccount()
          : txs.getFirst().getIdSecurityaccount();
      if (!security.isMarginInstrument() && !held.containsKey(entry.getKey()))
        continue;
      if (aggregate && !security.isMarginInstrument()) {
        Double price = closingPrice(security.getId(), date, security.getName(), errors, market);
        if (price != null) {
          double amount = holding.getHodlings() * price * holding.getSplitPriceFactor();
          positions.add(
              new Position(entry.getKey(), security, account, holding.getHodlings(), amount, Math.abs(amount), null));
        }
        continue;
      }
      Map<Integer, List<Securitysplit>> splitMap = references.splitMaps.computeIfAbsent(security.getId(),
          splits::getSecuritysplitMapByIdSecuritycurrency);
      if (security.isMarginInstrument()) {
        var summary = new SecurityTransactionSummary(security, security.getCurrency(),
            parameters.getCurrencyPrecision());
        // Transactions are already bounded; the exclusive next day includes splits on the opening date.
        calculator.calcTransactions(security, tenant.isExcludeDivTax(), summary, splitMap, txs, date.plusDays(1),
            new DateTransactionCurrencypairMap(date.plusDays(1), true));
        var open = summary.securityPositionSummary.getTransactionsMarginOpenUnits();
        if (open.isEmpty())
          continue;
        Double price = closingPrice(security.getId(), date, security.getName(), errors, market);
        if (price == null)
          continue;
        double priceFactor = Securitysplit.calcSplitFatorForFromDate(security.getId(), date, splitMap);
        for (var lot : open) {
          if (lot.markForRemove || lot.openUnits == 0)
            continue;
          double closing = lot.calcGainLossOnPositionClose(price * priceFactor, 0);
          double gross = Math.abs(lot.openUnits * price * priceFactor * lot.openTransaction.getValuePerPoint());
          positions.add(new Position(entry.getKey() + ":" + lot.openTransaction.getId(), security, account,
              lot.openUnits, closing, gross, lot.openTransaction.getCashaccount().getId()));
        }
      } else {
        Double price = closingPrice(security.getId(), date, security.getName(), errors, market);
        if (price == null)
          continue;
        double unitPrice = price * holding.getSplitPriceFactor();
        Map<Integer, Double> attributed = new TreeMap<>();
        for (Transaction tx : txs) {
          if (tx.getTransactionType() != TransactionType.ACCUMULATE
              && tx.getTransactionType() != TransactionType.REDUCE)
            continue;
          double factor = Securitysplit.calcSplitFatorForFromDateAndToDate(security.getId(),
              tx.getTransactionDateAsLocalDate(), date.plusDays(1), splitMap).fromToDateFactor;
          double units = tx.getUnits() * factor * (tx.getTransactionType() == TransactionType.ACCUMULATE ? 1 : -1);
          attributed.merge(tx.getCashaccount().getId(), units, Double::sum);
        }
        double total = holding.getHodlings();
        boolean unambiguous = Math
            .abs(attributed.values().stream().mapToDouble(Double::doubleValue).sum() - total) < 1e-7
            && attributed.values().stream().allMatch(q -> Math.abs(q) < 1e-7 || Math.signum(q) == Math.signum(total));
        if (unambiguous) {
          attributed.forEach((ca, q) -> {
            if (Math.abs(q) > 1e-7)
              positions.add(new Position(entry.getKey() + ":" + ca, security, account, q, q * unitPrice,
                  Math.abs(q * unitPrice), ca));
          });
        } else {
          positions.add(new Position(entry.getKey(), security, account, total, total * unitPrice,
              Math.abs(total * unitPrice), null));
        }
      }
    }
    Set<String> required = accounts.stream().map(Cashaccount::getCurrency)
        .collect(Collectors.toCollection(TreeSet::new));
    positions.forEach(p -> required.add(p.security().getCurrency()));
    required.addAll(additionalCurrencies);
    Map<String, Double> receivables = market.receivables(date);
    required.addAll(receivables.keySet());
    Map<String, Double> fx = new HashMap<>();
    for (String currency : required) {
      if (currency.equals(tenant.getCurrency())) {
        fx.put(currency, 1.0);
        continue;
      }
      Exchange exchange = references.exchanges.computeIfAbsent(currency, references::exchange);
      Currencypair pair = exchange.pair();
      if (pair == null) {
        errors.add(currency + "/" + tenant.getCurrency());
        continue;
      }
      Double rate = closingPrice(pair.getId(), date, currency + "/" + tenant.getCurrency(), errors, market);
      if (rate != null)
        fx.put(currency, exchange.inverse() ? 1.0 / rate : rate);
    }
    double equity = 0, gross = 0;
    for (var entry : receivables.entrySet())
      if (fx.containsKey(entry.getKey()))
        equity += entry.getValue() * fx.get(entry.getKey());
    for (Cashaccount a : accounts)
      if (fx.containsKey(a.getCurrency()))
        equity += cash.get(a.getId()) * fx.get(a.getCurrency());
    for (Position p : positions)
      if (fx.containsKey(p.security().getCurrency())) {
        equity += p.closingValue() * fx.get(p.security().getCurrency());
        gross += p.grossExposure() * fx.get(p.security().getCurrency());
      }
    return new Snapshot(tenant.getCurrency(), positions, cash, fx, equity, gross, errors);
  }

  private record Exchange(Currencypair pair, boolean inverse) {
  }

  /** Reference data is local to a single valuation request or completed replay, never shared across runs. */
  private final class References {
    final Tenant tenant;
    final List<Cashaccount> accounts;
    final Map<Integer, Security> securities;
    final Map<Integer, Map<Integer, List<Securitysplit>>> splitMaps = new HashMap<>();
    final Map<String, Exchange> exchanges = new HashMap<>();

    References(Integer idTenant, Map<Integer, Security> securities) {
      tenant = tenants.findById(idTenant).orElseThrow();
      accounts = source.cashaccounts(idTenant);
      this.securities = securities;
    }

    Exchange exchange(String currency) {
      Currencypair pair = currencies.findByFromCurrencyAndToCurrency(currency, tenant.getCurrency());
      return pair == null
          ? new Exchange(currencies.findByFromCurrencyAndToCurrency(tenant.getCurrency(), currency), true)
          : new Exchange(pair, false);
    }
  }

  /** Sweeps inclusive holding periods once in date order, keeping only the currently active rows. */
  private static final class Periods<T extends HoldBase> {
    private final List<T> rows;
    private final Function<T, LocalDate> from;
    private final List<T> active = new ArrayList<>();
    private int next;

    Periods(List<T> rows, Function<T, LocalDate> from) {
      this.rows = rows.stream().sorted(Comparator.comparing(from)).toList();
      this.from = from;
    }

    Collection<T> at(LocalDate date) {
      while (next < rows.size() && !from.apply(rows.get(next)).isAfter(date))
        active.add(rows.get(next++));
      active.removeIf(row -> row.getToHoldDate() != null && row.getToHoldDate().isBefore(date));
      return active;
    }
  }

  private Double closingPrice(Integer id, LocalDate date, String label, List<String> errors, ClosingPrices market) {
    Double price = market.close(id, date);
    if (price == null || !Double.isFinite(price) || price <= 0) {
      errors.add(label);
      return null;
    }
    return price;
  }
}
