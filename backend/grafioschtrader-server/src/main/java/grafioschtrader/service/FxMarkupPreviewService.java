package grafioschtrader.service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.dto.FxMarkupPreviewRequest;
import grafioschtrader.dto.FxMarkupRequest;
import grafioschtrader.dto.FxOutcome;
import grafioschtrader.dto.FxQuote;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityaccountJpaRepository;
import grafioschtrader.repository.TradingPlatformPlanJpaRepository;

/** Read-only editor preview with tenant ownership checked before resolving any account document. */
@Service
@Transactional(readOnly = true)
public class FxMarkupPreviewService {
  private final SecurityaccountJpaRepository accounts;
  private final TradingPlatformPlanJpaRepository plans;
  private final CurrencypairJpaRepository pairs;
  private final HistoryquoteJpaRepository quotes;
  private final FxMarkupEngine engine;

  public FxMarkupPreviewService(SecurityaccountJpaRepository accounts, TradingPlatformPlanJpaRepository plans,
      CurrencypairJpaRepository pairs, HistoryquoteJpaRepository quotes, FxMarkupEngine engine) {
    this.accounts = accounts;
    this.plans = plans;
    this.pairs = pairs;
    this.quotes = quotes;
    this.engine = engine;
  }

  public Securityaccount ownedAccount(Integer id, Integer tenant) {
    Securityaccount account = id == null ? null : accounts.findByIdSecuritycashAccountAndIdTenant(id, tenant);
    if (account == null)
      throw new IllegalArgumentException("Security account not found or not owned by current tenant");
    return account;
  }

  public FxQuote account(FxMarkupPreviewRequest preview, Integer tenant) {
    try {
      var account = ownedAccount(preview.idSecurityaccount(), tenant);
      return evaluate(FeeModelResolver.resolve(account, preview.yaml()), preview.request());
    } catch (IllegalArgumentException e) {
      return invalid(e.getMessage());
    }
  }

  public FxQuote plan(FxMarkupPreviewRequest preview) {
    try {
      String yaml = preview.yaml();
      if (yaml == null || yaml.isBlank()) {
        var plan = preview.idTradingPlatformPlan() == null ? null
            : plans.findById(preview.idTradingPlatformPlan()).orElse(null);
        if (plan == null)
          return invalid("Trading platform plan not found");
        yaml = plan.getFeeModelYaml();
      }
      var plan = new grafioschtrader.entities.TradingPlatformPlan();
      plan.setFeeModelYaml(yaml);
      var account = new Securityaccount();
      account.setTradingPlatformPlan(plan);
      return evaluate(FeeModelResolver.resolve(account), preview.request());
    } catch (IllegalArgumentException e) {
      return invalid(e.getMessage());
    }
  }

  private FxQuote evaluate(FeeModelResolver.ResolvedFeeModel model, FxMarkupRequest request) {
    if (request == null || !knownCurrency(request.payCurrency()) || !knownCurrency(request.receiveCurrency()))
      return invalid("Unknown pay or receive currency");
    return engine.quote(model.fx(), request, this::close);
  }

  /** Does not create a pair or download quotes. Either stored direction is accepted. */
  private Double close(String from, String to, LocalDate date) {
    var wanted = DataBusinessHelper.getCurrencypairWithSetOfFromAndTo(from, to);
    for (var pair : pairs.findByFromCurrencyAndToCurrencyOrToCurrencyAndFromCurrency(wanted.getFromCurrency(),
        wanted.getToCurrency())) {
      var quote = quotes.findByIdSecuritycurrencyAndDate(pair.getId(), date).orElse(null);
      if (quote != null && Double.isFinite(quote.getClose()) && quote.getClose() > 0)
        return pair.getFromCurrency().equals(from) ? quote.getClose() : 1 / quote.getClose();
    }
    return null;
  }

  private static boolean knownCurrency(String code) {
    return code != null && (GlobalConstants.CRYPTO_CURRENCY_SUPPORTED.contains(code)
        || Currency.getAvailableCurrencies().stream().anyMatch(c -> c.getCurrencyCode().equals(code)));
  }

  public static List<ValueKeyHtmlSelectOptions> kinds() {
    return Arrays.stream(FxMarkupRequest.Kind.values())
        .map(k -> new ValueKeyHtmlSelectOptions(k.name(), "FX_KIND_" + k.name())).toList();
  }

  private static FxQuote invalid(String error) {
    return new FxQuote(0, FxOutcome.INVALID, null, null, null, error);
  }
}
