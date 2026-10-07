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

/** Captures tax, income, instrument, and standing-order inputs before a worker is queued. */
@Service
public class AlgoReplayInputs {
  /**
   * Version a newly captured snapshot is written with. Version 6 adds the security standing orders and the borrowing
   * rates of the cash accounts; a snapshot of an earlier version has neither, which its readers treat as empty. A new
   * component goes at the end of the canonical constructor, the previous canonical constructor stays as a compatibility
   * constructor, and the version is raised once a release has written snapshots of the current one.
   */
  public static final int SNAPSHOT_VERSION = 6;

  private final TaxCountryJpaRepository countries;
  private final DividendJpaRepository dividends;
  private final SecuritysplitJpaRepository splits;
  private final StandingOrderJpaRepository standingOrders;
  private final BankruptSecurityJpaRepository bankruptSecurities;
  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  public AlgoReplayInputs(TaxCountryJpaRepository countries, DividendJpaRepository dividends,
      SecuritysplitJpaRepository splits, StandingOrderJpaRepository standingOrders,
      BankruptSecurityJpaRepository bankruptSecurities) {
    this.countries = countries;
    this.dividends = dividends;
    this.splits = splits;
    this.standingOrders = standingOrders;
    this.bankruptSecurities = bankruptSecurities;
  }

  public record Account(String dealerCountry, Boolean exemptInvestor) {
  }

  public record Split(LocalDate date, Integer from, Integer to) {
  }

  public record Observation(Integer id, LocalDate exDate, LocalDate paymentDate, Double amount, String currency,
      boolean estimated) {
  }

  /**
   * Frozen description of one instrument for one replay.
   *
   * @param tradingEndDate first day on which the instrument can no longer be traded, captured from
   *                       {@code bankrupt_security.no_trading_since}. From that day on the replay neither buys nor sells
   *                       it, generates no further coupon and does not repay it at maturity: an exchange and a broker
   *                       stop supporting the instrument of a failed issuer, and nothing is paid on its schedule. The
   *                       position stays held and is valued at the last price, as everywhere else in the application.
   *                       Null for every instrument without such a record and for runs recorded before the field
   *                       existed, which therefore replay unchanged.
   */
  public record Instrument(String currency, String instrument, String assetclass, String mic, String issuerCountry,
      String exchangeCountry, boolean directBond, Integer denomination, AlgoReplayCouponSchedule.Terms couponTerms,
      String incomeSource, String sourceWarning, List<Observation> observations, List<Split> splits,
      LocalDate activeFromDate, LocalDate activeToDate, String isin, LocalDate tradingEndDate) {
    public Instrument(String currency, String instrument, String assetclass, String mic, String issuerCountry,
        String exchangeCountry, boolean directBond, Integer denomination, AlgoReplayCouponSchedule.Terms couponTerms,
        String incomeSource, String sourceWarning, List<Observation> observations, List<Split> splits,
        LocalDate activeFromDate, LocalDate activeToDate, String isin) {
      this(currency, instrument, assetclass, mic, issuerCountry, exchangeCountry, directBond, denomination, couponTerms,
          incomeSource, sourceWarning, observations, splits, activeFromDate, activeToDate, isin, null);
    }

    public Instrument(String currency, String instrument, String assetclass, String mic, String issuerCountry,
        String exchangeCountry, boolean directBond, Integer denomination, AlgoReplayCouponSchedule.Terms couponTerms,
        String incomeSource, String sourceWarning, List<Observation> observations, List<Split> splits,
        LocalDate activeFromDate, LocalDate activeToDate) {
      this(currency, instrument, assetclass, mic, issuerCountry, exchangeCountry, directBond, denomination, couponTerms,
          incomeSource, sourceWarning, observations, splits, activeFromDate, activeToDate, null, null);
    }

    /**
     * @param date a day of the replay
     * @return true when the issuer has failed by that day, so that the instrument can no longer be traded
     */
    public boolean tradingStopped(LocalDate date) {
      return tradingEndDate != null && !date.isBefore(tradingEndDate);
    }
  }

  /** Frozen account-based standing order used by one replay. */
  public record CashStandingOrder(Integer id, Integer idCashaccount, String cashaccountName, String cashaccountCurrency,
      TransactionType transactionType, Double amount, String amountCurrency, Integer idCurrencypair, String formula,
      Double transactionCost, RepeatUnit repeatUnit, short repeatInterval, Byte dayOfExecution, Byte monthOfExecution,
      PeriodDayPosition periodDayPosition, WeekendAdjustType weekendAdjust, byte quoteToleranceDays,
      LocalDate validFrom, LocalDate validTo, String note) {
  }

  /**
   * Frozen security standing order used by one replay. Its costs are the fixed values or formulas of the standing order
   * itself, as in the live execution, and not the fee model of the security account.
   *
   * @param securityCurrency currency of the instrument, in which quotation and costs are expressed
   * @param idCurrencypair   pair converting the security currency into the cash account currency, null when both
   *                         currencies are equal
   */
  public record SecurityStandingOrder(Integer id, Integer idSecurity, Integer idSecurityaccount, Integer idCashaccount,
      String cashaccountName, String cashaccountCurrency, String securityCurrency, TransactionType transactionType,
      Double units, Double investAmount, boolean amountIncludesCosts, boolean fractionalUnits, Double taxCost,
      String taxCostFormula, Double transactionCost, String transactionCostFormula, Integer idCurrencypair,
      RepeatUnit repeatUnit, short repeatInterval, Byte dayOfExecution, Byte monthOfExecution,
      PeriodDayPosition periodDayPosition, WeekendAdjustType weekendAdjust, byte quoteToleranceDays,
      LocalDate validFrom, LocalDate validTo, String note) {
  }

  public record Snapshot(int version, boolean applyTaxModels, boolean generateBondCoupons, int dividendDelay,
      Map<String, String> countryModels, Map<Integer, Account> accounts, Map<Integer, Instrument> instruments,
      List<CashStandingOrder> cashStandingOrders, Map<Integer, Float> leverageFactors, AlgoReplayAllocation allocation,
      Map<Integer, String> feeModels, Map<Integer, grafioschtrader.dto.CustodyOpeningState> custodyOpening,
      Map<Integer, grafioschtrader.dto.FxFeeConfig> fxModels, List<SecurityStandingOrder> securityStandingOrders,
      Map<Integer, Double> borrowingRates) {
    public Snapshot(int version, boolean taxes, boolean coupons, int delay, Map<String, String> models,
        Map<Integer, Account> accounts, Map<Integer, Instrument> instruments, List<CashStandingOrder> orders,
        Map<Integer, Float> leverage, AlgoReplayAllocation allocation, Map<Integer, String> fees,
        Map<Integer, grafioschtrader.dto.CustodyOpeningState> opening, Map<Integer, grafioschtrader.dto.FxFeeConfig> fx,
        List<SecurityStandingOrder> securityOrders) {
      this(version, taxes, coupons, delay, models, accounts, instruments, orders, leverage, allocation, fees, opening, fx,
          securityOrders, Map.of());
    }

    public Snapshot(int version, boolean taxes, boolean coupons, int delay, Map<String, String> models,
        Map<Integer, Account> accounts, Map<Integer, Instrument> instruments, List<CashStandingOrder> orders,
        Map<Integer, Float> leverage, AlgoReplayAllocation allocation, Map<Integer, String> fees,
        Map<Integer, grafioschtrader.dto.CustodyOpeningState> opening,
        Map<Integer, grafioschtrader.dto.FxFeeConfig> fx) {
      this(version, taxes, coupons, delay, models, accounts, instruments, orders, leverage, allocation, fees, opening, fx,
          List.of());
    }

    public Snapshot(int version, boolean taxes, boolean coupons, int delay, Map<String, String> models,
        Map<Integer, Account> accounts, Map<Integer, Instrument> instruments, List<CashStandingOrder> orders,
        Map<Integer, Float> leverage, AlgoReplayAllocation allocation, Map<Integer, String> fees,
        Map<Integer, grafioschtrader.dto.CustodyOpeningState> opening) {
      this(version, taxes, coupons, delay, models, accounts, instruments, orders, leverage, allocation, fees, opening,
          Map.of());
    }

    public Snapshot(int version, boolean taxes, boolean coupons, int delay, Map<String, String> models,
        Map<Integer, Account> accounts, Map<Integer, Instrument> instruments, List<CashStandingOrder> orders,
        Map<Integer, Float> leverage, AlgoReplayAllocation allocation) {
      this(version, taxes, coupons, delay, models, accounts, instruments, orders, leverage, allocation, Map.of(),
          Map.of());
    }

    public Snapshot withFees(Map<Integer, String> models,
        Map<Integer, grafioschtrader.dto.CustodyOpeningState> opening) {
      return withFees(models, opening, Map.of());
    }

    public Snapshot withFees(Map<Integer, String> models, Map<Integer, grafioschtrader.dto.CustodyOpeningState> opening,
        Map<Integer, grafioschtrader.dto.FxFeeConfig> fx) {
      return new Snapshot(SNAPSHOT_VERSION, applyTaxModels, generateBondCoupons, dividendDelay, countryModels, accounts,
          instruments, cashStandingOrders, leverageFactors, allocation, models, opening, fx, securityStandingOrders,
          borrowingRates);
    }

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
          instruments, cashStandingOrders, leverageFactors, effective, feeModels, custodyOpening, fxModels,
          securityStandingOrders, borrowingRates);
    }

    /**
     * @return the annual borrowing rate in percent by cash account id, only for accounts whose rate is above zero; empty
     *         for a snapshot captured before the rates were frozen
     */
    public Map<Integer, Double> borrowingRatesOrEmpty() {
      return borrowingRates == null ? Map.of() : borrowingRates;
    }

    /** @return the frozen security standing orders, empty for a snapshot captured before they existed */
    public List<SecurityStandingOrder> securityStandingOrdersOrEmpty() {
      return securityStandingOrders == null ? List.of() : securityStandingOrders;
    }
  }

  public Snapshot capture(boolean taxes, boolean coupons, int delay, Integer idTenant, Collection<Security> securities,
      List<Securityaccount> accounts, List<Cashaccount> cashaccounts, LocalDate opening, LocalDate end) {
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
    // Only the trading stop counts, not the day the prices stopped: an instrument may still be quoted while an
    // exchange and the brokers no longer trade it. A record without that date gives no day to stop trading on.
    Map<Integer, LocalDate> tradingEnd = new HashMap<>();
    bankruptSecurities.findAll().stream().filter(b -> b.getNoTradingSince() != null)
        .forEach(b -> tradingEnd.put(b.getIdSecuritycurrency(), b.getNoTradingSince()));
    Map<Integer, Instrument> instrumentInputs = new TreeMap<>();
    for (Security security : securities)
      instrumentInputs.put(security.getId(),
          instrument(security, coupons, delay, opening, end, tradingEnd.get(security.getId())));
    List<StandingOrder> tenantOrders = standingOrders.findByIdTenant(idTenant);
    List<CashStandingOrder> cashOrders = tenantOrders.stream().filter(StandingOrderCashaccount.class::isInstance)
        .map(StandingOrderCashaccount.class::cast).map(this::cashStandingOrder).toList();
    List<SecurityStandingOrder> securityOrders = tenantOrders.stream()
        .filter(StandingOrderSecurity.class::isInstance).map(StandingOrderSecurity.class::cast)
        .map(AlgoReplayInputs::securityStandingOrder).toList();
    Map<Integer, Float> leverage = new TreeMap<>();
    securities.forEach(s -> leverage.put(s.getId(), s.getLeverageFactor()));
    // An account with a rate of zero may be overdrawn but costs nothing, so only a positive rate is frozen.
    Map<Integer, Double> borrowingRates = new TreeMap<>();
    cashaccounts.stream().filter(c -> c.getBorrowingRate() != null && c.getBorrowingRate() > 0)
        .forEach(c -> borrowingRates.put(c.getId(), c.getBorrowingRate()));
    return new Snapshot(SNAPSHOT_VERSION, taxes, coupons, delay, models, accountInputs, instrumentInputs, cashOrders,
        leverage, null, Map.of(), Map.of(), Map.of(), securityOrders, borrowingRates);
  }

  private static SecurityStandingOrder securityStandingOrder(StandingOrderSecurity order) {
    return new SecurityStandingOrder(order.getIdStandingOrder(), order.getSecurity().getId(),
        order.getIdSecurityaccount(), order.getCashaccount().getId(), order.getCashaccount().getName(),
        order.getCashaccount().getCurrency(), order.getSecurity().getCurrency(), order.getTransactionType(),
        order.getUnits(), order.getInvestAmount(), order.isAmountIncludesCosts(), order.isFractionalUnits(),
        order.getTaxCost(), order.getTaxCostFormula(), order.getTransactionCost(), order.getTransactionCostFormula(),
        order.getIdCurrencypair(), order.getRepeatUnit(), order.getRepeatInterval(), order.getDayOfExecution(),
        order.getMonthOfExecution(), order.getPeriodDayPosition(), order.getWeekendAdjust(),
        order.getQuoteToleranceDays(), order.getValidFrom(), order.getValidTo(), order.getNote());
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

  /**
   * @param security the instrument
   * @return true for a directly held fixed income or convertible bond, the instruments that accrue coupon interest
   */
  static boolean isDirectBond(Security security) {
    var asset = security.getAssetClass();
    return asset != null && asset.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT
        && (asset.getCategoryType() == AssetclassType.FIXED_INCOME
            || asset.getCategoryType() == AssetclassType.CONVERTIBLE_BOND);
  }

  /**
   * Builds the regular coupon terms of a directly held bond from its coupon rate, maturity, distribution frequency and
   * day-count convention. The replay generates coupons from them, and the disposal cost estimate of the reports
   * derives the accrued interest of a hypothetical sale from them.
   *
   * @param security the instrument
   * @return the terms, null when the instrument is not a direct bond; the terms may still be incomplete, which
   *         {@link AlgoReplayCouponSchedule} rejects
   */
  static AlgoReplayCouponSchedule.Terms couponTerms(Security security) {
    if (!isDirectBond(security))
      return null;
    SecurityBondTerms storedTerms = security.getSimulationMetadata() == null ? null
        : security.getSimulationMetadata().getBondTerms();
    return new AlgoReplayCouponSchedule.Terms(storedTerms == null ? null : storedTerms.getCouponRate(),
        security.getActiveToDate(), security.getDistributionFrequency().getValue(),
        couponDayCount(storedTerms, security.getCurrency()));
  }

  private Instrument instrument(Security security, boolean generate, int delay, LocalDate opening, LocalDate end,
      LocalDate tradingEndDate) {
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
    boolean bond = isDirectBond(security);
    var terms = couponTerms(security);
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
        security.getActiveFromDate(), security.getActiveToDate(), security.getIsin(), tradingEndDate);
  }

  public static String write(Object snapshot) {
    try {
      return JSON.writeValueAsString(snapshot);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot persist replay assumptions", e);
    }
  }

  /**
   * Reads the equity curve a completed run stored with {@link #write}.
   *
   * @param json the stored curve, null for a run without one
   * @return the points of the curve, empty for null
   */
  public static List<SimulationRunEquityPoint> readEquitySeries(String json) {
    if (json == null) {
      return List.of();
    }
    try {
      return List.of(JSON.readValue(json, SimulationRunEquityPoint[].class));
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid replay equity series", e);
    }
  }

  public static Snapshot read(String json) {
    try {
      Snapshot snapshot = JSON.readValue(json, Snapshot.class);
      if (snapshot.version() < 1 || snapshot.version() > SNAPSHOT_VERSION)
        throw new IllegalArgumentException("Unsupported replay assumption version");
      return snapshot;
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid replay assumptions", e);
    }
  }
}
