package grafioschtrader.service;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import grafiosch.common.DataHelper;
import grafioschtrader.dto.TaxEstimateRequest.EventKind;
import grafioschtrader.dto.TaxEstimateResult;
import grafioschtrader.dto.TaxIncomeSummaryDto.IncomeTotals;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.repository.SimulationSourceRepository;
import grafioschtrader.types.AlgoEventType;
import grafioschtrader.types.TransactionType;

/** Coordinates income entitlement, settlement and audit independently of trading decisions. */
@Service
public class AlgoReplayIncomeService {
  private final AlgoReplayDividendService dividends;
  private final AlgoReplayIncomeBookingService booking;
  private final SimulationSourceRepository source;
  private final MessageSource messageSource;

  public AlgoReplayIncomeService(AlgoReplayDividendService dividends, AlgoReplayIncomeBookingService booking,
      SimulationSourceRepository source, MessageSource messageSource) {
    this.dividends = dividends;
    this.booking = booking;
    this.source = source;
    this.messageSource = messageSource;
  }

  /** Persistence and the run's event budget belong to the coordinator. */
  public record Audit(AlgoEventType type, LocalDate date, Integer security, Double units, Double price, Double amount,
      String currency, String rationale, String details) {
  }

  public Session open(AlgoReplayIncomeBookingService.Context context, Collection<Security> securities,
      LocalDate opening, LocalDate end, int delay, int reportingPrecision, Locale locale, Consumer<Audit> audit) {
    return new Session(context, dividends.open(context.idTenant(), securities, context.currency(), opening, end, delay),
        audit, null, null, opening, reportingPrecision, locale);
  }

  public Session open(AlgoReplayIncomeBookingService.Context context, Collection<Security> securities,
      LocalDate opening, LocalDate end, AlgoReplayInputs.Snapshot inputs, AlgoReplayTaxes taxes, int reportingPrecision,
      Locale locale, Consumer<Audit> audit) {
    return new Session(context,
        dividends.open(context.idTenant(), securities, context.currency(), opening, end, inputs), audit, inputs, taxes,
        opening, reportingPrecision, locale);
  }

  private record Estimate(double grossInstrument, TaxEstimateResult tax) {
  }

  /** Claims and successful-payment totals belong exclusively to one run. */
  public final class Session {
    private final AlgoReplayIncomeBookingService.Context context;
    private final AlgoReplayDividendService.Session claims;
    private final Consumer<Audit> audit;
    private final AlgoReplayInputs.Snapshot inputs;
    private final AlgoReplayTaxes taxes;
    private final Set<Integer> generatedCouponSecurities;
    private Map<String, NavigableMap<LocalDate, Double>> reportingHoldings;
    private final int reportingPrecision;
    /** The language the audit text of an income row is written in; it names dates and accounts, so it is not a key. */
    private final Locale locale;
    /** Names of the securities accounts of the environment, read once and only when an income row is written. */
    private Map<Integer, String> securityaccountNames;
    private final Map<EventKind, double[]> paid = new EnumMap<>(EventKind.class);

    private Session(AlgoReplayIncomeBookingService.Context context, AlgoReplayDividendService.Session claims,
        Consumer<Audit> audit, AlgoReplayInputs.Snapshot inputs, AlgoReplayTaxes taxes, LocalDate opening,
        int reportingPrecision, Locale locale) {
      this.locale = locale;
      this.context = context;
      this.claims = claims;
      this.audit = audit;
      this.inputs = inputs;
      this.taxes = taxes;
      this.generatedCouponSecurities = inputs == null ? Set.of()
          : inputs.instruments().entrySet().stream()
              .filter(entry -> "GENERATED".equals(entry.getValue().incomeSource())).map(Map.Entry::getKey)
              .collect(java.util.stream.Collectors.toUnmodifiableSet());
      this.reportingPrecision = reportingPrecision;
      if (inputs != null) {
        inputs.instruments().forEach((id, instrument) -> {
          if ((inputs.applyTaxModels() || inputs.generateBondCoupons()) && instrument.sourceWarning() != null)
            taxes.record(new TaxEstimateResult.Warning(instrument.sourceWarning(), null, "incomeWithholding", null), id,
                null, opening, "SOURCE");
        });
      }
    }

    public void processThrough(LocalDate date) {
      try {
        claims.processThrough(date, this::pay, (type, claim) -> {
          if (taxes != null && type == AlgoEventType.DIVIDEND_ENTITLEMENT) {
            var estimate = estimate(claim, claim.distribution().exDate());
            taxes.committed(estimate.tax(), claim.distribution().security().getId(), claim.securityaccount(),
                claim.distribution().exDate(), "ENTITLEMENT:" + claim.identity());
          }
          audit.accept(toAudit(type, claim));
        });
      } catch (Exception e) {
        audit.accept(new Audit(AlgoEventType.UNAVAILABLE, date, null, null, null, null, null,
            "REPLAY_DIVIDEND_UNAVAILABLE", diagnostic(e)));
        throw new IllegalStateException("REPLAY_DIVIDEND_UNAVAILABLE", e);
      }
    }

    /**
     * Describes one entitlement or payment for the details column of the trail. The row itself carries the instrument,
     * the units and the amount; what it cannot show is which kind of income it is, the two dates it hangs between and
     * the two accounts it belongs to. Those are data rather than a key, so the text is resolved here instead of being
     * translated on the client, in the language of the user the run belongs to.
     *
     * @param type  whether the row records the entitlement on the ex-date or the payment
     * @param claim the entitlement of one securities account
     * @return the audit row of that claim
     */
    private Audit toAudit(AlgoEventType type, AlgoReplayDividendService.Claim claim) {
      var distribution = claim.distribution();
      String details = messageSource.getMessage("REPLAY_INCOME_AUDIT",
          new Object[] { messageSource.getMessage(distribution.kind().name(), null, distribution.kind().name(), locale),
              distribution.exDate(), distribution.payDate(), securityaccountName(claim.securityaccount()),
              claim.cashaccount().getName() },
          null, locale);
      return new Audit(type,
          type == AlgoEventType.DIVIDEND_ENTITLEMENT ? distribution.exDate() : distribution.payDate(),
          distribution.security().getId(), claim.units(), distribution.amount(), claim.amount(),
          distribution.currency(), distribution.estimated() ? "REPLAY_DIVIDEND_ESTIMATED_DATE" : null, details);
    }

    /**
     * The name of a securities account of the environment, or its id where the account is not among them, which only a
     * ledger written outside the run can produce.
     *
     * @param idSecurityaccount the account the entitlement belongs to
     * @return what the trail names that account by
     */
    private String securityaccountName(Integer idSecurityaccount) {
      if (securityaccountNames == null) {
        securityaccountNames = new HashMap<>();
        for (Securityaccount securityaccount : source.securityaccounts(context.idTenant()))
          securityaccountNames.put(securityaccount.getId(), securityaccount.getName());
      }
      return securityaccountNames.getOrDefault(idSecurityaccount, String.valueOf(idSecurityaccount));
    }

    private String diagnostic(Exception failure) {
      return failure.getMessage() == null || failure.getMessage().isBlank() ? failure.toString() : failure.getMessage();
    }

    private double pay(AlgoReplayDividendService.Claim claim) {
      var distribution = claim.distribution();
      var estimate = estimate(claim, distribution.payDate());
      var payment = booking.pay(context, claim, claims.paymentUnits(claim), estimate.tax().estimatedTax());
      double[] totals = paid.computeIfAbsent(distribution.kind(), _ -> new double[3]);
      totals[0] += payment.gross();
      totals[1] += payment.withholding();
      totals[2] += payment.net();
      if (taxes != null)
        taxes.committed(estimate.tax(), distribution.security().getId(), claim.securityaccount(),
            distribution.payDate(), "PAYMENT:" + claim.identity());
      return payment.net();
    }

    private Estimate estimate(AlgoReplayDividendService.Claim claim, LocalDate fxDate) {
      var distribution = claim.distribution();
      if (taxes == null || !inputs.applyTaxModels())
        return new Estimate(claim.amount(),
            new TaxEstimateResult(0, distribution.currency(), true, List.of(), List.of()));
      double gross = booking.convertDividend(context, claim.amount(), distribution.currency(),
          distribution.security().getCurrency(), fxDate);
      double units = claims.paymentUnits(claim);
      return new Estimate(gross, taxes.estimate(distribution.security().getId(), claim.securityaccount(),
          distribution.kind(), distribution.payDate(), units, gross / units, 0, gross));
    }

    public Map<String, Double> receivables(LocalDate date) {
      Map<String, Double> result = claimReceivables(date, null, false);
      accrued(date).net().forEach((currency, amount) -> result.merge(currency, amount, Double::sum));
      return result;
    }

    /**
     * Called only after the last booking. Index generated-coupon units by effective date once so the final report does
     * not reload the ledger for each historical day. During trading every accrual still reads current holdings.
     */
    void freezeForReporting(LocalDate through) {
      if (reportingHoldings != null)
        return;
      reportingHoldings = new LinkedHashMap<>();
      if (generatedCouponSecurities.isEmpty())
        return;
      for (var transaction : source.transactions(context.idTenant(), through.plusDays(1))) {
        if (!isGeneratedTrade(transaction))
          continue;
        String key = transaction.getSecurity().getId() + ":" + transaction.getIdSecurityaccount();
        reportingHoldings.computeIfAbsent(key, _ -> new TreeMap<>()).merge(transaction.getTransactionDateAsLocalDate(),
            signedUnits(transaction), Double::sum);
      }
      reportingHoldings.values().forEach(changes -> {
        double total = 0;
        for (var entry : changes.entrySet()) {
          total += entry.getValue();
          entry.setValue(total);
        }
      });
    }

    public Map<String, Double> dividendReceivables(LocalDate date) {
      return claimReceivables(date, EventKind.DIVIDEND, false);
    }

    private Map<String, Double> claimReceivables(LocalDate date, EventKind kind, boolean gross) {
      Map<String, Double> result = new TreeMap<>();
      for (var claim : claims.unpaidClaims(date)) {
        var distribution = claim.distribution();
        if (kind != null && distribution.kind() != kind)
          continue;
        double amount = claim.amount();
        if (!gross && taxes != null && inputs.applyTaxModels()) {
          double withholding = estimate(claim, date).tax().estimatedTax();
          amount -= booking.convertDividend(context, withholding, distribution.security().getCurrency(),
              distribution.currency(), date);
        }
        result.merge(distribution.currency(), amount, Double::sum);
      }
      return result;
    }

    /**
     * Generated bond accrual uses the same gross convention as trade accrued interest, net of estimated withholding.
     */
    private Accrual accrued(LocalDate date) {
      Accrual result = new Accrual(new TreeMap<>(), new TreeMap<>());
      // Stored coupons and instruments without supported coupon terms cannot accrue generated interest. Most
      // portfolios contain only these, so avoid loading their entire ledger for an inevitably empty result.
      if (generatedCouponSecurities.isEmpty())
        return result;
      Map<String, Double> holdings = new LinkedHashMap<>();
      if (reportingHoldings != null) {
        reportingHoldings.forEach((key, series) -> {
          var entry = series.floorEntry(date);
          if (entry != null)
            holdings.put(key, entry.getValue());
        });
      } else {
        for (var transaction : source.transactions(context.idTenant(), date.plusDays(1))) {
          if (!isGeneratedTrade(transaction))
            continue;
          String key = transaction.getSecurity().getId() + ":" + transaction.getIdSecurityaccount();
          holdings.merge(key, signedUnits(transaction), Double::sum);
        }
      }
      holdings.forEach((key, units) -> {
        if (units <= 0)
          return;
        String[] ids = key.split(":");
        int security = Integer.parseInt(ids[0]), account = Integer.parseInt(ids[1]);
        var instrument = inputs.instruments().get(security);
        var schedule = new AlgoReplayCouponSchedule(instrument.couponTerms());
        double amount = schedule.accrued(units, date);
        result.gross().merge(instrument.currency(), amount, Double::sum);
        if (amount > 0 && inputs.applyTaxModels()) {
          double coupon = schedule.coupon(units);
          var estimate = taxes.estimate(security, account, EventKind.SECURITY_INTEREST, schedule.nextPayment(date),
              units, coupon / units, 0, coupon);
          if (coupon > 0)
            amount *= 1 - estimate.estimatedTax() / coupon;
        }
        result.net().merge(instrument.currency(), amount, Double::sum);
      });
      return result;
    }

    private boolean isGeneratedTrade(grafioschtrader.entities.Transaction transaction) {
      return transaction.getSecurity() != null && transaction.getIdSecurityaccount() != null
          && generatedCouponSecurities.contains(transaction.getSecurity().getId())
          && (transaction.getTransactionType() == TransactionType.ACCUMULATE
              || transaction.getTransactionType() == TransactionType.REDUCE);
    }

    private double signedUnits(grafioschtrader.entities.Transaction transaction) {
      return transaction.getUnits() * (transaction.getTransactionType() == TransactionType.ACCUMULATE ? 1 : -1);
    }

    public double paidTotal() {
      return paid.getOrDefault(EventKind.DIVIDEND, new double[3])[2];
    }

    public List<IncomeTotals> summary(LocalDate date) {
      List<IncomeTotals> result = new ArrayList<>();
      for (var kind : List.of(EventKind.DIVIDEND, EventKind.SECURITY_INTEREST)) {
        double[] payment = paid.getOrDefault(kind, new double[3]);
        var gross = claimReceivables(date, kind, true);
        var net = claimReceivables(date, kind, false);
        if (kind == EventKind.SECURITY_INTEREST) {
          // Gross and net describe the same holdings; compute both from one fresh read, without retaining a ledger
          // across calls in which the replay may have booked a trade or a redemption.
          Accrual accrual = accrued(date);
          accrual.gross().forEach((currency, amount) -> gross.merge(currency, amount, Double::sum));
          accrual.net().forEach((currency, amount) -> net.merge(currency, amount, Double::sum));
        }
        double grossPaid = round(payment[0]);
        double withholdingPaid = round(payment[1]);
        double netPaid = round(payment[2]);
        double grossReport = round(reporting(gross, date));
        double netReport = round(reporting(net, date));
        result.add(new IncomeTotals(kind.name(), context.currency(), grossPaid, withholdingPaid, netPaid, grossReport,
            round(grossReport - netReport), netReport, round(netPaid - (grossPaid - withholdingPaid))));
      }
      return List.copyOf(result);
    }

    private double reporting(Map<String, Double> totals, LocalDate date) {
      return totals.entrySet().stream()
          .mapToDouble(
              entry -> booking.convertDividend(context, entry.getValue(), entry.getKey(), context.currency(), date))
          .sum();
    }

    private double round(double value) {
      return DataHelper.round(value, reportingPrecision);
    }
  }

  private record Accrual(Map<String, Double> gross, Map<String, Double> net) {
  }

}
