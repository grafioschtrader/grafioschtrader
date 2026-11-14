package grafioschtrader.tax.germany;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import grafiosch.common.DataHelper;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.germantax.GermanTaxDetail;
import grafioschtrader.reportviews.germantax.GermanTaxDetail.LotPart;
import grafioschtrader.reportviews.germantax.GermanTaxKeys;
import grafioschtrader.reportviews.germantax.GermanTaxSum;
import grafioschtrader.reportviews.germantax.GermanTaxUnclassified;
import grafioschtrader.reportviews.germantax.GermanTaxWarning;
import grafioschtrader.reportviews.germantax.GermanTaxYearReport;
import grafioschtrader.tax.germany.GermanTaxClassifier.Classification;
import grafioschtrader.tax.germany.GermanTaxClassifier.Kind;
import grafioschtrader.tax.germany.GermanTaxMarketData.Distribution;
import grafioschtrader.tax.germany.GermanTaxMarketData.PricePoint;
import grafioschtrader.types.FundTaxClass;
import grafioschtrader.types.TaxWithholding;
import grafioschtrader.types.TransactionType;

/**
 * Derives the starting values of a tax year for the German tax return from the transactions of a tenant: the gains of
 * sales, the income and the advance lump sums of funds, always in euro and always by FIFO per security account. It is
 * no tax calculation.
 *
 * <p>
 * The calculation runs beside the one of {@code SecurityGeneralCalc}, because it moves lots between security accounts
 * and securities, values everything in euro and applies advance lump sums to lots. It runs once over all transactions
 * from the oldest on, and the report keeps only the sales and income of the tax year.
 * </p>
 *
 * <ul>
 * <li>A security account transfer and an ISIN change are one event: the lots of the source move with their purchase
 * time, cost in euro and applied advance lump sums, and no gain arises (BMF letter on individual questions of the
 * flat-rate withholding tax: Rn. 67 for the ISIN change, Rn. 184a for the transferred acquisition data).</li>
 * <li>A sale gains the proceeds after its costs at the rate of the day of the sale, less the cost in euro of the lots
 * it consumed (§ 20 Abs. 4 EStG). The gain of a fund is reduced by the advance lump sums of the units sold (§ 19 Abs.
 * 1 InvStG).</li>
 * <li>The advance lump sum of a fund is calculated at each year end for the units then held and counts as income of the
 * following year (§ 18 InvStG).</li>
 * <li>Purchases before 1 January 2009 (shares and claims) and 1 January 2018 (funds) follow transition rules and appear
 * only in the block LEGACY (§ 52 EStG, § 56 InvStG).</li>
 * </ul>
 *
 * <p>
 * A gap, such as a missing exchange rate, base interest rate or price, never turns into a substitute value. The lot or
 * the booking becomes incomplete, the sums it feeds stay without an amount and a warning names the cause.
 * </p>
 */
public class GermanTaxLotEngine {

  private static final double EPS = 1e-7;
  /** The advance lump sum exists since 2018. */
  private static final int FIRST_PREPAYMENT_YEAR = 2018;
  private static final LocalDate LEGACY_CUTOFF_SHARES = LocalDate.of(2009, 1, 1);
  private static final LocalDate LEGACY_CUTOFF_FUNDS = LocalDate.of(2018, 1, 1);
  private static final double BASE_INCOME_FACTOR = 0.7;
  private static final double FOREIGN_TAX_CEILING = 0.25;

  private final GermanTaxMarketData market;
  private final GermanTaxPortfolios portfolios;
  private final Map<Integer, List<Securitysplit>> splitMap;
  private final LocalDate today;

  /**
   * @param market     closing rates, prices, distributions and base interest rates
   * @param portfolios the portfolios of the tenant and their withholding
   * @param splitMap   the splits by security id, to put the units of every purchase on the basis of today
   * @param today      the current day of the client, the tax year of this day is provisional
   */
  public GermanTaxLotEngine(GermanTaxMarketData market, GermanTaxPortfolios portfolios,
      Map<Integer, List<Securitysplit>> splitMap, LocalDate today) {
    this.market = market;
    this.portfolios = portfolios;
    this.splitMap = splitMap;
    this.today = today;
  }

  /**
   * The first working day of a year, on which the advance lump sum of the previous year counts as received (§ 18
   * Abs. 3 InvStG). The 1 January is a holiday, Saturday and Sunday are no working days. For 2027 it is 4 January.
   *
   * @param year the calendar year
   * @return the day
   */
  public static LocalDate firstWorkingDay(int year) {
    LocalDate day = LocalDate.of(year, 1, 2);
    while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
      day = day.plusDays(1);
    }
    return day;
  }

  /**
   * Calculates the report of a tax year.
   *
   * @param taxYear              the tax year, normally between 2018 and the current year
   * @param securityTransactions all purchases, sales and dividends of the tenant that belong to a security, the
   *                             transfer and ISIN change bookings included
   * @param cashInterest         the interest bookings of the cash accounts
   * @return the sums, warnings, unclassified bookings and the derivation
   */
  public GermanTaxYearReport calculate(int taxYear, List<Transaction> securityTransactions,
      List<Transaction> cashInterest) {
    return new Run(taxYear).execute(securityTransactions, cashInterest);
  }

  // ---------------------------------------------------------------------------------------------------------------

  private enum EventType {
    BUY, SELL, DIVIDEND, MOVE, SWAP
  }

  /** What happens at a point of time. A move and an ISIN change carry their purchase as partner. */
  private record Event(EventType type, Transaction tx, Transaction partner) {
  }

  private enum Target {
    FORM, WITHHELD, UNREVIEWED
  }

  private record BookKey(Integer idSecurityaccount, Integer idSecuritycurrency) {
  }

  /** The lots of one security in one security account, oldest first. */
  private static final class Book {
    final Security security;
    final List<GermanTaxLot> lots = new ArrayList<>();

    Book(Security security) {
      this.security = security;
    }

    double units() {
      return lots.stream().mapToDouble(lot -> lot.units).sum();
    }

    void add(GermanTaxLot lot) {
      int index = lots.size();
      while (index > 0 && order(lots.get(index - 1), lot) > 0) {
        index--;
      }
      lots.add(index, lot);
    }

    private static int order(GermanTaxLot a, GermanTaxLot b) {
      final int byTime = a.acquisitionTime.compareTo(b.acquisitionTime);
      if (byTime != 0) {
        return byTime;
      }
      return Comparator.nullsLast(Comparator.<Integer>naturalOrder()).compare(a.idTransaction, b.idTransaction);
    }

    /** Takes the oldest units first. The lots it hands out are no longer part of the book. */
    Consumption consume(double units) {
      final List<GermanTaxLot> parts = new ArrayList<>();
      double remaining = units;
      while (!lots.isEmpty() && remaining > EPS) {
        final GermanTaxLot lot = lots.getFirst();
        if (lot.units <= remaining + EPS) {
          lots.removeFirst();
          parts.add(lot);
          remaining -= lot.units;
        } else {
          parts.add(lot.split(remaining));
          remaining = 0.0;
        }
      }
      return new Consumption(parts, remaining > EPS ? remaining : 0.0);
    }
  }

  private record Consumption(List<GermanTaxLot> parts, double uncovered) {
  }

  /** An exchange rate to euro and where it came from. */
  private record RateResult(Double rate, String source) {
  }

  /** The advance lump sum of one unit of a fund for one year, or the gap that prevents it. */
  private record Prepayment(double perUnit, GermanTaxWarning problem) {
  }

  /** What a sale took from the lots, split into the part of transition purchases and the regular part. */
  private static final class SaleGroup {
    final List<GermanTaxLot> parts = new ArrayList<>();
    double uncoveredUnits;

    double units() {
      return parts.stream().mapToDouble(lot -> lot.units).sum() + uncoveredUnits;
    }

    boolean isEmpty() {
      return parts.isEmpty() && uncoveredUnits <= EPS;
    }
  }

  private final class Run {
    final int taxYear;
    final Map<BookKey, Book> books = new LinkedHashMap<>();
    final Map<Integer, Classification> classifications = new HashMap<>();
    final Map<String, Prepayment> prepayments = new HashMap<>();
    final Map<String, GermanTaxSum> sums = new LinkedHashMap<>();
    final List<GermanTaxWarning> warnings = new ArrayList<>();
    final List<GermanTaxDetail> details = new ArrayList<>();
    final List<GermanTaxUnclassified> unclassified = new ArrayList<>();
    final List<Event> pending = new ArrayList<>();
    int nextYearEnd = FIRST_PREPAYMENT_YEAR;

    Run(int taxYear) {
      this.taxYear = taxYear;
    }

    GermanTaxYearReport execute(List<Transaction> securityTransactions, List<Transaction> cashInterest) {
      final List<Transaction> marginTransactions = new ArrayList<>();
      final List<Event> events = buildEvents(securityTransactions, marginTransactions);
      java.time.LocalDateTime groupTime = null;
      for (Event event : events) {
        final int year = event.tx().getTransactionTime().getYear();
        if (year > taxYear) {
          break;
        }
        if (groupTime != null && !event.tx().getTransactionTime().equals(groupTime)) {
          flushPending();
        }
        advanceYearEnds(year);
        groupTime = event.tx().getTransactionTime();
        process(event);
        retryPending(false);
      }
      flushPending();
      advanceYearEnds(taxYear);
      marginTransactions.forEach(this::marginClose);
      cashInterest.forEach(this::cashInterest);
      return buildReport();
    }

    // ------------------------------------------------------------------------------------------ events

    private List<Event> buildEvents(List<Transaction> all, List<Transaction> marginTransactions) {
      final List<Transaction> sorted = new ArrayList<>();
      for (Transaction t : all) {
        if (t.getSecurity() == null || t.getIdSecurityaccount() == null) {
          continue;
        }
        if (t.getSecurity().isMarginInstrument()) {
          if (t.isMarginClosePosition() && t.getTransactionTime().getYear() == taxYear) {
            marginTransactions.add(t);
          }
        } else if (t.getTransactionType() == TransactionType.ACCUMULATE
            || t.getTransactionType() == TransactionType.REDUCE || t.getTransactionType() == TransactionType.DIVIDEND) {
          sorted.add(t);
        }
      }
      sorted.sort(Transaction.CALC_ORDER);
      final Map<Integer, Transaction> buyByTransfer = new HashMap<>();
      final Map<Integer, Transaction> byId = new HashMap<>();
      for (Transaction t : sorted) {
        byId.put(t.getIdTransaction(), t);
        if (t.getTransactionType() == TransactionType.ACCUMULATE && t.getIdSecurityTransfer() != null) {
          buyByTransfer.put(t.getIdSecurityTransfer(), t);
        }
      }
      final Set<Transaction> partners = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
      final Map<Transaction, Event> pairEvents = new IdentityHashMap<>();
      for (Transaction t : sorted) {
        if (t.getTransactionType() != TransactionType.REDUCE) {
          continue;
        }
        Event pair = null;
        if (t.getIdSecurityTransfer() != null) {
          final Transaction buy = buyByTransfer.get(t.getIdSecurityTransfer());
          if (buy != null && buy.getSecurity().getIdSecuritycurrency().equals(t.getSecurity().getIdSecuritycurrency())
              && !partners.contains(buy)) {
            pair = new Event(EventType.MOVE, t, buy);
          }
        } else if (t.getIdSecurityActionApp() != null && t.getConnectedIdTransaction() != null) {
          final Transaction buy = byId.get(t.getConnectedIdTransaction());
          if (buy != null && buy.getTransactionType() == TransactionType.ACCUMULATE
              && t.getIdSecurityActionApp().equals(buy.getIdSecurityActionApp()) && !partners.contains(buy)) {
            pair = new Event(EventType.SWAP, t, buy);
          }
        }
        if (pair != null) {
          partners.add(pair.partner());
          pairEvents.put(t, pair);
        }
      }
      final List<Event> events = new ArrayList<>();
      for (Transaction t : sorted) {
        if (partners.contains(t)) {
          continue;
        }
        final Event pair = pairEvents.get(t);
        if (pair != null) {
          events.add(pair);
        } else {
          events.add(new Event(plainType(t), t, null));
        }
      }
      return events;
    }

    private EventType plainType(Transaction t) {
      if (t.getIdSecurityTransfer() != null || t.getIdSecurityActionApp() != null) {
        if (t.getTransactionType() != TransactionType.DIVIDEND && t.getTransactionTime().getYear() == taxYear) {
          warnings.add(warn(GermanTaxKeys.WARN_UNPAIRED_BOOKING, t, null));
        }
      }
      return switch (t.getTransactionType()) {
      case ACCUMULATE -> EventType.BUY;
      case REDUCE -> EventType.SELL;
      default -> EventType.DIVIDEND;
      };
    }

    private void process(Event event) {
      final Transaction t = event.tx();
      final Classification cl = classification(t.getSecurity());
      switch (event.type()) {
      case BUY -> {
        if (cl.kind() != Kind.UNCLASSIFIED) {
          buy(t, cl);
        }
      }
      case SELL -> sell(t, cl);
      case DIVIDEND -> dividend(t, cl);
      case MOVE, SWAP -> {
        if (cl.kind() == Kind.UNCLASSIFIED
            && classification(event.partner().getSecurity()).kind() == Kind.UNCLASSIFIED) {
          return;
        }
        if (!tryMove(event, false)) {
          pending.add(event);
        }
      }
      }
    }

    /** Executes the moves whose source holds the units by now, until none makes progress. */
    private void retryPending(boolean force) {
      boolean progress = !pending.isEmpty();
      while (progress) {
        progress = false;
        for (Event event : new ArrayList<>(pending)) {
          if (tryMove(event, false)) {
            pending.remove(event);
            progress = true;
          }
        }
      }
      if (force) {
        for (Event event : new ArrayList<>(pending)) {
          tryMove(event, true);
          pending.remove(event);
        }
      }
    }

    private void flushPending() {
      retryPending(true);
    }

    // ------------------------------------------------------------------------------------------ lots

    private Book book(Integer idSecurityaccount, Security security) {
      return books.computeIfAbsent(new BookKey(idSecurityaccount, security.getIdSecuritycurrency()),
          _ -> new Book(security));
    }

    private double unitsOf(Transaction t) {
      return t.getUnits() * Securitysplit.calcSplitFatorForFromDate(t.getSecurity().getIdSecuritycurrency(),
          t.getTransactionDate(), splitMap);
    }

    private void buy(Transaction t, Classification cl) {
      final GermanTaxLot lot = new GermanTaxLot(t.getIdTransaction(), t.getTransactionTime(), unitsOf(t));
      final double net = t.getSeucritiesNetPrice();
      final RateResult rate = eurRate(t);
      if (net <= EPS) {
        lot.markIncomplete(warn(GermanTaxKeys.WARN_PURCHASE_WITHOUT_CONSIDERATION, t, null));
      } else if (rate.rate() == null) {
        lot.markIncomplete(warn(GermanTaxKeys.WARN_MISSING_RATE, t, t.getSecurity().getCurrency()));
      } else {
        lot.costEur = (net + nz(t.getTransactionCost()) + nz(t.getTaxCost())) * rate.rate();
      }
      book(t.getIdSecurityaccount(), t.getSecurity()).add(lot);
      accruedInterest(t, cl, false);
    }

    /** Moves the lots of a transfer or an ISIN change. False when the source lacks units and may still get them. */
    private boolean tryMove(Event event, boolean force) {
      final Transaction sell = event.tx();
      final Transaction buy = event.partner();
      final double units = unitsOf(sell);
      final Book source = book(sell.getIdSecurityaccount(), sell.getSecurity());
      if (!force && source.units() < units - EPS) {
        return false;
      }
      final Consumption consumption = source.consume(units);
      final boolean isinChange = event.type() == EventType.SWAP;
      final Book target = book(buy.getIdSecurityaccount(), buy.getSecurity());
      final double factor = isinChange ? unitsOf(buy) / units : 1.0;
      final boolean fund = classification(sell.getSecurity()).isFund() || classification(buy.getSecurity()).isFund();
      GermanTaxWarning fundWarning = null;
      if (isinChange && fund) {
        fundWarning = warn(GermanTaxKeys.WARN_ISIN_CHANGE_FUND, sell, null);
        registerInTaxYear(fundWarning);
      }
      for (GermanTaxLot part : consumption.parts()) {
        part.units *= factor;
        if (fundWarning != null) {
          part.markIncomplete(fundWarning);
        }
        target.add(part);
      }
      if (consumption.uncovered() > 0.0) {
        final GermanTaxLot unknown = new GermanTaxLot(sell.getIdTransaction(), sell.getTransactionTime(),
            consumption.uncovered() * factor);
        final GermanTaxWarning uncovered = warn(GermanTaxKeys.WARN_UNCOVERED_TRANSFER, sell, null);
        registerInTaxYear(uncovered);
        unknown.markIncomplete(uncovered);
        target.add(unknown);
      }
      return true;
    }

    /** A warning of an event of the tax year is part of the report even before a sum depends on it. */
    private void registerInTaxYear(GermanTaxWarning warning) {
      if (warning.year != null && warning.year == taxYear) {
        warnings.add(warning);
      }
    }

    // ------------------------------------------------------------------------------------------ sale

    private void sell(Transaction t, Classification cl) {
      final double units = unitsOf(t);
      if (cl.kind() == Kind.UNCLASSIFIED) {
        if (t.getTransactionTime().getYear() == taxYear) {
          final RateResult rate = eurRate(t);
          unclassified.add(new GermanTaxUnclassified("SALE_PROCEEDS", cl.reason(), t.getIdTransaction(),
              t.getSecurity().getIdSecuritycurrency(), t.getSecurity().getName(), t.getTransactionDate(),
              rate.rate() == null ? null : money((t.getSeucritiesNetPrice() - costsOf(t)) * rate.rate())));
        }
        return;
      }
      final Consumption consumption = book(t.getIdSecurityaccount(), t.getSecurity()).consume(units);
      if (t.getTransactionTime().getYear() != taxYear) {
        return;
      }
      accruedInterest(t, cl, true);
      final SaleGroup regular = new SaleGroup();
      final SaleGroup legacy = new SaleGroup();
      for (GermanTaxLot part : consumption.parts()) {
        (isLegacy(part, cl) ? legacy : regular).parts.add(part);
      }
      regular.uncoveredUnits = consumption.uncovered();
      final RateResult rate = eurRate(t);
      final Double proceeds = rate.rate() == null ? null : (t.getSeucritiesNetPrice() - costsOf(t)) * rate.rate();
      final List<GermanTaxWarning> saleCauses = new ArrayList<>();
      if (rate.rate() == null) {
        saleCauses.add(warn(GermanTaxKeys.WARN_MISSING_RATE, t, t.getSecurity().getCurrency()));
      }
      if (consumption.uncovered() > 0.0) {
        saleCauses.add(warn(GermanTaxKeys.WARN_UNCOVERED_SALE, t, null));
      }
      for (SaleGroup group : List.of(regular, legacy)) {
        if (!group.isEmpty()) {
          saleGroup(t, cl, group, group == legacy, proceeds, units, rate, saleCauses);
        }
      }
    }

    private void saleGroup(Transaction t, Classification cl, SaleGroup group, boolean isLegacy, Double proceeds,
        double saleUnits, RateResult rate, List<GermanTaxWarning> saleCauses) {
      final List<GermanTaxWarning> causes = new ArrayList<>(saleCauses);
      if (isLegacy) {
        causes.removeIf(c -> GermanTaxKeys.WARN_UNCOVERED_SALE.equals(c.code));
      }
      boolean fromIncompleteLot = false;
      for (GermanTaxLot lot : group.parts) {
        if (lot.incomplete) {
          fromIncompleteLot = true;
          lot.reasons.stream().filter(r -> !causes.contains(r)).forEach(causes::add);
        }
      }
      if (fromIncompleteLot) {
        causes.add(warn(GermanTaxKeys.WARN_SALE_FROM_INCOMPLETE_LOT, t, null));
      }
      final double cost = group.parts.stream().mapToDouble(lot -> lot.costEur).sum();
      final double prepayment = cl.isFund() ? group.parts.stream().mapToDouble(lot -> lot.preliminaryEur).sum() : 0.0;
      final Double share = proceeds == null ? null : proceeds * group.units() / saleUnits;
      final boolean complete = causes.isEmpty() && share != null;
      final Double gain = complete ? share - cost - prepayment : null;
      final Target target = isLegacy ? Target.FORM
          : target(portfolios.portfolioOfSecurityaccount(t.getIdSecurityaccount()));

      final GermanTaxDetail detail = detail(GermanTaxKeys.KIND_SALE, t, cl, rate);
      detail.units = group.units();
      detail.proceedsEur = share == null ? null : money(share);
      detail.costEur = complete ? money(cost) : null;
      detail.preliminaryDeductedEur = cl.isFund() && complete ? money(prepayment) : null;
      detail.gainEur = gain == null ? null : money(gain);
      detail.complete = complete;
      group.parts.forEach(lot -> detail.lots.add(new LotPart(lot.idTransaction, lot.acquisitionTime, lot.units,
          money(lot.costEur), money(lot.preliminaryEur))));
      if (isLegacy) {
        detail.block = GermanTaxKeys.BLOCK_LEGACY;
        detail.category = categoryOf(cl);
        postLegacy(cl, gain, causes);
      } else {
        postSale(target, cl, gain, causes, detail);
      }
      details.add(detail);
    }

    private void postSale(Target target, Classification cl, Double gain, List<GermanTaxWarning> causes,
        GermanTaxDetail detail) {
      detail.block = blockOf(target, categoryOf(cl));
      detail.category = target == Target.FORM ? null : categoryOf(cl);
      switch (cl.kind()) {
      case SHARE -> postGainLoss(target, GermanTaxKeys.CATEGORY_SHARES, GermanTaxKeys.LINE_SHARE_GAINS,
          GermanTaxKeys.LINE_SHARE_LOSSES, gain, causes);
      case CLAIM -> postGainLoss(target, GermanTaxKeys.CATEGORY_OTHER_INCOME, GermanTaxKeys.LINE_CLAIM_GAINS,
          GermanTaxKeys.LINE_CLAIM_LOSSES, gain, causes);
      default -> {
        if (gain == null) {
          postIncomplete(target, GermanTaxKeys.CATEGORY_FUNDS, GermanTaxKeys.LINE_FUND_DISPOSAL, cl.fundTaxClass(),
              causes);
        } else {
          post(target, GermanTaxKeys.CATEGORY_FUNDS, GermanTaxKeys.LINE_FUND_DISPOSAL, cl.fundTaxClass(), gain);
        }
      }
      }
    }

    private void postGainLoss(Target target, String category, String gainLine, String lossLine, Double gain,
        List<GermanTaxWarning> causes) {
      if (gain == null) {
        postIncomplete(target, category, gainLine, null, causes);
        postIncomplete(target, category, lossLine, null, causes);
      } else if (gain >= 0) {
        post(target, category, gainLine, null, gain);
      } else {
        post(target, category, lossLine, null, -gain);
      }
    }

    private void postLegacy(Classification cl, Double gain, List<GermanTaxWarning> causes) {
      final GermanTaxSum sum = sum(GermanTaxKeys.BLOCK_LEGACY, categoryOf(cl), GermanTaxKeys.LINE_LEGACY_DISPOSAL,
          cl.fundTaxClass());
      if (gain == null) {
        incomplete(sum, causes);
      } else {
        add(sum, gain, cl.fundTaxClass());
      }
    }

    private boolean isLegacy(GermanTaxLot lot, Classification cl) {
      final LocalDate cutoff = cl.isFund() ? LEGACY_CUTOFF_FUNDS : LEGACY_CUTOFF_SHARES;
      return lot.acquisitionTime.toLocalDate().isBefore(cutoff);
    }

    // ------------------------------------------------------------------------------------------ income

    private void dividend(Transaction t, Classification cl) {
      if (t.getTransactionTime().getYear() != taxYear) {
        return;
      }
      final RateResult rate = eurRate(t);
      if (cl.kind() == Kind.UNCLASSIFIED) {
        unclassified.add(new GermanTaxUnclassified("INCOME", cl.reason(), t.getIdTransaction(),
            t.getSecurity().getIdSecuritycurrency(), t.getSecurity().getName(), t.getTransactionDate(),
            rate.rate() == null ? null : money(t.getSeucritiesNetPrice() * rate.rate())));
        return;
      }
      final String category;
      final String line;
      final String kind;
      switch (cl.kind()) {
      case SHARE -> {
        category = GermanTaxKeys.CATEGORY_SHARES;
        line = GermanTaxKeys.LINE_SHARE_DIVIDENDS;
        kind = GermanTaxKeys.KIND_DIVIDEND;
      }
      case CLAIM -> {
        category = GermanTaxKeys.CATEGORY_OTHER_INCOME;
        line = GermanTaxKeys.LINE_INTEREST;
        kind = GermanTaxKeys.KIND_INTEREST;
      }
      default -> {
        category = GermanTaxKeys.CATEGORY_FUNDS;
        line = GermanTaxKeys.LINE_FUND_DISTRIBUTIONS;
        kind = GermanTaxKeys.KIND_DIVIDEND;
      }
      }
      final Integer idPortfolio = portfolios.portfolioOfSecurityaccount(t.getIdSecurityaccount());
      income(kind, t, cl, rate, t.getSeucritiesNetPrice(), nz(t.getTaxCost()), category, line, cl.fundTaxClass(),
          idPortfolio, null);
    }

    private void cashInterest(Transaction t) {
      if (t.getTransactionTime().getYear() != taxYear || t.getCashaccountAmount() == null) {
        return;
      }
      final String currency = t.getCashaccount().getCurrency();
      final RateResult rate = new RateResult(market.rateToEur(currency, t.getTransactionDate()),
          "EUR".equals(currency) ? "EUR" : "CLOSE");
      final Integer idPortfolio = portfolios.portfolioOfCashaccount(t.getCashaccount().getIdSecuritycashAccount());
      final double tax = nz(t.getTaxCost());
      income(GermanTaxKeys.KIND_INTEREST, t, null, rate, t.getCashaccountAmount() + tax, tax,
          GermanTaxKeys.CATEGORY_OTHER_INCOME, GermanTaxKeys.LINE_INTEREST, null, idPortfolio, currency);
    }

    /** Posts an income and the foreign tax recorded with it, both converted with the rate of their own currency. */
    private void income(String kind, Transaction t, Classification cl, RateResult rate, double gross, double tax,
        String category, String line, FundTaxClass fundTaxClass, Integer idPortfolio, String currency) {
      final Target target = target(idPortfolio);
      final GermanTaxDetail detail = detail(kind, t, cl, rate);
      if (currency != null) {
        detail.currency = currency;
      }
      detail.block = blockOf(target, category);
      detail.category = target == Target.FORM ? null : category;
      detail.idPortfolio = idPortfolio;
      final List<GermanTaxWarning> causes = new ArrayList<>();
      if (rate.rate() == null) {
        causes.add(warn(GermanTaxKeys.WARN_MISSING_RATE, t, currency != null ? currency
            : t.getSecurity().getCurrency()));
        postIncomplete(target, category, line, fundTaxClass, causes);
        if (tax > 0) {
          postTaxIncomplete(target, causes);
        }
        detail.complete = false;
      } else {
        final double grossEur = gross * rate.rate();
        post(target, category, line, fundTaxClass, grossEur);
        detail.grossEur = money(grossEur);
        if (tax > 0) {
          final double taxEur = tax * rate.rate();
          final double exemption = fundTaxClass == null ? 0.0 : fundTaxClass.getPartialExemption();
          final double ceiling = Math.min(taxEur, Math.max(grossEur * (1 - exemption), 0.0) * FOREIGN_TAX_CEILING);
          post(target, GermanTaxKeys.CATEGORY_FOREIGN_TAX, GermanTaxKeys.LINE_TAX_RECORDED, null, taxEur);
          post(target, GermanTaxKeys.CATEGORY_FOREIGN_TAX, GermanTaxKeys.LINE_TAX_CEILING, null, ceiling);
          post(target, GermanTaxKeys.CATEGORY_FOREIGN_TAX, GermanTaxKeys.LINE_TAX_NOT_CREDITED, null,
              taxEur - ceiling);
          detail.foreignTaxEur = money(taxEur);
          detail.taxCeilingEur = money(ceiling);
        }
      }
      details.add(detail);
    }

    private void postTaxIncomplete(Target target, List<GermanTaxWarning> causes) {
      for (String line : List.of(GermanTaxKeys.LINE_TAX_RECORDED, GermanTaxKeys.LINE_TAX_CEILING,
          GermanTaxKeys.LINE_TAX_NOT_CREDITED)) {
        postIncomplete(target, GermanTaxKeys.CATEGORY_FOREIGN_TAX, line, null, causes);
      }
    }

    /** Accrued interest of a bond is income of its booking year: paid with a purchase, received with a sale. */
    private void accruedInterest(Transaction t, Classification cl, boolean received) {
      if (cl.kind() != Kind.CLAIM || t.getAssetInvestmentValue1() == null || t.getAssetInvestmentValue1() == 0.0
          || t.getTransactionTime().getYear() != taxYear) {
        return;
      }
      final RateResult rate = eurRate(t);
      final String line = received ? GermanTaxKeys.LINE_ACCRUED_INTEREST_RECEIVED
          : GermanTaxKeys.LINE_ACCRUED_INTEREST_PAID;
      final Target target = target(portfolios.portfolioOfSecurityaccount(t.getIdSecurityaccount()));
      final GermanTaxDetail detail = detail(GermanTaxKeys.KIND_ACCRUED_INTEREST, t, cl, rate);
      detail.block = blockOf(target, GermanTaxKeys.CATEGORY_OTHER_INCOME);
      detail.category = target == Target.FORM ? null : GermanTaxKeys.CATEGORY_OTHER_INCOME;
      if (rate.rate() == null) {
        final List<GermanTaxWarning> causes = List.of(
            warn(GermanTaxKeys.WARN_MISSING_RATE, t, t.getSecurity().getCurrency()));
        postIncomplete(target, GermanTaxKeys.CATEGORY_OTHER_INCOME, line, null, causes);
        detail.complete = false;
      } else {
        final double amount = t.getAssetInvestmentValue1() * rate.rate() * (received ? 1.0 : -1.0);
        post(target, GermanTaxKeys.CATEGORY_OTHER_INCOME, line, null, amount);
        detail.grossEur = money(amount);
      }
      details.add(detail);
    }

    private void marginClose(Transaction t) {
      final String currency = t.getCashaccount().getCurrency();
      final Double rate = market.rateToEur(currency, t.getTransactionDate());
      final Double amount = t.getCashaccountAmount() == null || rate == null ? null
          : money(t.getCashaccountAmount() * rate);
      unclassified.add(new GermanTaxUnclassified("MARGIN_CLOSE", GermanTaxKeys.REASON_MARGIN_INSTRUMENT,
          t.getIdTransaction(), t.getSecurity().getIdSecuritycurrency(), t.getSecurity().getName(),
          t.getTransactionDate(), amount));
    }

    // ------------------------------------------------------------------------------------------ prepayment

    /** Applies the advance lump sum of a year to the lots held at its end, it counts as income of the next year. */
    private void advanceYearEnds(int upToYear) {
      while (nextYearEnd < upToYear && nextYearEnd < today.getYear()) {
        yearEnd(nextYearEnd);
        nextYearEnd++;
      }
    }

    private void yearEnd(int year) {
      for (Book book : books.values()) {
        final Classification cl = classification(book.security);
        if (!cl.isFund() || book.units() <= EPS) {
          continue;
        }
        final Prepayment prepayment = prepayment(book.security, year);
        double sum = 0.0;
        for (GermanTaxLot lot : book.lots) {
          if (prepayment.problem() != null) {
            lot.markIncomplete(prepayment.problem());
          } else {
            final double months = lot.acquisitionTime.getYear() == year
                ? (13 - lot.acquisitionTime.getMonthValue()) / 12.0
                : 1.0;
            final double amount = lot.units * prepayment.perUnit() * months;
            lot.preliminaryEur += amount;
            sum += amount;
          }
        }
        if (year + 1 == taxYear) {
          prepaymentIncome(book, cl, year, prepayment, sum);
        }
      }
    }

    private void prepaymentIncome(Book book, Classification cl, int year, Prepayment prepayment, double sum) {
      final Integer idSecurityaccount = books.entrySet().stream().filter(e -> e.getValue() == book)
          .map(e -> e.getKey().idSecurityaccount()).findFirst().orElse(null);
      final Integer idPortfolio = portfolios.portfolioOfSecurityaccount(idSecurityaccount);
      final Target target = target(idPortfolio);
      final GermanTaxDetail detail = new GermanTaxDetail();
      detail.kind = GermanTaxKeys.KIND_PRELIMINARY;
      detail.date = firstWorkingDay(year + 1);
      detail.idSecuritycurrency = book.security.getIdSecuritycurrency();
      detail.securityName = book.security.getName();
      detail.isin = book.security.getIsin();
      detail.idSecurityaccount = idSecurityaccount;
      detail.idPortfolio = idPortfolio;
      detail.currency = book.security.getCurrency();
      detail.fundTaxClass = cl.fundTaxClass().name();
      detail.block = blockOf(target, GermanTaxKeys.CATEGORY_FUNDS);
      detail.category = target == Target.FORM ? null : GermanTaxKeys.CATEGORY_FUNDS;
      detail.units = book.units();
      if (prepayment.problem() != null) {
        postIncomplete(target, GermanTaxKeys.CATEGORY_FUNDS, GermanTaxKeys.LINE_FUND_PRELIMINARY, cl.fundTaxClass(),
            List.of(prepayment.problem()));
        detail.complete = false;
      } else {
        post(target, GermanTaxKeys.CATEGORY_FUNDS, GermanTaxKeys.LINE_FUND_PRELIMINARY, cl.fundTaxClass(), sum);
        detail.grossEur = money(sum);
      }
      details.add(detail);
    }

    /**
     * The advance lump sum of one unit for a year in euro: the base income, which is the price at the start of the year
     * times the base interest rate times 0.7, limited by the increase in value plus the distributions, less the
     * distributions. Prices and distributions are converted at the rate of their own day.
     */
    private Prepayment prepayment(Security fund, int year) {
      return prepayments.computeIfAbsent(fund.getIdSecuritycurrency() + "/" + year, _ -> calcPrepayment(fund, year));
    }

    private Prepayment calcPrepayment(Security fund, int year) {
      final Double baseRate = market.baseRate(year);
      if (baseRate == null) {
        return problem(GermanTaxKeys.WARN_MISSING_BASE_RATE, fund, year, String.valueOf(year));
      }
      final PricePoint first = market.firstPrice(fund.getIdSecuritycurrency(), year);
      final PricePoint last = market.lastPrice(fund.getIdSecuritycurrency(), year);
      if (first == null || last == null) {
        return problem(GermanTaxKeys.WARN_MISSING_PRICE, fund, year, String.valueOf(year));
      }
      final Double rateFirst = market.rateToEur(fund.getCurrency(), first.date());
      final Double rateLast = market.rateToEur(fund.getCurrency(), last.date());
      if (rateFirst == null || rateLast == null) {
        return problem(GermanTaxKeys.WARN_MISSING_RATE, fund, year, fund.getCurrency());
      }
      final List<Distribution> distributions = market.distributions(fund, year);
      if (distributions == null) {
        return problem(GermanTaxKeys.WARN_MISSING_DISTRIBUTIONS, fund, year, String.valueOf(year));
      }
      double distributionEur = 0.0;
      for (Distribution distribution : distributions) {
        final Double rate = market.rateToEur(distribution.currency(), distribution.date());
        if (rate == null) {
          return problem(GermanTaxKeys.WARN_MISSING_RATE, fund, year, distribution.currency());
        }
        distributionEur += distribution.amountPerUnit() * rate;
      }
      final double startEur = first.close() * rateFirst;
      final double endEur = last.close() * rateLast;
      final double baseIncome = startEur * (baseRate / 100.0) * BASE_INCOME_FACTOR;
      final double limited = Math.min(baseIncome, endEur - startEur + distributionEur);
      return new Prepayment(Math.max(0.0, limited - distributionEur), null);
    }

    private Prepayment problem(String code, Security fund, int year, String detail) {
      return new Prepayment(0.0, new GermanTaxWarning(code, year, null, fund.getIdSecuritycurrency(), fund.getName(),
          detail));
    }

    // ------------------------------------------------------------------------------------------ helpers

    private Classification classification(Security security) {
      return classifications.computeIfAbsent(security.getIdSecuritycurrency(),
          _ -> GermanTaxClassifier.classify(security));
    }

    private Target target(Integer idPortfolio) {
      final TaxWithholding withholding = idPortfolio == null ? null : portfolios.withholding(idPortfolio);
      if (withholding == null) {
        return Target.UNREVIEWED;
      }
      return withholding == TaxWithholding.DOMESTIC_WITHHOLDING ? Target.WITHHELD : Target.FORM;
    }

    private String blockOf(Target target, String category) {
      return switch (target) {
      case FORM -> category;
      case WITHHELD -> GermanTaxKeys.BLOCK_WITHHELD;
      case UNREVIEWED -> GermanTaxKeys.BLOCK_UNREVIEWED;
      };
    }

    private String categoryOf(Classification cl) {
      return switch (cl.kind()) {
      case SHARE -> GermanTaxKeys.CATEGORY_SHARES;
      case CLAIM -> GermanTaxKeys.CATEGORY_OTHER_INCOME;
      default -> GermanTaxKeys.CATEGORY_FUNDS;
      };
    }

    private double costsOf(Transaction t) {
      return nz(t.getTransactionCost()) + nz(t.getTaxCost());
    }

    /**
     * The rate from the currency of the security to euro on the day of the transaction: 1 for a security in euro, the
     * rate booked with the transaction when the cash account is in euro and a currency pair was booked, otherwise the
     * closing rate of the day. A missing rate stays missing.
     */
    private RateResult eurRate(Transaction t) {
      final String currency = t.getSecurity().getCurrency();
      if ("EUR".equals(currency)) {
        return new RateResult(1.0, "EUR");
      }
      if (t.getCashaccount() != null && "EUR".equals(t.getCashaccount().getCurrency()) && t.getIdCurrencypair() != null
          && t.getCurrencyExRate() != null) {
        return new RateResult(t.getCurrencyExRate(), "BOOKED");
      }
      return new RateResult(market.rateToEur(currency, t.getTransactionDate()), "CLOSE");
    }

    private GermanTaxDetail detail(String kind, Transaction t, Classification cl, RateResult rate) {
      final GermanTaxDetail detail = new GermanTaxDetail();
      detail.kind = kind;
      detail.date = t.getTransactionDate();
      detail.idTransaction = t.getIdTransaction();
      detail.idSecurityaccount = t.getIdSecurityaccount();
      if (t.getSecurity() != null) {
        detail.idSecuritycurrency = t.getSecurity().getIdSecuritycurrency();
        detail.securityName = t.getSecurity().getName();
        detail.isin = t.getSecurity().getIsin();
        detail.currency = t.getSecurity().getCurrency();
        detail.idPortfolio = portfolios.portfolioOfSecurityaccount(t.getIdSecurityaccount());
      }
      detail.fundTaxClass = cl != null && cl.fundTaxClass() != null ? cl.fundTaxClass().name() : null;
      detail.rate = rate.rate();
      detail.rateSource = rate.source();
      return detail;
    }

    private GermanTaxWarning warn(String code, Transaction t, String detail) {
      return new GermanTaxWarning(code, t.getTransactionTime().getYear(), t.getIdTransaction(),
          t.getSecurity() == null ? null : t.getSecurity().getIdSecuritycurrency(),
          t.getSecurity() == null ? null : t.getSecurity().getName(), detail);
    }

    // ------------------------------------------------------------------------------------------ sums

    private GermanTaxSum sum(String block, String category, String line, FundTaxClass fundTaxClass) {
      final GermanTaxSum probe = new GermanTaxSum(block, category, line,
          fundTaxClass == null ? null : fundTaxClass.name());
      return sums.computeIfAbsent(probe.key(), _ -> {
        probe.amountEur = 0.0;
        probe.amountAfterPartialExemptionEur = fundTaxClass == null ? null : 0.0;
        return probe;
      });
    }

    private void post(Target target, String category, String line, FundTaxClass fundTaxClass, double amount) {
      add(sum(blockOf(target, category), target == Target.FORM ? null : category, line, fundTaxClass), amount,
          fundTaxClass);
    }

    private void add(GermanTaxSum sum, double amount, FundTaxClass fundTaxClass) {
      if (sum.complete) {
        sum.amountEur += amount;
        if (fundTaxClass != null && sum.amountAfterPartialExemptionEur != null) {
          sum.amountAfterPartialExemptionEur += amount * (1 - fundTaxClass.getPartialExemption());
        }
      }
    }

    private void postIncomplete(Target target, String category, String line, FundTaxClass fundTaxClass,
        List<GermanTaxWarning> causes) {
      incomplete(sum(blockOf(target, category), target == Target.FORM ? null : category, line, fundTaxClass), causes);
    }

    private void incomplete(GermanTaxSum sum, List<GermanTaxWarning> causes) {
      sum.complete = false;
      sum.amountEur = null;
      sum.amountAfterPartialExemptionEur = null;
      for (GermanTaxWarning cause : causes) {
        if (!cause.affectedSums.contains(sum.key())) {
          cause.affectedSums.add(sum.key());
        }
        if (!warnings.contains(cause)) {
          warnings.add(cause);
        }
      }
    }

    private GermanTaxYearReport buildReport() {
      final GermanTaxYearReport report = new GermanTaxYearReport();
      report.taxYear = taxYear;
      report.provisional = taxYear == today.getYear();
      for (GermanTaxSum sum : sums.values()) {
        if (sum.complete) {
          sum.amountEur = money(sum.amountEur);
          if (sum.amountAfterPartialExemptionEur != null) {
            sum.amountAfterPartialExemptionEur = money(sum.amountAfterPartialExemptionEur);
          }
        }
        report.sums.add(sum);
      }
      report.warnings.addAll(warnings);
      report.unclassified.addAll(unclassified);
      report.details.addAll(details);
      report.portfolios.addAll(portfolios.assignments());
      report.portfolioAssignmentRequired = report.portfolios.stream()
          .anyMatch(p -> p.hasAccounts && p.taxWithholding == null);
      return report;
    }
  }

  private static double nz(Double value) {
    return value == null ? 0.0 : value;
  }

  private static double money(double value) {
    return DataHelper.round(value, 2);
  }
}
