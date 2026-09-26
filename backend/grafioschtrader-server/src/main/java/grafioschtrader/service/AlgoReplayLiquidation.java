package grafioschtrader.service;

import java.time.LocalDate;
import java.util.*;

import org.springframework.stereotype.Service;

import grafioschtrader.entities.*;
import grafioschtrader.repository.SimulationSourceRepository;
import grafioschtrader.types.AlgoEventType;
import grafioschtrader.types.CreateType;
import grafioschtrader.types.TransactionType;

/** Opening-only liquidation of excluded instruments, kept separate from ordinary replay order eligibility. */
@Service
public class AlgoReplayLiquidation {
  private final SimulationSourceRepository source;
  private final AlgoReplayCalendar calendar;
  private final AlgoReplayBooking booking;

  public AlgoReplayLiquidation(SimulationSourceRepository source, AlgoReplayCalendar calendar,
      AlgoReplayBooking booking) {
    this.source = source;
    this.calendar = calendar;
    this.booking = booking;
  }

  /** A margin close retains its opening lot; an ordinary sale represents the whole custody position. */
  record Close(Security security, Integer account, Cashaccount cash, double units, Transaction opening) {
  }

  /** Resolves execution dates before ordinary trading begins. Missing data never becomes a fictitious closure. */
  NavigableMap<LocalDate, List<Close>> schedule(AlgoReplayState state) {
    var schedule = new TreeMap<LocalDate, List<Close>>();
    var ledger = source.transactions(state.idTenant(), state.run.getOpeningDate().plusDays(1));
    for (Security security : state.securities.values().stream().sorted(Comparator.comparing(Security::getId))
        .toList()) {
      if (!state.market.tradingExcluded(security))
        continue;
      List<Securitysplit> splits = splits(state, security);
      var openingPositions = positions(ledger, security, state.run.getOpeningDate(), splits);
      if (openingPositions.isEmpty())
        continue;
      LocalDate cursor = state.run.getOpeningDate();
      boolean scheduled = false;
      var input = state.inputs.instruments().get(security.getId());
      while (cursor.isBefore(state.run.getEndDate())) {
        var next = calendar.nextEligibleClose(security, cursor, state.run.getEndDate(),
            input == null ? null : input.activeFromDate(), input == null ? null : input.activeToDate(), false);
        if (next.isEmpty())
          break;
        cursor = next.get();
        Double price = state.market.exactClose(security.getId(), cursor);
        if (price == null || !Double.isFinite(price) || price <= 0)
          continue;
        List<Close> closes = positions(ledger, security, cursor, splits);
        LocalDate date = cursor;
        if (closes.stream().anyMatch(c -> !booking.hasLiquidationFx(state, security, c.cash(), date)))
          continue;
        schedule.computeIfAbsent(cursor, _ -> new ArrayList<>()).addAll(closes);
        scheduled = true;
        break;
      }
      if (!scheduled)
        throw failure(state, security, state.run.getOpeningDate(),
            "REPLAY_NO_LIQUIDATION_DATE " + openingPositions.stream().map(
                c -> "account=" + c.account() + " opening=" + (c.opening() == null ? "position" : c.opening().getId()))
                .toList());
    }
    return schedule;
  }

  void execute(AlgoReplayState state, LocalDate date, List<Close> closes) {
    for (Close close : closes) {
      try {
        Transaction fill = booking.closeExcluded(state, close, date);
        state.write(AlgoEventType.OPENING_EXCLUDED_CLOSE, date, null, close.security().getId(), fill.getUnits(),
            fill.getQuotation(), fill.getCashaccountAmount(), fill.getCashaccount().getCurrency(),
            "REPLAY_OPENING_LIQUIDATION", state.fx.details(fill.getAlgoFillId(), "account=" + close.account()
                + " opening=" + (close.opening() == null ? "position" : close.opening().getId())));
      } catch (AlgoReplayFx.Failure e) {
        throw e;
      } catch (Exception e) {
        String detail = booking.detailsOf(e, state.locale);
        throw failure(state, close.security(), date,
            "account=" + close.account() + " opening="
                + (close.opening() == null ? "position" : close.opening().getId()) + " "
                + AlgoReplayBooking.rationaleOf(e) + (detail == null ? "" : ": " + detail));
      }
    }
  }

  private IllegalStateException failure(AlgoReplayState state, Security security, LocalDate date, String reason) {
    String detail = security.getName() + " (" + security.getId() + "): " + reason;
    state.write(AlgoEventType.UNAVAILABLE, date, null, security.getId(), null, null, null, null,
        "REPLAY_LIQUIDATION_FAILED", detail);
    return new IllegalStateException("REPLAY_LIQUIDATION_FAILED: " + detail);
  }

  private static List<Securitysplit> splits(AlgoReplayState state, Security security) {
    var input = state.inputs.instruments().get(security.getId());
    return input == null ? List.of()
        : input.splits().stream()
            .map(s -> new Securitysplit(security.getId(), s.date(), s.from(), s.to(), CreateType.ADD_MODIFIED_USER))
            .toList();
  }

  /** Reconstructs remaining lots on the execution day's split basis; opposing margin lots never cancel each other. */
  static List<Close> positions(List<Transaction> ledger, Security security, LocalDate date,
      List<Securitysplit> splits) {
    if (!security.isMarginInstrument())
      return AlgoHistoricalReplayService.terminalHoldings(ledger, security, date, splits).stream()
          .map(p -> new Close(security, p.idSecurityaccount(), p.cashaccount(), p.units(), null)).toList();
    List<Transaction> trades = ledger.stream().filter(t -> t.getSecurity() != null
        && security.getId().equals(t.getSecurity().getId()) && !t.getTransactionDate().isAfter(date)
        && (t.getTransactionType() == TransactionType.ACCUMULATE || t.getTransactionType() == TransactionType.REDUCE))
        .toList();
    Map<Integer, List<Securitysplit>> splitMap = Map.of(security.getId(), splits);
    List<Close> result = new ArrayList<>();
    for (Transaction opening : trades.stream().filter(t -> t.getConnectedIdTransaction() == null)
        .sorted(Comparator.comparing(Transaction::getId)).toList()) {
      double units = adjustedUnits(opening, date, splitMap);
      units -= trades.stream().filter(t -> opening.getId().equals(t.getConnectedIdTransaction()))
          .mapToDouble(t -> adjustedUnits(t, date, splitMap)).sum();
      if (units > 1e-8)
        result.add(new Close(security, opening.getIdSecurityaccount(), opening.getCashaccount(), units, opening));
    }
    return result;
  }

  private static double adjustedUnits(Transaction transaction, LocalDate date,
      Map<Integer, List<Securitysplit>> splits) {
    return transaction.getUnits() * Securitysplit.calcSplitFatorForFromDateAndToDate(transaction.getSecurity().getId(),
        transaction.getTransactionDate(), date.plusDays(1), splits).fromToDateFactor;
  }
}
