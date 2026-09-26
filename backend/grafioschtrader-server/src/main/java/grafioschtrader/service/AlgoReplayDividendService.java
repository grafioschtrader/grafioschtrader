package grafioschtrader.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.DividendJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.SimulationSourceRepository;
import grafioschtrader.types.AlgoEventType;
import grafioschtrader.types.TransactionType;

/**
 * Earns gross dividend income independently of when it is paid. Each replay owns a session: shared reference data is
 * copied once, entitlements are derived from the simulated ledger before the ex-date, and a sale cannot erase a claim.
 * Receivables never enter cash balances. Only the caller's ordinary transaction writer can settle them.
 */
@Service
public class AlgoReplayDividendService {
  private static final double EPSILON = 1e-8;
  private final DividendJpaRepository dividends;
  private final SecuritysplitJpaRepository splits;
  private final SimulationSourceRepository source;

  public AlgoReplayDividendService(DividendJpaRepository dividends, SecuritysplitJpaRepository splits,
      SimulationSourceRepository source) {
    this.dividends = dividends;
    this.splits = splits;
    this.source = source;
  }

  /** Immutable provider observation; the fallback is captured, never written back to shared dividend data. */
  public record Distribution(Integer id, Security security, LocalDate exDate, LocalDate payDate, Double amount,
      String currency, boolean estimated, grafioschtrader.dto.TaxEstimateRequest.EventKind kind, boolean generated) {
    public Distribution(Integer id, Security security, LocalDate exDate, LocalDate payDate, Double amount,
        String currency, boolean estimated) {
      this(id, security, exDate, payDate, amount, currency, estimated,
          grafioschtrader.dto.TaxEstimateRequest.EventKind.DIVIDEND, false);
    }
  }

  /** One account's entitlement, expressed in dividend currency and in units on the ex-date's split basis. */
  public record Claim(Distribution distribution, Integer securityaccount, Cashaccount cashaccount, double units) {
    public double amount() {
      return units * distribution.amount();
    }

    public String identity() {
      return (distribution.generated() ? "C:" + distribution.security().getId() + ":" + distribution.payDate()
          : distribution.id()) + ":" + securityaccount + ":" + cashaccount.getId();
    }
  }

  @FunctionalInterface
  public interface Payer {
    /** Returns the actual payment converted to tenant currency; must throw when the booking was refused. */
    double pay(Claim claim) throws Exception;
  }

  /** Loads provider data and split factors once. Missing history is disclosed by the run's conventions. */
  @Transactional(readOnly = true)
  public Session open(Integer tenant, Collection<Security> securities, String currency, LocalDate opening,
      LocalDate end, int delay) {
    List<Distribution> observations = new ArrayList<>();
    Map<Integer, List<Securitysplit>> splitMap = new LinkedHashMap<>();
    for (Security security : securities) {
      List<Securitysplit> securitySplits = splits.findByIdSecuritycurrencyOrderBySplitDateAsc(security.getId());
      splitMap.put(security.getId(), securitySplits);
      for (Dividend dividend : dividends.findByIdSecuritycurrencyOrderByExDateAsc(security.getId())) {
        if (dividend.getExDate() == null)
          throw new IllegalArgumentException("REPLAY_DIVIDEND_INVALID: " + security.getName());
        if (!dividend.getExDate().isAfter(opening) || dividend.getExDate().isAfter(end))
          continue;
        Double amount = dividend.getAmount();
        if (amount == null && dividend.getAmountAdjusted() != null)
          amount = dividend.getAmountAdjusted()
              * Securitysplit.calcSplitFatorForFromDate(securitySplits, dividend.getExDate());
        observations.add(new Distribution(dividend.getId(), security, dividend.getExDate(),
            paymentDate(dividend.getExDate(), dividend.getPayDate(), delay), amount, dividend.getCurrency(),
            dividend.getPayDate() == null));
      }
    }
    return new Session(tenant, currency, opening, end, observations, splitMap, source.cashaccounts(tenant));
  }

  /** Opens exclusively from submission-time observations; shared income history is never reloaded by the worker. */
  public Session open(Integer tenant, Collection<Security> securities, String currency, LocalDate opening,
      LocalDate end, AlgoReplayInputs.Snapshot inputs) {
    List<Distribution> observations = new ArrayList<>();
    Map<Integer, List<Securitysplit>> splitMap = new LinkedHashMap<>();
    for (Security security : securities) {
      var input = inputs.instruments().get(security.getId());
      if (input == null)
        continue;
      splitMap.put(security.getId(), input.splits().stream().map(split -> new Securitysplit(security.getId(),
          split.date(), split.from(), split.to(), grafioschtrader.types.CreateType.ADD_MODIFIED_USER)).toList());
      var kind = input.directBond() ? grafioschtrader.dto.TaxEstimateRequest.EventKind.SECURITY_INTEREST
          : grafioschtrader.dto.TaxEstimateRequest.EventKind.DIVIDEND;
      for (var observation : input.observations()) {
        observations.add(new Distribution(observation.id(), security, observation.exDate(), observation.paymentDate(),
            observation.amount(), observation.currency(), observation.estimated(), kind, false));
      }
      if ("GENERATED".equals(input.incomeSource())) {
        var schedule = new AlgoReplayCouponSchedule(input.couponTerms());
        LocalDate through = end;
        if (input.activeToDate() != null && input.activeToDate().isBefore(through)) {
          through = input.activeToDate();
        }
        // A failed issuer pays no further coupon; recorded distributions above are real data and stay untouched.
        if (input.tradingEndDate() != null && !input.tradingEndDate().isAfter(through)) {
          through = input.tradingEndDate().minusDays(1);
        }
        for (LocalDate date : schedule.payments(opening, through))
          observations.add(
              new Distribution(null, security, date, date, schedule.coupon(1), input.currency(), false, kind, true));
      }
    }
    return new Session(tenant, currency, opening, end, observations, splitMap, source.cashaccounts(tenant));
  }

  static LocalDate paymentDate(LocalDate exDate, LocalDate payDate, int delay) {
    return payDate == null ? exDate.plusDays(delay) : payDate;
  }

  /** Mutable state belongs to one worker, not to the singleton service. Successful payments alone retire claims. */
  public final class Session {
    private final Integer tenant;
    private final String currency;
    private final LocalDate end;
    private final Map<LocalDate, List<Distribution>> observations = new TreeMap<>();
    private final TreeSet<LocalDate> dates = new TreeSet<>();
    private final Map<Integer, List<Securitysplit>> splitMap;
    private final List<Cashaccount> accounts;
    private final List<Claim> claims = new ArrayList<>();
    private final Map<String, LocalDate> payments = new LinkedHashMap<>();
    private double paidTotal;
    private LocalDate processed;

    Session(Integer tenant, String currency, LocalDate opening, LocalDate end, List<Distribution> distributions,
        Map<Integer, List<Securitysplit>> splitMap, List<Cashaccount> accounts) {
      this.tenant = tenant;
      this.currency = currency;
      this.processed = opening;
      this.end = end;
      this.splitMap = splitMap;
      this.accounts = accounts;
      for (Distribution distribution : distributions) {
        observations.computeIfAbsent(distribution.exDate(), _ -> new ArrayList<>()).add(distribution);
        dates.add(distribution.exDate());
        if (!distribution.payDate().isBefore(distribution.exDate()) && !distribution.payDate().isAfter(end))
          dates.add(distribution.payDate());
      }
    }

    /** Processes every due calendar event, even if no strategy evaluates on its date. */
    public void processThrough(LocalDate through, Payer payer, BiConsumer<AlgoEventType, Claim> audit)
        throws Exception {
      if (through.isBefore(processed) || through.isAfter(end))
        throw new IllegalArgumentException("Dividend replay date outside its processing interval");
      for (LocalDate date : new ArrayList<>(dates.subSet(processed, false, through, true))) {
        for (Distribution distribution : observations.getOrDefault(date, List.of())) {
          for (Claim claim : entitlement(distribution)) {
            claims.add(claim);
            audit.accept(AlgoEventType.DIVIDEND_ENTITLEMENT, claim);
          }
        }
        for (Claim claim : claims) {
          if (claim.distribution().payDate().equals(date) && !payments.containsKey(claim.identity())) {
            paidTotal += payer.pay(claim);
            payments.put(claim.identity(), date);
            audit.accept(AlgoEventType.DIVIDEND_PAYMENT, claim);
          }
        }
      }
      processed = through;
    }

    /** Historical receivables, never cash; paid claims stay available for earlier-date valuation. */
    public Map<String, Double> receivables(LocalDate asOf) {
      Map<String, Double> totals = new LinkedHashMap<>();
      for (Claim claim : claims) {
        LocalDate paid = payments.get(claim.identity());
        if (!claim.distribution().exDate().isAfter(asOf) && (paid == null || paid.isAfter(asOf)))
          totals.merge(claim.distribution().currency(), claim.amount(), Double::sum);
      }
      return totals;
    }

    public double paidTotal() {
      return paidTotal;
    }

    public List<Claim> unpaidClaims(LocalDate asOf) {
      return claims.stream().filter(claim -> !claim.distribution().exDate().isAfter(asOf)
          && (payments.get(claim.identity()) == null || payments.get(claim.identity()).isAfter(asOf))).toList();
    }

    /** The transaction validator expresses dividend units on the payment date's split basis. */
    public double paymentUnits(Claim claim) {
      Distribution distribution = claim.distribution();
      return claim.units() * Securitysplit.calcSplitFatorForFromDateAndToDate(distribution.security().getId(),
          distribution.exDate(), distribution.payDate().plusDays(1), splitMap).fromToDateFactor;
    }

    private List<Claim> entitlement(Distribution distribution) {
      // This query has an exclusive upper bound. Tomorrow's fills and all ex-date fills are ineligible.
      List<Transaction> ledger = source.transactions(tenant, distribution.exDate());
      Map<Integer, Map<Integer, Double>> units = new TreeMap<>();
      for (Transaction transaction : ledger) {
        if (transaction.getSecurity() == null
            || !transaction.getSecurity().getId().equals(distribution.security().getId())
            || transaction.getIdSecurityaccount() == null
            || !transaction.getTransactionDate().isBefore(distribution.exDate()))
          continue;
        TransactionType type = transaction.getTransactionType();
        if (type != TransactionType.ACCUMULATE && type != TransactionType.REDUCE)
          continue;
        // Splits on the ex-date change the unit basis, but do not confer entitlement on new buyers.
        double factor = Securitysplit.calcSplitFatorForFromDateAndToDate(distribution.security().getId(),
            transaction.getTransactionDate(), distribution.exDate().plusDays(1), splitMap).fromToDateFactor;
        units.computeIfAbsent(transaction.getIdSecurityaccount(), _ -> new TreeMap<>()).merge(
            transaction.getCashaccount().getId(),
            transaction.getUnits() * factor * (type == TransactionType.ACCUMULATE ? 1 : -1), Double::sum);
      }
      List<Claim> result = new ArrayList<>();
      for (var account : units.entrySet()) {
        double total = account.getValue().values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(total) < EPSILON)
          continue;
        validate(distribution, total);
        if (distribution.amount() == 0)
          continue;
        if (account.getValue().values().stream().allMatch(q -> q >= -EPSILON)) {
          account.getValue().forEach((cash, quantity) -> {
            if (quantity > EPSILON)
              result.add(new Claim(distribution, account.getKey(), cashaccount(cash), quantity));
          });
        } else {
          // Purchases and reductions used different settlement accounts: retain the security account and portfolio.
          Cashaccount original = cashaccount(account.getValue().keySet().iterator().next());
          Cashaccount destination = accounts.stream()
              .filter(a -> a.getPortfolio().getId().equals(original.getPortfolio().getId()))
              .sorted(Comparator.comparingInt((Cashaccount a) -> a.getCurrency().equals(distribution.currency()) ? 0
                  : a.getCurrency().equals(currency) ? 1 : 2).thenComparing(Cashaccount::getId))
              .findFirst().orElseThrow(() -> new IllegalArgumentException("REPLAY_NO_ACCOUNT"));
          result.add(new Claim(distribution, account.getKey(), destination, total));
        }
      }
      return result;
    }

    private Cashaccount cashaccount(Integer id) {
      return accounts.stream().filter(a -> a.getId().equals(id)).findFirst()
          .orElseThrow(() -> new IllegalArgumentException("REPLAY_NO_ACCOUNT"));
    }
  }

  private static void validate(Distribution distribution, double units) {
    if (units < 0 || distribution.security().isMarginInstrument())
      throw new IllegalArgumentException("REPLAY_MARGIN_UNSUPPORTED");
    if (distribution.amount() == null || !Double.isFinite(distribution.amount()) || distribution.amount() < 0
        || !Double.isFinite(units * distribution.amount()) || distribution.currency() == null
        || !distribution.currency().matches("[A-Z]{3}") || distribution.payDate().isBefore(distribution.exDate()))
      throw new IllegalArgumentException("REPLAY_DIVIDEND_INVALID: " + distribution.security().getName());
  }
}
