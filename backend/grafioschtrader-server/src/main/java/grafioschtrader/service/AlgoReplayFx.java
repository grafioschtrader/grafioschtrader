package grafioschtrader.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import grafioschtrader.dto.FxFeeConfig;
import grafioschtrader.dto.FxMarkupRequest;
import grafioschtrader.dto.FxOutcome;
import grafioschtrader.dto.FxQuote;
import grafioschtrader.entities.Currencypair;

/** Captured FX tariffs and committed conversion totals of one run. Estimation never changes the totals or trail. */
final class AlgoReplayFx {
  record Warning(LocalDate date, String details) {
  }

  /** Keeps the economic conversion alongside its quote until the ledger accepts it. */
  record Conversion(Integer account, String pay, String receive, String kind, FxQuote quote, double charge,
      double reportingCharge, LocalDate date) {
  }

  private final Map<Integer, FxFeeConfig> models;
  private final String currency;
  private final FxMarkupEngine.TierRate rates;
  private final Consumer<Warning> warnings;
  private final FxMarkupEngine engine = new FxMarkupEngine();
  private final Set<String> committed = new HashSet<>();
  private final Set<String> warned = new HashSet<>();
  private final Map<String, String> details = new HashMap<>();
  private double paid;
  private int uncovered;

  AlgoReplayFx(AlgoReplayInputs.Snapshot inputs, String currency, FxMarkupEngine.TierRate rates,
      Consumer<Warning> warnings) {
    this.models = inputs.version() >= 5 && inputs.fxModels() != null ? Map.copyOf(inputs.fxModels()) : Map.of();
    this.currency = currency;
    this.rates = rates;
    this.warnings = warnings;
  }

  static String convention(AlgoReplayInputs.Snapshot inputs) {
    return inputs.version() >= 5 && inputs.fxModels() != null && !inputs.fxModels().isEmpty()
        ? "FX_MARKUP_FROM_FEE_MODEL"
        : "FX_AT_EOD_MID";
  }

  boolean modelled() {
    return !models.isEmpty();
  }

  double paid() {
    return paid;
  }

  int uncovered() {
    return uncovered;
  }

  FxQuote quote(Integer account, String pay, String receive, Currencypair pair, double close, String kind,
      double amount, LocalDate date, String mic) {
    return quote(account, pay, receive, pair, close, kind, amount, date, mic, rates);
  }

  /** A third-currency tier uses the same exact-date or carried-close lookup as the conversion being priced. */
  FxQuote quote(Integer account, String pay, String receive, Currencypair pair, double close, String kind,
      double amount, LocalDate date, String mic, FxMarkupEngine.TierRate tierRates) {
    var quote = engine.quote(models.get(account),
        new FxMarkupRequest(pay, receive, FxMarkupRequest.Kind.valueOf(kind), amount, date, mic), (from, to, day) -> {
          if (from.equals(to))
            return 1.0;
          if (from.equals(pay) && to.equals(receive))
            return pay.equals(pair.getFromCurrency()) ? close : 1 / close;
          return tierRates.close(from, to, day);
        });
    if (quote.outcome() == FxOutcome.INVALID)
      throw new Failure(account + ": " + quote.error());
    return quote;
  }

  static double effective(Currencypair pair, double close, String receive, FxQuote quote) {
    return close * (1 + (receive.equals(pair.getFromCurrency()) ? 1 : -1) * quote.percent() / 100);
  }

  Conversion conversion(Integer account, String pay, String receive, String kind, FxQuote quote, double charge,
      String chargeCurrency, LocalDate date) {
    return new Conversion(account, pay, receive, kind, quote, charge, reporting(charge, chargeCurrency, date), date);
  }

  double reporting(double amount, String from, LocalDate date) {
    if (amount == 0 || from.equals(currency))
      return amount;
    Double rate = rates.close(from, currency, date);
    if (rate == null || !Double.isFinite(rate) || rate <= 0)
      throw new IllegalArgumentException("REPLAY_FX_UNAVAILABLE");
    return amount * rate;
  }

  void committed(String identity, Conversion conversion) {
    if (conversion == null || !committed.add(identity))
      return;
    paid += conversion.reportingCharge();
    var quote = conversion.quote();
    if (quote.percent() > 0)
      details.put(identity, "fx=" + quote.percent() + "% (" + quote.ruleName() + ")");
    if (quote.outcome() == FxOutcome.MATCHED || !modelled())
      return;
    uncovered++;
    String key = conversion.account() + ": " + conversion.pay() + "→" + conversion.receive() + " " + conversion.kind()
        + " " + quote.outcome();
    if (warned.add(key))
      warnings.accept(new Warning(conversion.date(), key + " from " + conversion.date()));
  }

  String details(String identity, String original) {
    String fx = details.get(identity);
    return fx == null ? original : original == null || original.isBlank() ? fx : original + "; " + fx;
  }

  /** Configuration errors must cross the ordinary per-order refusal handlers and fail the run. */
  static final class Failure extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    Failure(String message) {
      super("REPLAY_FX_MODEL_FAILED: " + message);
    }
  }
}
