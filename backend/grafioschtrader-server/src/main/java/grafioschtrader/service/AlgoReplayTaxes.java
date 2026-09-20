package grafioschtrader.service;

import java.time.LocalDate;
import java.util.*;

import grafiosch.BaseConstants;
import grafioschtrader.dto.*;
import grafioschtrader.dto.TaxEstimateRequest.EventKind;
import grafioschtrader.dto.TaxEstimateResult.Warning;
import grafioschtrader.dto.TaxIncomeSummaryDto.WarningGroup;

/** Parsed models and committed diagnostics belong to exactly one run. Previews have no side effects. */
public final class AlgoReplayTaxes {
  private final AlgoReplayInputs.Snapshot inputs;
  private final TaxEvalExEstimator estimator;
  private final Map<String, Integer> currencyPrecision;
  private final Map<String, TaxModelConfig> models = new TreeMap<>();
  private final List<Warning> invalidModels = new ArrayList<>();
  private final Map<String, WarningGroup> warnings = new LinkedHashMap<>();
  private final Set<String> occurrences = new HashSet<>();

  public AlgoReplayTaxes(AlgoReplayInputs.Snapshot inputs, TaxEvalExEstimator estimator, LocalDate opening,
      Map<String, Integer> currencyPrecision) {
    this.inputs = inputs;
    this.estimator = estimator;
    this.currencyPrecision = currencyPrecision;
    if (inputs.applyTaxModels()) {
      inputs.countryModels().forEach((country, yaml) -> {
        try {
          models.put(country, estimator.parse(yaml));
        } catch (Exception e) {
          invalidModels.add(new Warning("TAX_MODEL_INVALID", country, null, e.getMessage()));
        }
      });
      invalidModels.forEach(warning -> record(warning, null, null, opening, "RUN"));
      if (inputs.countryModels().isEmpty())
        record(new Warning("TAX_NO_MODELS", null, null, null), null, null, opening, "RUN");
    }
  }

  public TaxEstimateResult estimate(Integer security, Integer account, EventKind kind, LocalDate date, double units,
      double price, double accrued, double gross) {
    var instrument = inputs.instruments().get(security);
    String currency = instrument == null ? null : instrument.currency();
    if (!inputs.applyTaxModels())
      return new TaxEstimateResult(0, currency, true, List.of(), List.of());
    TaxEstimateRequest r = new TaxEstimateRequest();
    r.eventKind = kind;
    r.eventDate = date;
    r.currency = currency;
    r.units = Math.abs(units);
    r.price = price;
    r.cleanValue = r.units * r.price;
    r.accruedInterest = accrued;
    r.grossIncome = gross;
    if (instrument != null) {
      r.instrument = instrument.instrument();
      r.assetclass = instrument.assetclass();
      r.mic = instrument.mic();
      r.issuerCountry = instrument.issuerCountry();
      r.exchangeCountry = instrument.exchangeCountry();
    }
    var dealer = inputs.accounts().get(account);
    if (dealer != null) {
      r.dealerCountry = dealer.dealerCountry();
      r.exemptInvestor = dealer.exemptInvestor();
    }
    var result = estimator.evaluate(models, r, precision(currency));
    List<Warning> pending = new ArrayList<>(invalidModels);
    result.warnings().stream().filter(w -> !"TAX_NO_MODELS".equals(w.code())).forEach(pending::add);
    return new TaxEstimateResult(result.estimatedTax(), currency,
        result.complete() && pending.isEmpty() && !inputs.countryModels().isEmpty(), result.matchedRules(), pending);
  }

  public void committed(TaxEstimateResult result, Integer security, Integer account, LocalDate date, String identity) {
    result.warnings().forEach(w -> record(w, security, account, date, identity));
  }

  public void record(Warning warning, Integer security, Integer account, LocalDate date, String identity) {
    String key = warning.code() + "|" + warning.country() + "|" + warning.section() + "|" + security + "|" + account;
    if (!occurrences.add(key + "|" + date + "|" + identity))
      return;
    WarningGroup old = warnings.get(key);
    warnings.put(key,
        new WarningGroup(warning.code(), warning.country(), warning.section(), security, account,
            old == null ? 1 : old.count() + 1, old == null || date.isBefore(old.firstDate()) ? date : old.firstDate(),
            old == null || date.isAfter(old.lastDate()) ? date : old.lastDate(),
            old == null ? identity : old.eventIdentity(), warning.detail()));
  }

  public List<WarningGroup> warnings() {
    return List.copyOf(warnings.values());
  }

  private int precision(String currency) {
    return currency == null ? BaseConstants.FID_STANDARD_FRACTION_DIGITS
        : currencyPrecision.getOrDefault(currency, BaseConstants.FID_STANDARD_FRACTION_DIGITS);
  }
}
