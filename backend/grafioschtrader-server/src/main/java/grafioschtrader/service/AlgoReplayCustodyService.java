package grafioschtrader.service;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import grafiosch.common.DataHelper;
import grafioschtrader.dto.CustodyOpeningState;
import grafioschtrader.entities.*;
import grafioschtrader.exceptions.TransactionLimitExceededException;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.types.AlgoEventType;
import grafioschtrader.types.TransactionType;

/** Connects the pure custody engine to frozen replay inputs and the ordinary fee transaction write path. */
@Service
public class AlgoReplayCustodyService {
  private final AlgoHistoricalValuationService valuation;
  private final TransactionJpaRepository transactions;
  private final AlgoReplayBooking booking;

  public AlgoReplayCustodyService(AlgoHistoricalValuationService valuation, TransactionJpaRepository transactions,
      AlgoReplayBooking booking) {
    this.valuation = valuation;
    this.transactions = transactions;
    this.booking = booking;
  }

  /** Recorded as the assumption when the user stated no explanation for an account's opening balances. */
  static final String ZERO_ASSUMED = "Not stated; missing opening balances are zero";

  /** Validates all account selections and tariff coverage before the existing simulation ledger is restored. */
  public static AlgoReplayInputs.Snapshot capture(AlgoReplayInputs.Snapshot inputs, List<Securityaccount> accounts,
      List<Cashaccount> cashaccounts, String yaml, LocalDate opening, LocalDate end) {
    Map<String, CustodyOpeningState> byName = new HashMap<>();
    if (yaml != null && !yaml.isBlank()) {
      try {
        CustodyOpeningYamlValidator.requireValid(yaml);
        byName = new YAMLMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .readValue(yaml, new TypeReference<Map<String, CustodyOpeningState>>() {
            });
      } catch (Exception e) {
        throw new IllegalArgumentException("REPLAY_CUSTODY_FAILED: Opening YAML: " + e.getMessage());
      }
      if (byName == null)
        throw new IllegalArgumentException("Opening state must be a map");
    }
    Map<Integer, grafioschtrader.dto.FxFeeConfig> fxModels = new TreeMap<>();
    Map<Integer, String> models = new TreeMap<>();
    Map<Integer, CustodyOpeningState> initial = new TreeMap<>();
    Set<String> consumed = new HashSet<>();
    for (Securityaccount account : accounts) {
      var resolved = FeeModelResolver.resolve(account);
      String model = resolved.commissionYaml();
      if (resolved.fx() != null)
        fxModels.put(account.getId(), resolved.fx());
      if (model != null)
        models.put(account.getId(), model);
      var config = CustodyFeeEngine.parse(model);
      if (config == null || account.getActiveToDate() != null && !account.getActiveToDate().isAfter(opening))
        continue;
      CustodyOpeningState given = byName.get(account.getName());
      if (given != null && !consumed.add(account.getName()))
        throw new IllegalArgumentException("Ambiguous securities account name: " + account.getName());
      CustodyOpeningState state = withDefaults(given);
      LocalDate until = account.getActiveToDate() == null || account.getActiveToDate().isAfter(end) ? end
          : account.getActiveToDate();
      CustodyFeeEngine engine;
      try {
        engine = new CustodyFeeEngine(config, opening, until, state);
        if (until.equals(account.getActiveToDate()))
          engine.validateClosing(until);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(account.getName() + ": " + e.getMessage(), e);
      }
      Integer selected = state.cashaccount();
      var candidates = settlementCandidates(account, cashaccounts, selected, engine.currency(opening), until);
      boolean needsCash = engine.requiresSettlement() || selected != null;
      if (needsCash && candidates.size() != 1)
        throw new IllegalArgumentException("REPLAY_CUSTODY_FAILED: " + account.getName()
            + ": select one active settlement cashaccount in the same portfolio");
      var cash = needsCash ? candidates.getFirst() : null;
      Integer cashId = cash == null ? null : cash.getId();
      initial.put(account.getId(), new CustodyOpeningState(cashId, state.accruedFees(), state.remainingCredits(),
          state.billedThisYear(), state.assumption()));
      // An explicit tag establishes overlap; unrelated maintenance standing orders remain untouched.
      if (cash != null && inputs.cashStandingOrders() != null
          && inputs.cashStandingOrders().stream().anyMatch(o -> o.transactionType() == TransactionType.FEE
              && Objects.equals(o.idCashaccount(), cash.getId()) && o.note() != null && o.note().contains("[custody]")))
        throw new IllegalArgumentException(
            "REPLAY_CUSTODY_FAILED: overlapping [custody] standing order: " + account.getName());
    }
    if (!consumed.containsAll(byName.keySet()))
      throw new IllegalArgumentException("Unknown or inactive custody account name in opening state");
    return inputs.withFees(models, initial, fxModels);
  }

  /**
   * Determines the cash accounts that may settle the custody fees of a securities account. Only active accounts of the
   * same portfolio qualify. An account named in the opening YAML is taken as it is. Otherwise the account must carry
   * the fee currency, and the assignment of a cash account to a securities account resolves several accounts of that
   * currency in one portfolio: an account assigned to this securities account wins, else only unassigned accounts
   * remain, because an account assigned to another securities account belongs to that one.
   *
   * @param account  the securities account whose custody fees are settled
   * @param cashaccounts all cash accounts of the simulation tenant
   * @param selected the cash account ID from the opening YAML, or null when none was stated
   * @param currency the custody fee currency at the opening date
   * @param until    the last day the account must still be active
   * @return the qualifying cash accounts; the caller requires exactly one
   */
  private static List<Cashaccount> settlementCandidates(Securityaccount account, List<Cashaccount> cashaccounts,
      Integer selected, String currency, LocalDate until) {
    var active = cashaccounts.stream()
        .filter(c -> c.getPortfolio().getId().equals(account.getPortfolio().getId())
            && (c.getActiveToDate() == null || !c.getActiveToDate().isBefore(until)))
        .toList();
    if (selected != null)
      return active.stream().filter(c -> c.getId().equals(selected)).toList();
    var inCurrency = active.stream().filter(c -> c.getCurrency().equals(currency)).toList();
    var assigned = inCurrency.stream().filter(c -> account.getId().equals(c.getConnectIdSecurityaccount())).toList();
    return assigned.isEmpty() ? inCurrency.stream().filter(c -> c.getConnectIdSecurityaccount() == null).toList()
        : assigned;
  }

  /**
   * Every opening balance is optional, so that a replay can start without any preparation. An account without an entry
   * and a field left out both count as zero: a billing cycle already running at the opening is billed only for its
   * simulated days, it has no commission credit left, and no fees billed earlier in the year count against an annual
   * cap.
   */
  private static CustodyOpeningState withDefaults(CustodyOpeningState given) {
    if (given == null)
      return new CustodyOpeningState(null, 0.0, 0.0, 0.0, ZERO_ASSUMED);
    return new CustodyOpeningState(given.cashaccount(), zeroIfNull(given.accruedFees()),
        zeroIfNull(given.remainingCredits()), zeroIfNull(given.billedThisYear()),
        given.assumption() == null || given.assumption().isBlank() ? ZERO_ASSUMED : given.assumption());
  }

  private static double zeroIfNull(Double value) {
    return value == null ? 0.0 : value;
  }

  Session open(AlgoReplayState state, List<Securityaccount> accounts, List<Cashaccount> cashaccounts) {
    return new Session(state, accounts, cashaccounts);
  }

  /** Per-run mutable state, never shared by the singleton service. */
  public final class Session {
    private final AlgoReplayState state;
    private final Map<Integer, CustodyFeeEngine> engines = new TreeMap<>();
    private final Map<Integer, Securityaccount> accounts = new HashMap<>();
    private final Map<Integer, Cashaccount> cash = new HashMap<>();
    private boolean carriedIn;

    Session(AlgoReplayState state, List<Securityaccount> securities, List<Cashaccount> cashaccounts) {
      this.state = state;
      if (state.inputs.version() < 4)
        return;
      for (Securityaccount account : securities) {
        var initial = state.inputs.custodyOpening().get(account.getId());
        if (initial == null)
          continue;
        var config = CustodyFeeEngine.parse(state.inputs.feeModels().get(account.getId()));
        LocalDate end = account.getActiveToDate() == null || account.getActiveToDate().isAfter(state.run.getEndDate())
            ? state.run.getEndDate()
            : account.getActiveToDate();
        engines.put(account.getId(), new CustodyFeeEngine(config, state.run.getOpeningDate(), end, initial));
        accounts.put(account.getId(), account);
        if (initial.cashaccount() != null)
          cash.put(account.getId(),
              cashaccounts.stream().filter(c -> c.getId().equals(initial.cashaccount())).findFirst().orElseThrow());
      }
    }

    public Set<LocalDate> dates() {
      Set<LocalDate> dates = new TreeSet<>();
      if (!engines.isEmpty())
        for (LocalDate date = state.run.getOpeningDate().plusDays(1); !date.isAfter(state.run.getEndDate()); date = date
            .plusDays(1))
          dates.add(date);
      return dates;
    }

    /** Negative receivables reduce equity without being mistaken for spendable cash. */
    public Map<String, Double> liabilities(LocalDate date) {
      Map<String, Double> result = new HashMap<>();
      engines.forEach((id, engine) -> {
        if (!engine.requiresSettlement())
          return;
        LocalDate capped = effectiveDate(id, date);
        result.merge(engine.currency(capped), 0.0 - engine.liability(capped), Double::sum);
      });
      return result;
    }

    private LocalDate effectiveDate(Integer id, LocalDate date) {
      var close = accounts.get(id).getActiveToDate();
      return close != null && date.isAfter(close) ? close : date;
    }

    void process(LocalDate date, boolean beforeOrders) {
      carryIn();
      // One valuation serves every account of this call. Observation and billing read only the security positions and
      // the exchange rates of the day, and neither changes when a fee or its funding transfer is booked in between:
      // those move cash. The call before and the one after the orders of the day stay separate valuations.
      AlgoHistoricalValuationService.Snapshot[] day = new AlgoHistoricalValuationService.Snapshot[1];
      Supplier<AlgoHistoricalValuationService.Snapshot> valued = () -> {
        if (day[0] == null) {
          day[0] = valuation.value(state.idTenant(), date, state.market);
          requireSnapshot(day[0]);
        }
        return day[0];
      };
      for (var entry : engines.entrySet()) {
        Integer id = entry.getKey();
        if (!effectiveDate(id, date).equals(date))
          continue;
        CustodyFeeEngine engine = entry.getValue();
        boolean closing = !beforeOrders && date.equals(accounts.get(id).getActiveToDate())
            && engine.period(date).collection() == grafioschtrader.dto.CustodyFeeConfig.Collection.END;
        if (engine.settledOn(date))
          continue;
        if (!beforeOrders && (engine.observes(date) || closing)) {
          if (engine.period(date).valuation() == grafioschtrader.dto.CustodyFeeConfig.Valuation.NONE) {
            engine.observe(date, List.of());
          } else {
            var snapshot = valued.get();
            engine.observe(date, holdings(snapshot, id, rate(snapshot, engine.currency(date)), true), closing);
          }
        }
        if (engine.bills(date, beforeOrders) || closing)
          settle(id, engine, date, closing, valued);
      }
      if (!beforeOrders && !engines.isEmpty())
        state.decisionEquity.remove(date);
    }

    /**
     * Hands every engine the positions and the trades of the opening, once, before the first replayed day. A
     * per-exchange fee is due for exchanges held on entry or traded earlier in the opening cycle.
     */
    private void carryIn() {
      if (carriedIn)
        return;
      carriedIn = true;
      if (engines.values().stream().allMatch(e -> e.period(state.run.getOpeningDate().plusDays(1))
          .valuation() == grafioschtrader.dto.CustodyFeeConfig.Valuation.NONE))
        return;
      var snapshot = valuation.value(state.idTenant(), state.run.getOpeningDate(), state.market);
      requireSnapshot(snapshot);
      engines.forEach((id, engine) -> engine.carryIn(holdings(snapshot, id, 1, false)));
      for (Transaction t : state.openingTrades) {
        CustodyFeeEngine engine = engines.get(t.getIdSecurityaccount());
        if (engine != null)
          engine.traded(t.getTransactionDate(), "L:" + t.getIdTransaction(), tradeVariables(t.getSecurity(), null));
      }
    }

    /** Positions of one account; unvalued holdings (value 0) serve only to identify their exchanges. */
    private List<CustodyFeeEngine.Holding> holdings(AlgoHistoricalValuationService.Snapshot snapshot, Integer id,
        double feeFx, boolean valued) {
      return snapshot.positions().stream().filter(p -> id.equals(p.idSecurityaccount())).map(p -> {
        var instrument = instrument(p.security());
        double value = valued ? Math.abs(p.closingValue()) * rate(snapshot, instrument.currency()) / feeFx : 0;
        return new CustodyFeeEngine.Holding(value, text(instrument.instrument()), text(instrument.assetclass()),
            text(instrument.isin()), instrument.currency(), text(instrument.mic()));
      }).toList();
    }

    private AlgoReplayInputs.Instrument instrument(Security security) {
      var instrument = state.inputs.instruments().get(security.getId());
      if (instrument == null)
        throw new IllegalArgumentException("REPLAY_CUSTODY_FAILED: Missing frozen instrument " + security.getId());
      return instrument;
    }

    /** Variables of a traded instrument for credit eligibility and the exchange condition. */
    private Map<String, Object> tradeVariables(Security security, TransactionType type) {
      var instrument = instrument(security);
      Map<String, Object> variables = new HashMap<>(
          Map.of("instrument", text(instrument.instrument()), "assetclass", text(instrument.assetclass()), "isin",
              text(instrument.isin()), "currency", instrument.currency(), "mic", text(instrument.mic())));
      if (type != null)
        variables.put("tradeDirection", type == TransactionType.ACCUMULATE ? 0 : 1);
      return variables;
    }

    private void settle(Integer id, CustodyFeeEngine engine, LocalDate date, boolean closing,
        Supplier<AlgoHistoricalValuationService.Snapshot> valued) {
      var bill = engine.bill(date, state.precision(engine.currency(date)), closing);
      Cashaccount target = cash.get(id);
      if (bill.total() > 0) {
        var snapshot = valued.get();
        double amount = bill.total() * rate(snapshot, bill.currency()) / rate(snapshot, target.getCurrency());
        Transaction fee = new Transaction();
        fee.setIdTenant(state.idTenant());
        fee.setCashaccount(target);
        fee.setIdSecurityaccount(id);
        fee.setTransactionType(TransactionType.FEE);
        fee.setTransactionTime(date.atTime(
            engine.period(date).collection() == grafioschtrader.dto.CustodyFeeConfig.Collection.START ? 0 : 23, 0));
        // FEE write requests carry a positive expense; the repository stores its negative cash impact.
        fee.setCashaccountAmount(DataHelper.round(amount, state.precision(target.getCurrency())));
        fee.setAlgoFillId(state.run.getIdSimulationResult() + ":C:" + id + ":" + date);
        fee.setNote("[custody] " + accounts.get(id).getName());
        try {
          booking.fundCustody(state, id, target, date, fee.getCashaccountAmount());
          transactions.throwWhenTransactionLimitReached(state.idTenant(), 1);
          transactions.saveOnlyAttributes(fee, null, Set.of());
        } catch (TransactionLimitExceededException | AlgoReplayFx.Failure e) {
          throw e;
        } catch (Exception e) {
          throw new IllegalArgumentException("REPLAY_CUSTODY_FAILED: " + accounts.get(id).getName() + ": " + reason(e),
              e);
        }
      }
      engine.settled(bill);
      state.write(AlgoEventType.CUSTODY_FEE, date, null, null, null, null, -bill.total(), bill.currency(),
          "REPLAY_CUSTODY_FEE", accounts.get(id).getName() + "; " + bill.details());
    }

    /**
     * Readable reason of a refused custody charge. The overdraft refusal of the write path carries no message of its
     * own - a {@code DataViolationException} keeps its reasons in its violation list - so it is resolved in the
     * language of the run's user; a refusal the replay raised itself keeps its key.
     */
    private String reason(Exception e) {
      String details = booking.detailsOf(e, state.locale);
      return details != null ? details : e.getMessage();
    }

    public record Discount(double netCommission, double credit) {
    }

    Discount discount(Integer id, Security security, TransactionType type, LocalDate date, double commission) {
      CustodyFeeEngine engine = engines.get(id);
      if (engine == null || commission == 0 || engine.period(date).creditAmount() == null
          || engine.period(date).creditAmount() == 0)
        return new Discount(commission, 0);
      var snapshot = valuation.value(state.idTenant(), date, state.market, Set.of(security.getCurrency()));
      requireSnapshot(snapshot);
      double conversion = rate(snapshot, security.getCurrency()) / rate(snapshot, engine.currency(date));
      double credit = engine.credit(date, commission * conversion, tradeVariables(security, type));
      double net = DataHelper.round(Math.max(0, commission - credit / conversion),
          state.precision(security.getCurrency()));
      return new Discount(net, Math.min(credit, Math.max(0, (commission - net) * conversion)));
    }

    void committed(Transaction fill, double credit) {
      CustodyFeeEngine engine = engines.get(fill.getIdSecurityaccount());
      if (engine == null)
        return;
      LocalDate date = fill.getTransactionDate();
      engine.traded(date, fill.getAlgoFillId(), tradeVariables(fill.getSecurity(), fill.getTransactionType()));
      if (credit == 0)
        return;
      engine.consume(date, fill.getAlgoFillId(), credit);
      state.write(AlgoEventType.CUSTODY_CREDIT, date, null, fill.getSecurity().getId(), null, null, credit,
          engine.currency(date), "REPLAY_CUSTODY_CREDIT", accounts.get(fill.getIdSecurityaccount()).getName());
    }
  }

  private static String text(String value) {
    return value == null ? "" : value;
  }

  private static void requireSnapshot(AlgoHistoricalValuationService.Snapshot snapshot) {
    if (!snapshot.errors().isEmpty())
      throw new IllegalArgumentException("REPLAY_CUSTODY_FAILED: Missing valuation " + snapshot.errors());
  }

  private static double rate(AlgoHistoricalValuationService.Snapshot snapshot, String currency) {
    Double value = snapshot.fx().get(currency);
    if (value == null || !Double.isFinite(value) || value <= 0)
      throw new IllegalArgumentException("REPLAY_CUSTODY_FAILED: Missing FX " + currency);
    return value;
  }
}
