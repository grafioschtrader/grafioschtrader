package grafioschtrader.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import grafiosch.BaseConstants;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.dto.FxFeeConfig;
import grafioschtrader.dto.FxMarkupRequest;
import grafioschtrader.dto.FxOutcome;
import grafioschtrader.dto.FxQuote;
import grafioschtrader.dto.TaxEstimateRequest;
import grafioschtrader.dto.TaxEstimateRequest.EventKind;
import grafioschtrader.dto.TaxEstimateResult;
import grafioschtrader.dto.TaxEstimateResult.Warning;
import grafioschtrader.dto.TaxModelConfig;
import grafioschtrader.dto.TransactionCostEstimateResult;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reportviews.securityaccount.DisposalCostDetail;
import grafioschtrader.reportviews.securityaccount.DisposalCostDetail.Part;
import grafioschtrader.reportviews.securityaccount.DisposalEstimate;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.repository.HoldDailyTotalJpaRepository;
import grafioschtrader.repository.SecurityaccountJpaRepository;
import grafioschtrader.repository.TaxCountryJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.service.DisposalPositionCollector.Allocation;
import grafioschtrader.service.FeeModelResolver.ResolvedFeeModel;
import grafioschtrader.types.TransactionType;

/**
 * Estimates the costs of the hypothetical sale with which the reports value an open position: the commission of the fee
 * model, the transaction tax of the simulation tax models and the markup of converting the proceeds into the currency
 * of the settlement cash account. Personal income and capital gains taxes are out of scope.
 *
 * <p>
 * The rules are evaluated by the same estimators the historical replay uses, so a report and a simulation price a sale
 * identically: {@link FeeModelResolver}, {@link TransactionCostEvalExEstimator}, {@link TaxEvalExEstimator} and
 * {@link FxMarkupEngine}. Unlike the replay, which stops on a fee model that cannot answer, a report never fails: a
 * missing model, a missing rule or an unknown required input marks the affected component as incomplete, with the
 * reason, and an unknown cost is never taken as zero.
 * </p>
 *
 * <p>
 * The estimate is switched on by the global parameter {@code gt.disposal.cost.estimate}, and a tenant can switch it
 * off for itself ({@link Tenant#isDisposalCostEstimate()}); it is effective only while both are on, otherwise
 * {@link #newCollectorIfEnabled} and therefore {@link #newSession} return null. A session belongs to one report and resolves the fee model of each security account and the tax
 * models only once.
 * </p>
 */
@Service
public class DisposalCostEstimator {

  public static final String FEE_MODEL_MISSING = "DISPOSAL_FEE_MODEL_MISSING";
  public static final String FEE_MODEL_INVALID = "DISPOSAL_FEE_MODEL_INVALID";
  public static final String FEE_RULE_UNMATCHED = "DISPOSAL_FEE_RULE_UNMATCHED";
  public static final String FIXED_ASSETS_UNKNOWN = "DISPOSAL_FIXED_ASSETS_UNKNOWN";
  public static final String PORTFOLIO_TOTAL_UNKNOWN = "DISPOSAL_PORTFOLIO_TOTAL_UNKNOWN";
  public static final String TENANT_TOTAL_UNKNOWN = "DISPOSAL_TENANT_TOTAL_UNKNOWN";
  public static final String FX_MODEL_MISSING = "DISPOSAL_FX_MODEL_MISSING";
  public static final String FX_RULE_UNMATCHED = "DISPOSAL_FX_RULE_UNMATCHED";
  public static final String FX_ACCOUNT_AMBIGUOUS = "DISPOSAL_FX_ACCOUNT_AMBIGUOUS";
  public static final String ACCOUNT_UNKNOWN = "DISPOSAL_ACCOUNT_UNKNOWN";
  public static final String EVALUATION_ERROR = "DISPOSAL_EVALUATION_ERROR";
  public static final String TAX_NO_MODELS = "TAX_NO_MODELS";
  public static final String TAX_MODEL_INVALID = "TAX_MODEL_INVALID";

  /** Days a tier rate may lie before the report date, so that a weekend or a missing quote does not fail a rule. */
  private static final int TIER_RATE_DAYS_BACK = 7;

  private static final Logger log = LoggerFactory.getLogger(DisposalCostEstimator.class);

  private final TransactionCostEvalExEstimator costEstimator;
  private final TaxEvalExEstimator taxEstimator;
  private final FxMarkupEngine fxEngine;
  private final TaxCountryJpaRepository taxCountryJpaRepository;
  private final SecurityaccountJpaRepository securityaccountJpaRepository;
  private final FxMarkupPreviewService fxMarkupPreviewService;
  private final GlobalparametersService globalparametersService;
  private final HoldDailyTotalJpaRepository holdDailyTotalJpaRepository;
  private final TenantJpaRepository tenantJpaRepository;

  public DisposalCostEstimator(TransactionCostEvalExEstimator costEstimator, TaxEvalExEstimator taxEstimator,
      FxMarkupEngine fxEngine, TaxCountryJpaRepository taxCountryJpaRepository,
      SecurityaccountJpaRepository securityaccountJpaRepository, FxMarkupPreviewService fxMarkupPreviewService,
      GlobalparametersService globalparametersService, HoldDailyTotalJpaRepository holdDailyTotalJpaRepository,
      TenantJpaRepository tenantJpaRepository) {
    this.costEstimator = costEstimator;
    this.taxEstimator = taxEstimator;
    this.fxEngine = fxEngine;
    this.taxCountryJpaRepository = taxCountryJpaRepository;
    this.securityaccountJpaRepository = securityaccountJpaRepository;
    this.fxMarkupPreviewService = fxMarkupPreviewService;
    this.globalparametersService = globalparametersService;
    this.holdDailyTotalJpaRepository = holdDailyTotalJpaRepository;
    this.tenantJpaRepository = tenantJpaRepository;
  }

  /**
   * Whether the estimate applies to the reports of a tenant: the global parameter must switch it on and the tenant must
   * not have switched it off. A simulation tenant follows its main tenant, whose flag is the one the user edits.
   *
   * @param idTenant the tenant of the report, passed explicitly because reports may run outside the request thread
   * @return true when the estimate is effective for the tenant
   */
  public boolean isEnabled(Integer idTenant) {
    if (!globalparametersService.isDisposalCostEstimate() || idTenant == null) {
      return false;
    }
    Tenant tenant = tenantJpaRepository.findById(idTenant).orElse(null);
    if (tenant != null && tenant.getIdParentTenant() != null) {
      tenant = tenantJpaRepository.findById(tenant.getIdParentTenant()).orElse(tenant);
    }
    return tenant != null && tenant.isDisposalCostEstimate();
  }

  /**
   * @param idTenant the tenant of the report
   * @return a collector for a report, null while the estimate is not effective for the tenant
   */
  public DisposalPositionCollector newCollectorIfEnabled(Integer idTenant) {
    return isEnabled(idTenant) ? new DisposalPositionCollector() : null;
  }

  /**
   * Opens the estimate of one report.
   *
   * @param date      the report date, the day of the hypothetical sale
   * @param collector the units, settlement currencies and trades collected while the report walked the transactions;
   *                  null when the estimate is switched off
   * @return the session, null when the collector is null
   */
  public Session newSession(LocalDate date, DisposalPositionCollector collector) {
    if (collector == null) {
      return null;
    }
    Map<String, TaxModelConfig> models = new TreeMap<>();
    List<Warning> invalidModels = new ArrayList<>();
    taxCountryJpaRepository.findAll().stream().filter(country -> country.isHasTaxModel()).forEach(country -> {
      try {
        models.put(country.getCountryCode(), taxEstimator.parse(country.getTaxModelYaml()));
      } catch (Exception e) {
        invalidModels.add(new Warning(TAX_MODEL_INVALID, country.getCountryCode(), null, e.getMessage()));
      }
    });
    Map<Integer, Securityaccount> accounts = new HashMap<>();
    Map<List<Integer>, Optional<Double>> totals = new HashMap<>();
    return new Session(date, collector, costEstimator, taxEstimator, fxEngine, models, invalidModels,
        id -> accounts.computeIfAbsent(id, key -> securityaccountJpaRepository.findById(key).orElse(null)),
        this::tierRate, globalparametersService.getCurrencyPrecision(),
        (idTenant, idPortfolio) -> totals.computeIfAbsent(Arrays.asList(idTenant, idPortfolio),
            _ -> Optional.ofNullable(holdDailyTotalJpaRepository
                .getLastOnOrBefore(idTenant, idPortfolio, date.minusDays(1)).totalBalanceMC()))
            .orElse(null));
  }

  /**
   * The total value of a portfolio or of a tenant at the close of the last day before the day of the hypothetical
   * sale, for the fee model variables {@code portfolioTotal} and {@code tenantTotal}.
   */
  @FunctionalInterface
  public interface TotalLookup {
    /**
     * @param idTenant    the tenant
     * @param idPortfolio the portfolio, or null for the total of the tenant
     * @return the total value, null when the daily total value of no such day exists
     */
    Double totalBefore(Integer idTenant, Integer idPortfolio);
  }

  /** The stored close of the report date or of one of the days before it, in either direction of the pair. */
  private Double tierRate(String from, String to, LocalDate date) {
    for (int i = 0; i <= TIER_RATE_DAYS_BACK; i++) {
      Double close = fxMarkupPreviewService.close(from, to, date.minusDays(i));
      if (close != null) {
        return close;
      }
    }
    return null;
  }

  /**
   * Markup of converting the balance of a cash account into the main currency.
   *
   * @param percent the markup in percent, null when it is unknown
   * @param detail  the matched rule or the reason of an unknown markup, null for an account in the main currency
   */
  public record TransferMarkup(Double percent, DisposalCostDetail detail) {
  }

  /** The estimate of one report. Not thread safe. */
  public static class Session {
    private final LocalDate date;
    private final DisposalPositionCollector collector;
    private final TransactionCostEvalExEstimator costEstimator;
    private final TaxEvalExEstimator taxEstimator;
    private final FxMarkupEngine fxEngine;
    private final Map<String, TaxModelConfig> taxModels;
    private final List<Warning> invalidTaxModels;
    private final Function<Integer, Securityaccount> accountLookup;
    private final FxMarkupEngine.TierRate tierRate;
    private final Map<String, Integer> currencyPrecision;
    private final TotalLookup totalLookup;
    private final Map<Integer, AccountContext> accountContexts = new HashMap<>();
    /** Market value in main currency of the positions of each account; an absent account has an unknown value. */
    private final Map<Integer, Double> fixedAssetsByAccount = new HashMap<>();

    /** Resolved fee model of a security account, or the reason why it could not be resolved. */
    private record AccountContext(Securityaccount account, ResolvedFeeModel model, String modelError) {
    }

    public Session(LocalDate date, DisposalPositionCollector collector, TransactionCostEvalExEstimator costEstimator,
        TaxEvalExEstimator taxEstimator, FxMarkupEngine fxEngine, Map<String, TaxModelConfig> taxModels,
        List<Warning> invalidTaxModels, Function<Integer, Securityaccount> accountLookup,
        FxMarkupEngine.TierRate tierRate, Map<String, Integer> currencyPrecision, TotalLookup totalLookup) {
      this.date = date;
      this.collector = collector;
      this.costEstimator = costEstimator;
      this.taxEstimator = taxEstimator;
      this.fxEngine = fxEngine;
      this.taxModels = taxModels;
      this.invalidTaxModels = invalidTaxModels;
      this.accountLookup = accountLookup;
      this.tierRate = tierRate;
      this.currencyPrecision = currencyPrecision;
      this.totalLookup = totalLookup;
    }

    /**
     * Estimates the disposal costs of every open position of a report and stores them on the positions. Closed
     * positions, margin instruments, cash accounts shown as securities and comparison-only rows are left without an
     * estimate.
     *
     * @param positions  the valued positions of the report
     * @param rateToMain exchange rate of a currency into the main currency at the report date; it may throw when no
     *                   rate is available, which leaves the account value of a tiered fee model unknown
     */
    public void estimatePositions(List<SecurityPositionSummary> positions, ToDoubleFunction<String> rateToMain) {
      List<SecurityPositionSummary> estimable = positions.stream().filter(Session::isEstimable).toList();
      collectFixedAssets(estimable, rateToMain);
      for (SecurityPositionSummary position : estimable) {
        position.applyDisposalEstimate(estimate(position.getSecurity(), position.units,
            position.valueSecurity / position.units, position.usedIdSecurityaccount));
      }
    }

    /**
     * Estimates the costs of selling the given units, as shown on the hypothetical sale of the transaction list. The
     * value of the security accounts is not known there, so a fee model grading its commission by it stays incomplete;
     * the total values of the portfolio and of the tenant are known from the daily total value.
     *
     * @param security        the security sold
     * @param units           the units of the position
     * @param price           the price of the hypothetical sale
     * @param fallbackAccount the account taking all units when the collector knows none, may be null
     * @return the estimate in security currency
     */
    public DisposalEstimate estimateSell(Security security, double units, double price, Integer fallbackAccount) {
      return estimate(security, units, price, fallbackAccount);
    }

    private static boolean isEstimable(SecurityPositionSummary position) {
      Security security = position.getSecurity();
      return security != null && security.getIdSecuritycurrency() != null && security.getIdSecuritycurrency() >= 0
          && !position.comparisonOnly && !security.isMarginInstrument() && Math.abs(position.units) > 1e-9
          && Double.isFinite(position.valueSecurity / position.units);
    }

    private void collectFixedAssets(List<SecurityPositionSummary> positions, ToDoubleFunction<String> rateToMain) {
      Set<Integer> unknown = new HashSet<>();
      for (SecurityPositionSummary position : positions) {
        Security security = position.getSecurity();
        double price = position.valueSecurity / position.units;
        Double rate;
        try {
          rate = rateToMain.applyAsDouble(security.getCurrency());
        } catch (Exception e) {
          rate = null;
        }
        for (Allocation allocation : collector.allocate(security.getIdSecuritycurrency(), position.units,
            position.usedIdSecurityaccount, security.getCurrency())) {
          if (rate == null) {
            unknown.add(allocation.idSecurityaccount());
          } else {
            fixedAssetsByAccount.merge(allocation.idSecurityaccount(), allocation.units() * price * rate, Double::sum);
          }
        }
      }
      unknown.forEach(fixedAssetsByAccount::remove);
    }

    private DisposalEstimate estimate(Security security, double units, double price, Integer fallbackAccount) {
      Accumulator accumulator = new Accumulator();
      List<Allocation> allocations = collector.allocate(security.getIdSecuritycurrency(), units, fallbackAccount,
          security.getCurrency());
      if (allocations.isEmpty()) {
        accumulator.unknownAll(null, ACCOUNT_UNKNOWN, null);
      }
      for (Allocation allocation : allocations) {
        try {
          estimateAccount(accumulator, security, allocation, price);
        } catch (Exception e) {
          log.warn("Disposal cost estimate of security {} in security account {} failed", security.getId(),
              allocation.idSecurityaccount(), e);
          accumulator.unknownAll(null, EVALUATION_ERROR, e.getMessage());
        }
      }
      return accumulator.toEstimate();
    }

    private void estimateAccount(Accumulator accumulator, Security security, Allocation allocation, double price) {
      AccountContext context = accountContexts.computeIfAbsent(allocation.idSecurityaccount(), this::accountContext);
      if (context.account() == null) {
        accumulator.unknownAll(null, ACCOUNT_UNKNOWN, String.valueOf(allocation.idSecurityaccount()));
        return;
      }
      String name = context.account().getName();
      String settlementCurrency = allocation.settlementCurrency() == null ? security.getCurrency()
          : allocation.settlementCurrency();
      double units = Math.abs(allocation.units());
      Double commission = commission(accumulator, name, context, security, allocation, units, price,
          settlementCurrency);
      double accrued = accruedInterest(security, units);
      Double tax = tax(accumulator, name, context.account(), security, units, price, accrued);
      double net = units * price - (commission == null ? 0.0 : commission) - (tax == null ? 0.0 : tax);
      fxMarkup(accumulator, name, context, security, settlementCurrency, net);
    }

    private AccountContext accountContext(Integer idSecurityaccount) {
      Securityaccount account = accountLookup.apply(idSecurityaccount);
      if (account == null) {
        return new AccountContext(null, null, null);
      }
      try {
        return new AccountContext(account, FeeModelResolver.resolve(account), null);
      } catch (IllegalArgumentException e) {
        return new AccountContext(account, null, e.getMessage());
      }
    }

    private Double commission(Accumulator accumulator, String name, AccountContext context, Security security,
        Allocation allocation, double units, double price, String settlementCurrency) {
      if (context.modelError() != null) {
        accumulator.unknown(name, Part.COMMISSION, FEE_MODEL_INVALID, context.modelError());
        return null;
      }
      String yaml = context.model().commissionYaml();
      if (yaml == null) {
        accumulator.unknown(name, Part.COMMISSION, FEE_MODEL_MISSING, null);
        return null;
      }
      Double fixedAssets = fixedAssetsByAccount.get(allocation.idSecurityaccount());
      if (fixedAssets == null && yaml.contains("fixedAssets")) {
        accumulator.unknown(name, Part.COMMISSION, FIXED_ASSETS_UNKNOWN, null);
        return null;
      }
      Securityaccount account = context.account();
      Double portfolioTotal = yaml.contains("portfolioTotal") ? portfolioTotal(account) : null;
      if (portfolioTotal == null && yaml.contains("portfolioTotal")) {
        accumulator.unknown(name, Part.COMMISSION, PORTFOLIO_TOTAL_UNKNOWN, null);
        return null;
      }
      Double tenantTotal = yaml.contains("tenantTotal") ? tenantTotal(account) : null;
      if (tenantTotal == null && yaml.contains("tenantTotal")) {
        accumulator.unknown(name, Part.COMMISSION, TENANT_TOTAL_UNKNOWN, null);
        return null;
      }
      var request = TransactionCostEvalExEstimator.buildRequest(security, units, price, TransactionType.REDUCE, date,
          account.getTradingPlatformPlan() == null ? null : account.getTradingPlatformPlan().getIdTradingPlatformPlan(),
          fixedAssets == null ? 0.0 : fixedAssets, settlementCurrency,
          collector.tradeCounts(allocation.idSecurityaccount(), security.getIdSecuritycurrency(), date));
      request.setIdSecurityaccount(allocation.idSecurityaccount());
      request.setPortfolioTotal(portfolioTotal);
      request.setTenantTotal(tenantTotal);
      TransactionCostEstimateResult result = costEstimator.evaluateYaml(yaml, request);
      Double cost = result.getEstimatedCost();
      if (result.getError() != null || cost == null || !Double.isFinite(cost) || cost < 0) {
        accumulator.unknown(name, Part.COMMISSION, FEE_RULE_UNMATCHED,
            result.getError() != null ? result.getError() : String.valueOf(cost));
        return null;
      }
      double rounded = DataBusinessHelper.roundStandard(cost);
      accumulator.commission(name, result.getMatchedRuleName(), rounded);
      return rounded;
    }

    /** The total value of the portfolio of a security account before the day of the sale, null when unknown. */
    private Double portfolioTotal(Securityaccount account) {
      return account.getIdTenant() == null || account.getPortfolio() == null ? null
          : totalLookup.totalBefore(account.getIdTenant(), account.getPortfolio().getIdPortfolio());
    }

    /** The total value of all portfolios of the tenant before the day of the sale, null when unknown. */
    private Double tenantTotal(Securityaccount account) {
      return account.getIdTenant() == null ? null : totalLookup.totalBefore(account.getIdTenant(), null);
    }

    /** Accrued interest a buyer pays on top of the clean price of a directly held bond, 0 for anything else. */
    private double accruedInterest(Security security, double units) {
      var terms = AlgoReplayInputs.couponTerms(security);
      if (terms == null) {
        return 0.0;
      }
      try {
        return new AlgoReplayCouponSchedule(terms).accrued(units, date);
      } catch (IllegalArgumentException e) {
        return 0.0;
      }
    }

    private Double tax(Accumulator accumulator, String name, Securityaccount account, Security security, double units,
        double price, double accrued) {
      if (taxModels.isEmpty() && invalidTaxModels.isEmpty()) {
        accumulator.unknown(name, Part.TAX, TAX_NO_MODELS, null);
        return null;
      }
      TaxEstimateRequest request = new TaxEstimateRequest();
      request.eventKind = EventKind.SELL;
      request.eventDate = date;
      request.currency = security.getCurrency();
      request.units = units;
      request.price = price;
      request.cleanValue = units * price;
      request.accruedInterest = accrued;
      var asset = security.getAssetClass();
      if (asset != null) {
        request.instrument = asset.getSpecialInvestmentInstrument() == null ? null
            : asset.getSpecialInvestmentInstrument().name();
        request.assetclass = asset.getCategoryType() == null ? null : asset.getCategoryType().name();
      }
      var exchange = security.getStockexchange();
      if (exchange != null) {
        request.mic = exchange.getMic();
        request.exchangeCountry = exchange.getCountryCode();
      }
      request.issuerCountry = security.getIssuerCountry();
      request.dealerCountry = account.getTradingPlatformPlan() == null ? null
          : account.getTradingPlatformPlan().getCountryCode();
      request.exemptInvestor = account.getTaxExemptInvestor();
      TaxEstimateResult result = taxEstimator.evaluate(taxModels, request,
          currencyPrecision.getOrDefault(security.getCurrency(), BaseConstants.FID_STANDARD_FRACTION_DIGITS));
      result.matchedRules().forEach(match -> accumulator.details
          .add(DisposalCostDetail.matched(name, Part.TAX, match.rule(), match.country(), match.amount())));
      List<Warning> warnings = new ArrayList<>(invalidTaxModels);
      result.warnings().stream().filter(w -> !TAX_NO_MODELS.equals(w.code()) || taxModels.isEmpty())
          .forEach(warnings::add);
      warnings.forEach(w -> accumulator.unknown(name, Part.TAX, w.code(),
          w.country() == null ? w.detail() : w.detail() == null ? w.country() : w.country() + ": " + w.detail()));
      accumulator.tax(result.estimatedTax());
      return result.estimatedTax();
    }

    private void fxMarkup(Accumulator accumulator, String name, AccountContext context, Security security,
        String settlementCurrency, double net) {
      if (settlementCurrency.equals(security.getCurrency())) {
        accumulator.fx(0.0);
        accumulator.sameCurrencyNet += net;
        return;
      }
      if (context.modelError() != null) {
        accumulator.unknown(name, Part.FX, FEE_MODEL_INVALID, context.modelError());
        return;
      }
      String mic = security.getStockexchange() == null ? null : security.getStockexchange().getMic();
      FxQuote quote = fxEngine.quote(context.model().fx(), new FxMarkupRequest(security.getCurrency(),
          settlementCurrency, FxMarkupRequest.Kind.TRADE, Math.max(0.0, net), date, mic), tierRate);
      if (quote.outcome() == FxOutcome.MATCHED) {
        double markup = DataBusinessHelper.roundStandard(Math.max(0.0, net) * quote.percent() / 100.0);
        accumulator.fx(markup);
        accumulator.details
            .add(DisposalCostDetail.matched(name, Part.FX, quote.ruleName(), settlementCurrency, markup));
      } else {
        accumulator.unknown(name, Part.FX, fxReason(quote), fxDetail(quote));
      }
    }

    /**
     * Estimates the markup of converting the balance of a cash account, together with the net proceeds of the
     * securities settling in its currency, into the main currency. The fx section is the one of the security account
     * connected to the cash account, otherwise the one shared by all security accounts of the portfolio.
     *
     * @param cashaccount       the cash account
     * @param portfolioAccounts the security accounts of the portfolio of the cash account
     * @param mainCurrency      the currency of the report
     * @param amount            the amount to convert in the currency of the cash account
     * @return the markup, 0 percent without detail for an account in the main currency or with nothing to convert
     */
    public TransferMarkup transferMarkup(Cashaccount cashaccount, List<Securityaccount> portfolioAccounts,
        String mainCurrency, double amount) {
      if (cashaccount.getCurrency().equals(mainCurrency) || amount <= 0) {
        return new TransferMarkup(0.0, null);
      }
      String name = cashaccount.getName();
      try {
        FxFeeConfig fx = null;
        if (cashaccount.getConnectIdSecurityaccount() != null) {
          AccountContext context = accountContexts.computeIfAbsent(cashaccount.getConnectIdSecurityaccount(),
              this::accountContext);
          if (context.modelError() != null) {
            return new TransferMarkup(null,
                DisposalCostDetail.unknown(name, Part.FX, FEE_MODEL_INVALID, context.modelError()));
          }
          fx = context.model() == null ? null : context.model().fx();
        } else {
          Set<String> sources = new HashSet<>();
          for (Securityaccount account : portfolioAccounts) {
            AccountContext context = accountContexts.computeIfAbsent(account.getIdSecuritycashAccount(),
                this::accountContext);
            if (context.modelError() != null) {
              return new TransferMarkup(null,
                  DisposalCostDetail.unknown(name, Part.FX, FEE_MODEL_INVALID, context.modelError()));
            }
            sources.add(fxSourceKey(account, context.model()));
            fx = context.model().fx();
          }
          if (sources.size() != 1) {
            return new TransferMarkup(null, DisposalCostDetail.unknown(name, Part.FX, FX_ACCOUNT_AMBIGUOUS, null));
          }
        }
        FxQuote quote = fxEngine.quote(fx, new FxMarkupRequest(cashaccount.getCurrency(), mainCurrency,
            FxMarkupRequest.Kind.TRANSFER, amount, date, null), tierRate);
        if (quote.outcome() == FxOutcome.MATCHED) {
          return new TransferMarkup(quote.percent(), DisposalCostDetail.matched(name, Part.FX, quote.ruleName(),
              mainCurrency, DataBusinessHelper.roundStandard(amount * quote.percent() / 100.0)));
        }
        return new TransferMarkup(null, DisposalCostDetail.unknown(name, Part.FX, fxReason(quote), fxDetail(quote)));
      } catch (Exception e) {
        log.warn("Disposal transfer markup of cash account {} failed", cashaccount.getId(), e);
        return new TransferMarkup(null, DisposalCostDetail.unknown(name, Part.FX, EVALUATION_ERROR, e.getMessage()));
      }
    }

    /** Identifies where the fx section of an account comes from, so that accounts sharing one plan count as one. */
    private static String fxSourceKey(Securityaccount account, ResolvedFeeModel model) {
      return switch (model.fxSource()) {
      case ACCOUNT -> "A" + account.getIdSecuritycashAccount();
      case PLAN -> "P" + account.getTradingPlatformPlan().getIdTradingPlatformPlan();
      case NONE -> "N";
      };
    }

    private static String fxReason(FxQuote quote) {
      return quote.outcome() == FxOutcome.NO_SECTION ? FX_MODEL_MISSING : FX_RULE_UNMATCHED;
    }

    private static String fxDetail(FxQuote quote) {
      return quote.error() == null ? quote.outcome().name() : quote.outcome().name() + ": " + quote.error();
    }
  }

  /** Sums the components over the security accounts of one position and records why a component is unknown. */
  private static final class Accumulator {
    private Double commission;
    private boolean commissionComplete = true;
    private Double tax;
    private boolean taxComplete = true;
    private Double fxCost;
    private boolean fxComplete = true;
    private double sameCurrencyNet;
    private final List<DisposalCostDetail> details = new ArrayList<>();

    void commission(String name, String rule, double amount) {
      commission = (commission == null ? 0.0 : commission) + amount;
      details.add(DisposalCostDetail.matched(name, Part.COMMISSION, rule, null, amount));
    }

    void tax(double amount) {
      tax = (tax == null ? 0.0 : tax) + amount;
    }

    void fx(double amount) {
      fxCost = (fxCost == null ? 0.0 : fxCost) + amount;
    }

    void unknown(String name, Part part, String reason, String detail) {
      switch (part) {
      case COMMISSION -> commissionComplete = false;
      case TAX -> taxComplete = false;
      case FX -> fxComplete = false;
      }
      details.add(DisposalCostDetail.unknown(name, part, reason, detail));
    }

    void unknownAll(String name, String reason, String detail) {
      for (Part part : Part.values()) {
        unknown(name, part, reason, detail);
      }
    }

    DisposalEstimate toEstimate() {
      return new DisposalEstimate(commission, commissionComplete, tax, taxComplete, fxCost, fxComplete, sameCurrencyNet,
          List.copyOf(details));
    }
  }
}
