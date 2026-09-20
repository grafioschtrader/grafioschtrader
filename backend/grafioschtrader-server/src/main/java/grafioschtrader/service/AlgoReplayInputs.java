package grafioschtrader.service;

import java.time.LocalDate;
import java.util.*;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import grafioschtrader.entities.*;
import grafioschtrader.repository.*;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.CouponDayCount;
import grafioschtrader.types.PeriodDayPosition;
import grafioschtrader.types.RepeatUnit;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TransactionType;
import grafioschtrader.types.WeekendAdjustType;

/** Captures tax, income, instrument, and cash-standing-order inputs before a worker is queued. */
@Service
public class AlgoReplayInputs {
  private final TaxCountryJpaRepository countries;
  private final DividendJpaRepository dividends;
  private final SecuritysplitJpaRepository splits;
  private final StandingOrderJpaRepository standingOrders;
  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  public AlgoReplayInputs(TaxCountryJpaRepository countries, DividendJpaRepository dividends,
      SecuritysplitJpaRepository splits, StandingOrderJpaRepository standingOrders) {
    this.countries = countries;
    this.dividends = dividends;
    this.splits = splits;
    this.standingOrders = standingOrders;
  }

  public record Account(String dealerCountry, Boolean exemptInvestor) {
  }

  public record Split(LocalDate date, Integer from, Integer to) {
  }

  public record Observation(Integer id, LocalDate exDate, LocalDate paymentDate, Double amount, String currency,
      boolean estimated) {
  }

  public record Instrument(String currency, String instrument, String assetclass, String mic, String issuerCountry,
      String exchangeCountry, boolean directBond, Integer denomination, AlgoReplayCouponSchedule.Terms couponTerms,
      String incomeSource, String sourceWarning, List<Observation> observations, List<Split> splits,
      LocalDate activeFromDate, LocalDate activeToDate) {
  }

  /** Frozen account-based standing order used by one replay. */
  public record CashStandingOrder(Integer id, Integer idCashaccount, String cashaccountName, String cashaccountCurrency,
      TransactionType transactionType, Double amount, String amountCurrency, Integer idCurrencypair, String formula,
      Double transactionCost, RepeatUnit repeatUnit, short repeatInterval, Byte dayOfExecution, Byte monthOfExecution,
      PeriodDayPosition periodDayPosition, WeekendAdjustType weekendAdjust, byte quoteToleranceDays,
      LocalDate validFrom, LocalDate validTo, String note) {
  }

  public record Snapshot(int version, boolean applyTaxModels, boolean generateBondCoupons, int dividendDelay,
      Map<String, String> countryModels, Map<Integer, Account> accounts, Map<Integer, Instrument> instruments,
      List<CashStandingOrder> cashStandingOrders, Map<Integer, Float> leverageFactors,
      AlgoReplayAllocation allocation) {
    /** Compatibility constructor for historical fixtures and snapshots without an exclusion policy. */
    public Snapshot(int version, boolean taxes, boolean coupons, int delay, Map<String, String> models,
        Map<Integer, Account> accounts, Map<Integer, Instrument> instruments, List<CashStandingOrder> orders) {
      this(version, taxes, coupons, delay, models, accounts, instruments, orders, Map.of(), null);
    }

    public boolean excluded(Integer security) {
      Instrument input = instruments.get(security);
      if (input == null)
        return false;
      return Security.simulationTradingExcluded(
          input.instrument() == null ? null : SpecialInvestmentInstruments.valueOf(input.instrument()),
          leverageFactors == null ? 1 : leverageFactors.getOrDefault(security, 1f));
    }

    public Snapshot withAllocation(AlgoReplayAllocation effective) {
      return new Snapshot(version, applyTaxModels, generateBondCoupons, dividendDelay, countryModels, accounts,
          instruments, cashStandingOrders, leverageFactors, effective);
    }
  }

  public Snapshot capture(boolean taxes, boolean coupons, int delay, Integer idTenant, Collection<Security> securities,
      List<Securityaccount> accounts, LocalDate opening, LocalDate end) {
    Map<String, String> models = new TreeMap<>();
    if (taxes)
      countries.findAll().forEach(country -> {
        if (country.isHasTaxModel())
          models.put(country.getCountryCode(), country.getTaxModelYaml());
      });
    Map<Integer, Account> accountInputs = new TreeMap<>();
    accounts.forEach(account -> accountInputs.put(account.getId(),
        new Account(account.getTradingPlatformPlan() == null ? null : account.getTradingPlatformPlan().getCountryCode(),
            account.getTaxExemptInvestor())));
    Map<Integer, Instrument> instrumentInputs = new TreeMap<>();
    for (Security security : securities)
      instrumentInputs.put(security.getId(), instrument(security, coupons, delay, opening, end));
    List<CashStandingOrder> cashOrders = standingOrders.findByIdTenant(idTenant).stream()
        .filter(StandingOrderCashaccount.class::isInstance).map(StandingOrderCashaccount.class::cast)
        .map(this::cashStandingOrder).toList();
    Map<Integer, Float> leverage = new TreeMap<>();
    securities.forEach(s -> leverage.put(s.getId(), s.getLeverageFactor()));
    return new Snapshot(3, taxes, coupons, delay, models, accountInputs, instrumentInputs, cashOrders, leverage, null);
  }

  private CashStandingOrder cashStandingOrder(StandingOrderCashaccount order) {
    return new CashStandingOrder(order.getIdStandingOrder(), order.getCashaccount().getId(),
        order.getCashaccount().getName(), order.getCashaccount().getCurrency(), order.getTransactionType(),
        order.getCashaccountAmount(), order.getAmountCurrency(), order.getIdCurrencypair(),
        order.getCashaccountAmountFormula(), order.getTransactionCost(), order.getRepeatUnit(),
        order.getRepeatInterval(), order.getDayOfExecution(), order.getMonthOfExecution(), order.getPeriodDayPosition(),
        order.getWeekendAdjust(), order.getQuoteToleranceDays(), order.getValidFrom(), order.getValidTo(),
        order.getNote());
  }

  /**
   * Resolves the day-count convention of a bond. A convention stored in the bond terms wins; otherwise the usual
   * convention of the bond market of the security currency is used, so that a bond with only a coupon rate still
   * receives generated coupons. Securities saved before the edit dialog proposed a convention, or created by import or
   * GTNet without passing that dialog, depend on this fallback.
   *
   * @param storedTerms the bond terms of the security, may be null
   * @param currency    the currency of the security
   * @return the convention used by the coupon schedule
   */
  static CouponDayCount couponDayCount(SecurityBondTerms storedTerms, String currency) {
    CouponDayCount stored = storedTerms == null ? null : storedTerms.getCouponDayCount();
    return stored != null ? stored : CouponDayCount.defaultForCurrency(currency);
  }

  private Instrument instrument(Security security, boolean generate, int delay, LocalDate opening, LocalDate end) {
    List<Securitysplit> securitySplits = splits.findByIdSecuritycurrencyOrderBySplitDateAsc(security.getId());
    List<Observation> observations = new ArrayList<>();
    for (Dividend dividend : dividends.findByIdSecuritycurrencyOrderByExDateAsc(security.getId())) {
      if (dividend.getExDate() == null)
        throw new IllegalArgumentException("REPLAY_DIVIDEND_INVALID: " + security.getName());
      if (!dividend.getExDate().isAfter(opening) || dividend.getExDate().isAfter(end))
        continue;
      Double amount = dividend.getAmount();
      if (amount == null && dividend.getAmountAdjusted() != null)
        amount = dividend.getAmountAdjusted()
            * Securitysplit.calcSplitFatorForFromDate(securitySplits, dividend.getExDate());
      observations.add(new Observation(dividend.getId(), dividend.getExDate(),
          AlgoReplayDividendService.paymentDate(dividend.getExDate(), dividend.getPayDate(), delay), amount,
          dividend.getCurrency(), dividend.getPayDate() == null));
    }
    observations.sort(Comparator.comparing(Observation::exDate).thenComparing(Observation::id));
    var asset = security.getAssetClass();
    boolean bond = asset != null
        && asset.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT
        && (asset.getCategoryType() == AssetclassType.FIXED_INCOME
            || asset.getCategoryType() == AssetclassType.CONVERTIBLE_BOND);
    SecurityBondTerms storedTerms = security.getSimulationMetadata() == null ? null
        : security.getSimulationMetadata().getBondTerms();
    var terms = bond
        ? new AlgoReplayCouponSchedule.Terms(storedTerms == null ? null : storedTerms.getCouponRate(),
            security.getActiveToDate(), security.getDistributionFrequency().getValue(),
            couponDayCount(storedTerms, security.getCurrency()))
        : null;
    String source = "STORED";
    String warning = bond && !observations.isEmpty() ? "INCOME_STORED_COVERAGE" : null;
    if (bond && observations.isEmpty()) {
      source = "UNAVAILABLE";
      warning = "INCOME_HISTORY_MISSING";
      if (generate) {
        try {
          new AlgoReplayCouponSchedule(terms);
          if (securitySplits.stream().anyMatch(split -> !split.getFromFactor().equals(split.getToFactor())))
            throw new IllegalArgumentException("Coupon schedule cannot be preserved across splits");
          source = "GENERATED";
          warning = null;
        } catch (IllegalArgumentException e) {
          warning = "COUPON_TERMS_UNSUPPORTED";
        }
      }
    }
    var exchange = security.getStockexchange();
    return new Instrument(security.getCurrency(),
        asset == null || asset.getSpecialInvestmentInstrument() == null ? null
            : asset.getSpecialInvestmentInstrument().name(),
        asset == null || asset.getCategoryType() == null ? null : asset.getCategoryType().name(),
        exchange == null ? null : exchange.getMic(), security.getIssuerCountry(),
        exchange == null ? null : exchange.getCountryCode(), bond, security.getDenomination(), terms, source, warning,
        List.copyOf(observations),
        securitySplits.stream()
            .map(split -> new Split(split.getSplitDate(), split.getFromFactor(), split.getToFactor())).toList(),
        security.getActiveFromDate(), security.getActiveToDate());
  }

  public static String write(Object snapshot) {
    try {
      return JSON.writeValueAsString(snapshot);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot persist replay assumptions", e);
    }
  }

  public static Snapshot read(String json) {
    try {
      Snapshot snapshot = JSON.readValue(json, Snapshot.class);
      if (snapshot.version() != 1 && snapshot.version() != 2 && snapshot.version() != 3)
        throw new IllegalArgumentException("Unsupported replay assumption version");
      return snapshot;
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid replay assumptions", e);
    }
  }
}
