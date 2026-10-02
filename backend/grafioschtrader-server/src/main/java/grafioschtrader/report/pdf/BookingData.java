package grafioschtrader.report.pdf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.types.TransactionType;

/**
 * Period bookings and their attribution. Unknown rates propagate through affected sums as NaN, never as zero.
 *
 * @param addedBackTax withholding tax added back to dividend income because the tenant excludes it; it is part of the
 *                     position contributions but was never credited to an account, so the reconciliation removes it
 */
public record BookingData(List<Booking> transactions, List<Position> positions, double fees, double interest,
    double accountTransactionCosts, int missingRates, double addedBackTax) {
  public BookingData {
    transactions = List.copyOf(transactions);
    positions = List.copyOf(positions);
  }

  public record Booking(Transaction transaction, double rate, double amount, double transactionCosts, double taxes) {
  }

  public record Position(Security security, boolean transfer, double income, double withholdingTax,
      double transactionCosts, double transactionTaxes, double financing, double contribution) {
  }

  /** Converts one loaded booking set and joins the union of both valuations and securities booked in the period. */
  public static BookingData calculate(List<Transaction> transactions, HoldingsData opening, HoldingsData closing,
      DateTransactionCurrencypairMap rates, boolean excludeDivTax) {
    Map<Integer, Accumulator> positions = new LinkedHashMap<>();
    addValuation(positions, opening, -1);
    addValuation(positions, closing, 1);
    List<Booking> bookings = new ArrayList<>();
    double fees = 0, interest = 0, accountCosts = 0;
    int missing = 0;
    for (Transaction t : transactions.stream().sorted(Comparator.comparing(Transaction::getTransactionTime)
        .thenComparing(Transaction::getIdTransaction, Comparator.nullsLast(Comparator.naturalOrder()))).toList()) {
      Booking b = convert(t, rates);
      bookings.add(b);
      if (!Double.isFinite(b.amount()) || !Double.isFinite(b.transactionCosts()) || !Double.isFinite(b.taxes())) {
        missing++;
      }
      if (t.getTransactionType() == TransactionType.FEE) {
        fees -= b.amount();
      } else if (t.getTransactionType() == TransactionType.INTEREST_CASHACCOUNT) {
        interest += b.amount();
      }
      if (t.getSecurity() != null) {
        positions.computeIfAbsent(t.getSecurity().getIdSecuritycurrency(), _ -> new Accumulator(t.getSecurity())).add(b,
            excludeDivTax);
      } else {
        accountCosts += b.transactionCosts();
      }
    }
    double addedBackTax = positions.values().stream().mapToDouble(a -> a.addedBackTax).sum();
    var rows = positions.values().stream().map(Accumulator::position)
        .sorted(Comparator.comparing((Position p) -> p.security().getAssetClass().getCategoryType())
            .thenComparing(p -> p.security().getName(), String.CASE_INSENSITIVE_ORDER))
        .toList();
    return new BookingData(bookings, rows, fees, interest, accountCosts, missing, addedBackTax);
  }

  private static void addValuation(Map<Integer, Accumulator> positions, HoldingsData holdings, int sign) {
    if (holdings != null) {
      holdings.positions().stream().filter(p -> !HoldingsData.cash(p))
          .forEach(p -> positions.computeIfAbsent(p.getSecurity().getIdSecuritycurrency(),
              _ -> new Accumulator(p.getSecurity())).contribution += sign * HoldingsData.value(p));
    }
  }

  private static Booking convert(Transaction t, DateTransactionCurrencypairMap rates) {
    boolean accountIncome = t.getTransactionType() == TransactionType.FEE
        || t.getTransactionType() == TransactionType.INTEREST_CASHACCOUNT;
    var date = accountIncome && rates.isUseUntilDateForFeeAndInterest() ? rates.getUntilDate()
        : t.getTransactionDateAsLocalDate();
    String mc = rates.getMainCurrency();
    Double rawRate = t.getCashaccount().getCurrency().equals(mc) ? Double.valueOf(1.0)
        : rates.getPriceByDateAndFromCurrency(date, t.getCashaccount().getCurrency(), false);
    double rate = rawRate != null && Double.isFinite(rawRate) && rawRate > 0 ? rawRate : Double.NaN;
    // Costs and taxes are stored in the instrument currency, cash amounts in the account currency.
    double costs = t.getTransactionCost() == null || t.getTransactionCost() == 0 ? 0
        : -(t.getSecurity() == null ? t.getTransactionCost() * rate : t.getTransactionCostCurrencyExRate(mc, rate));
    double taxes = t.getTaxCost() == null || t.getTaxCost() == 0 ? 0
        : -(t.getSecurity() == null ? t.getTaxCost() * rate : t.getTaxCostExRate(mc, rate));
    return new Booking(t, rate, t.getCashaccountAmount() * rate, costs, taxes);
  }

  /**
   * Positive charges for the ratio; financing credits reduce recorded costs. Withholding tax is disclosed separately.
   */
  public double recordedCosts() {
    return fees - accountTransactionCosts
        - positions.stream().mapToDouble(p -> p.transactionCosts() + p.transactionTaxes() + p.financing()).sum();
  }

  /**
   * Reconciles position contributions and account income, including separately disclosed cash transfer charges, with
   * the credited cash the performance is based on.
   */
  public double attributedGain() {
    return positions.stream().mapToDouble(Position::contribution).sum() - addedBackTax + interest - fees
        + accountTransactionCosts;
  }

  private static final class Accumulator {
    private final Security security;
    private boolean transfer;
    private double income, withholdingTax, transactionCosts, transactionTaxes, financing, contribution, addedBackTax;

    private Accumulator(Security security) {
      this.security = security;
    }

    private void add(Booking b, boolean excludeDivTax) {
      Transaction t = b.transaction();
      transfer |= t.getIdSecurityTransfer() != null;
      transactionCosts += b.transactionCosts();
      switch (t.getTransactionType()) {
      case ACCUMULATE, REDUCE -> {
        // Signed cash also handles short margin positions and losses on closing margin trades.
        contribution += b.amount();
        transactionTaxes += b.taxes();
      }
      case DIVIDEND -> {
        double netIncome = b.amount() - (excludeDivTax ? b.taxes() : 0);
        addedBackTax += excludeDivTax ? -b.taxes() : 0;
        income += netIncome;
        withholdingTax += b.taxes();
        contribution += netIncome;
      }
      case FINANCE_COST -> {
        financing += b.amount();
        contribution += b.amount();
      }
      default -> {
      }
      }
    }

    private Position position() {
      return new Position(security, transfer, income, withholdingTax, transactionCosts, transactionTaxes, financing,
          contribution);
    }
  }
}
