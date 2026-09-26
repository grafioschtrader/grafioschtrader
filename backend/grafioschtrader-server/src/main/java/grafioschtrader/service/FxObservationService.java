package grafioschtrader.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.dto.FxFeeConfig;
import grafioschtrader.dto.FxMarkupRequest;
import grafioschtrader.dto.FxObservationGroup;
import grafioschtrader.dto.FxObservationReport;
import grafioschtrader.dto.FxOutcome;
import grafioschtrader.dto.FxQuote;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityaccountJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.types.TransactionType;

/** Calibration of real conversions against stored EOD closes, independent of fee-model availability. */
@Service
@Transactional(readOnly = true)
public class FxObservationService {
  private final SecurityaccountJpaRepository accounts;
  private final TransactionJpaRepository transactions;
  private final CurrencypairJpaRepository pairs;
  private final HistoryquoteJpaRepository quotes;
  private final FxMarkupEngine engine;

  public FxObservationService(SecurityaccountJpaRepository accounts, TransactionJpaRepository transactions,
      CurrencypairJpaRepository pairs, HistoryquoteJpaRepository quotes, FxMarkupEngine engine) {
    this.accounts = accounts;
    this.transactions = transactions;
    this.pairs = pairs;
    this.quotes = quotes;
    this.engine = engine;
  }

  /** Checks account ownership before loading rows or resolving its tariff; date bounds are inclusive and optional. */
  public FxObservationReport account(Integer idAccount, Integer idTenant, LocalDate from, LocalDate to) {
    var account = accounts.findByIdSecuritycashAccountAndIdTenant(idAccount, idTenant);
    if (account == null)
      return new FxObservationReport(List.of());
    FxFeeConfig config = null;
    String error = null;
    try {
      config = FeeModelResolver.resolve(account).fx();
    } catch (IllegalArgumentException e) {
      error = e.getMessage();
    }
    return report(transactions.findFxAccountObservations(idAccount, idTenant, from, to), config, error);
  }

  /** Loads only paired withdrawals of the authenticated tenant; real transfers have no attributable account model. */
  public FxObservationReport transfers(Integer idTenant, LocalDate from, LocalDate to) {
    return report(transactions.findFxTransferObservations(idTenant, from, to), null, null);
  }

  private record GroupKey(Integer pair, FxMarkupRequest.Kind kind, String from, String to) {
  }

  private FxObservationReport report(List<Transaction> rows, FxFeeConfig config, String modelError) {
    Map<GroupKey, Statistics> groups = new LinkedHashMap<>();
    var market = new MarketData();
    for (var tx : rows) {
      var pair = market.pair(tx.getIdCurrencypair());
      var kind = tx.getTransactionType() == TransactionType.WITHDRAWAL ? FxMarkupRequest.Kind.TRANSFER
          : tx.getTransactionType() == TransactionType.DIVIDEND ? FxMarkupRequest.Kind.INCOME
              : FxMarkupRequest.Kind.TRADE;
      var period = period(config, tx.getTransactionDate());
      var key = new GroupKey(pair.getId(), kind, period == null ? null : period.validFrom(),
          period == null ? null : period.validTo());
      var stats = groups.computeIfAbsent(key, _ -> new Statistics(pair, key, config != null, modelError));
      observe(tx, pair, kind, config, modelError, market, stats);
    }
    return new FxObservationReport(groups.values().stream().map(Statistics::result).toList());
  }

  private void observe(Transaction tx, Currencypair pair, FxMarkupRequest.Kind kind, FxFeeConfig config,
      String modelError, MarketData market, Statistics stats) {
    Double recorded = tx.getCurrencyExRate();
    if (recorded == null || !Double.isFinite(recorded) || recorded <= 0) {
      stats.noRate++;
      return;
    }
    Double close = market.close(pair, tx.getTransactionDate());
    if (close == null) {
      stats.noClose++;
      return;
    }
    double deviation = (recorded / close - 1) * 100;
    if (Math.abs(deviation) >= 8 - 1e-12) {
      stats.outlier++;
      return;
    }
    var request = request(tx, pair, close, kind);
    double adverse = (request.receiveCurrency().equals(pair.getFromCurrency()) ? 1 : -1) * deviation;
    double volume = request.amount() * (request.payCurrency().equals(pair.getFromCurrency()) ? close : 1);
    FxQuote quote = modelError == null ? engine.quote(config, request, (pay, receive, date) -> {
      if (pay.equals(receive))
        return 1.0;
      if (receive.equals(request.receiveCurrency()))
        return pay.equals(pair.getFromCurrency()) ? close : 1 / close;
      return market.rate(pay, receive, date);
    }) : new FxQuote(0, FxOutcome.INVALID, null, null, null, modelError);
    stats.add(adverse, volume, quote);
  }

  /**
   * Uses the net instrument amount, including costs and accrued interest, before any exchange-rate conversion. A
   * margin instrument converts only its costs on opening and its result on closing, so its amount is the recorded cash
   * flow taken back into the instrument currency at the recorded rate.
   */
  private static FxMarkupRequest request(Transaction tx, Currencypair pair, double close, FxMarkupRequest.Kind kind) {
    String cash = tx.getCashaccount().getCurrency();
    if (kind == FxMarkupRequest.Kind.TRANSFER) {
      String receive = cash.equals(pair.getFromCurrency()) ? pair.getToCurrency() : pair.getFromCurrency();
      return new FxMarkupRequest(cash, receive, kind, Math.abs(tx.getCashaccountAmount()), tx.getTransactionDate(), "");
    }
    String instrument = tx.getSecurity().getCurrency();
    double net = tx.isMarginInstrument()
        ? DataBusinessHelper.divideMultiplyExchangeRate(tx.getCashaccountAmount(), tx.getCurrencyExRate(), instrument,
            cash, true)
        : tx.calculateSecurityTransactionAmountWithoutExchangeRate(0);
    // Negative income (for example a dividend on a short position) buys instrument currency as well.
    boolean buy = net < 0 || (net == 0 && tx.getTransactionType() == TransactionType.ACCUMULATE);
    double amount = Math.abs(net);
    if (buy)
      amount *= instrument.equals(pair.getFromCurrency()) ? close : 1 / close;
    String mic = tx.getSecurity().getStockexchange() == null ? "" : tx.getSecurity().getStockexchange().getMic();
    return new FxMarkupRequest(buy ? cash : instrument, buy ? instrument : cash, kind, amount, tx.getTransactionDate(),
        mic);
  }

  private static FxFeeConfig.Period period(FxFeeConfig config, LocalDate date) {
    if (config == null || config.periods() == null)
      return null;
    return config.periods().stream().filter(p -> !date.isBefore(LocalDate.parse(p.validFrom()))
        && (p.validTo() == null || !date.isAfter(LocalDate.parse(p.validTo())))).findFirst().orElse(null);
  }

  /** Request-local caches also retain missing closes; no quote downloads or pair creation are allowed. */
  private final class MarketData {
    private final Map<Integer, Currencypair> pairCache = new HashMap<>();
    private final Map<String, Double> closeCache = new HashMap<>();

    Currencypair pair(Integer id) {
      return pairCache.computeIfAbsent(id, key -> pairs.findById(key).orElseThrow());
    }

    Double close(Currencypair pair, LocalDate date) {
      String key = pair.getId() + ":" + date;
      if (!closeCache.containsKey(key)) {
        Double value = quotes.findByIdSecuritycurrencyAndDate(pair.getId(), date).map(q -> q.getClose()).orElse(null);
        closeCache.put(key, value != null && Double.isFinite(value) && value > 0 ? value : null);
      }
      return closeCache.get(key);
    }

    Double rate(String from, String to, LocalDate date) {
      for (var pair : pairs.findByFromCurrencyAndToCurrencyOrToCurrencyAndFromCurrency(from, to)) {
        Double close = close(pair, date);
        if (close != null)
          return pair.getFromCurrency().equals(from) ? close : 1 / close;
      }
      return null;
    }
  }

  private static final class Statistics {
    private final Currencypair pair;
    private final GroupKey key;
    private final boolean hasSection;
    private final List<Double> values = new ArrayList<>();
    private final Map<FxOutcome, Integer> outcomes = new EnumMap<>(FxOutcome.class);
    private int noClose, noRate, outlier, modelled;
    private double weightedSum, volumeSum, modelSum;
    private String error;

    Statistics(Currencypair pair, GroupKey key, boolean hasSection, String error) {
      this.pair = pair;
      this.key = key;
      this.hasSection = hasSection;
      this.error = error;
    }

    void add(double adverse, double volume, FxQuote quote) {
      values.add(adverse);
      weightedSum += volume * adverse;
      volumeSum += volume;
      outcomes.merge(quote.outcome(), 1, Integer::sum);
      if (quote.outcome() == FxOutcome.MATCHED) {
        modelled++;
        modelSum += quote.percent();
      }
      if (quote.outcome() == FxOutcome.INVALID)
        error = quote.error();
    }

    FxObservationGroup result() {
      values.sort(Double::compare);
      int n = values.size();
      Double mean = n == 0 ? null : values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
      Double median = n == 0 ? null : (values.get((n - 1) / 2) + values.get(n / 2)) / 2;
      Double stdDev = n < 2 ? null
          : Math.sqrt(values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / (n - 1));
      return new FxObservationGroup(pair.getId(), pair.getFromCurrency() + "/" + pair.getToCurrency(), key.kind(),
          key.from(), key.to(), n, noClose, noRate, outlier, mean, median, stdDev,
          volumeSum == 0 ? null : weightedSum / volumeSum, hasSection ? modelled : null,
          modelled == 0 ? null : modelSum / modelled, n - modelled, Map.copyOf(outcomes), error);
    }
  }
}
