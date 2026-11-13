package grafioschtrader.reports.dataverification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.instrument.CostBasisMethods;
import grafioschtrader.instrument.SecurityCalcService;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.transaction.SecurityTransactionPosition;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.CostBasisMethod;
import grafioschtrader.types.TransactionType;

/**
 * Read-only evidence run of the cost basis methods on the live database. It calculates every position of every
 * security account by average cost, FIFO and LIFO and asserts what must hold whatever the data is:
 *
 * <ol>
 * <li><b>Average cost against an independent replay.</b> The gain of every sale by average cost must equal a replay
 * that keeps one pool of units and cost straight from the transaction rows and uses none of the position
 * accumulators. This is the guard that existing figures did not move.</li>
 * <li><b>The method only moves gain.</b> Realized gain less the remaining cost equals proceeds less purchase costs,
 * a quantity no method can change. It must therefore be the same for all three methods.</li>
 * <li><b>A closed position.</b> Its realized gain is the same for every method.</li>
 * <li><b>Lots.</b> No sale is uncovered, the consumed lots of every FIFO and LIFO sale add up to its cost, and no
 * value is non-finite.</li>
 * </ol>
 *
 * <p>
 * Every position goes into a CSV file in the directory given by the system property {@code costbasis.out}, by default
 * the temporary directory, so that the size of the shift between the methods can be read off.
 * </p>
 *
 * <p>
 * <b>{@code @ActiveProfiles("prod")} is not optional.</b> Without an explicit profile the context boots the test
 * bootstrap and {@code V1__schema.sql} drops every table of the target database. Nothing here opens a write path.
 * </p>
 */
@SpringBootTest(classes = GTforTest.class)
@ActiveProfiles("prod")
@Transactional
class CostBasisMethodComparisonTest {

  /** Below this many units of the security currency a deviation is rounding. */
  private static final double TOLERANCE = 0.01;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private SecurityCalcService securityCalcService;

  private final List<String> findings = new ArrayList<>();
  private final List<String> csv = new ArrayList<>();
  private int positions;
  private int positionsWithSale;
  private int positionsShifted;

  @Test
  void compareCostBasisMethods() throws IOException {
    csv.add("idTenant;idSecurityaccount;idSecurity;name;currency;units;sales;realizedAverage;realizedFifo;"
        + "realizedLifo;remainingAverage;remainingFifo;remainingLifo");
    final LocalDate today = LocalDate.now();
    for (final Tenant tenant : tenantJpaRepository.findAll()) {
      positionsOf(tenant.getIdTenant()).forEach((_, transactions) -> comparePosition(tenant, transactions, today));
    }
    final Path out = Path.of(System.getProperty("costbasis.out", System.getProperty("java.io.tmpdir")),
        "cost-basis-method-comparison.csv");
    Files.write(out, csv);
    System.out.printf("%d positions, %d with a sale, %d with a realized gain that differs between the methods -> %s%n",
        positions, positionsWithSale, positionsShifted, out);
    assertThat(findings).isEmpty();
  }

  /** The transactions of every non-margin security, grouped by security account and security. */
  private Map<String, List<Transaction>> positionsOf(final Integer idTenant) {
    final Map<String, List<Transaction>> positionMap = new LinkedHashMap<>();
    transactionJpaRepository.findByIdTenantOrderByTransactionTimeDesc(idTenant).stream()
        .filter(transaction -> transaction.getSecurity() != null && !transaction.getSecurity().isMarginInstrument()
            && transaction.getIdSecurityaccount() != null)
        .forEach(transaction -> positionMap.computeIfAbsent(
            transaction.getIdSecurityaccount() + "/" + transaction.getSecurity().getIdSecuritycurrency(),
            _ -> new ArrayList<>()).add(transaction));
    return positionMap;
  }

  private void comparePosition(final Tenant tenant, final List<Transaction> transactions, final LocalDate today) {
    final Security security = transactions.getFirst().getSecurity();
    final String label = tenant.getIdTenant() + "/" + transactions.getFirst().getIdSecurityaccount() + "/"
        + security.getIdSecuritycurrency() + " " + security.getName();
    final Map<Integer, List<Securitysplit>> splitMap = securitysplitJpaRepository
        .getSecuritysplitMapByIdSecuritycurrency(security.getIdSecuritycurrency());
    final Map<CostBasisMethod, SecurityTransactionSummary> summaryMap = new EnumMap<>(CostBasisMethod.class);
    for (final CostBasisMethod method : CostBasisMethod.values()) {
      final SecurityTransactionSummary summary = new SecurityTransactionSummary(security, null,
          globalparametersService.getCurrencyPrecision());
      // No main currency: every figure compared here is in the currency of the security
      securityCalcService.calcTransactions(security, tenant.isExcludeDivTax(), summary, splitMap, transactions, today,
          new DateTransactionCurrencypairMap(today, true), CostBasisMethods.all(method));
      summaryMap.put(method, summary);
      checkLots(label, method, summary);
    }
    positions++;
    final SecurityTransactionSummary average = summaryMap.get(CostBasisMethod.AVERAGE_COST);
    final List<SecurityTransactionPosition> averageSales = sales(average);
    if (!averageSales.isEmpty()) {
      positionsWithSale++;
    }
    checkAverageAgainstReplay(label, transactions, averageSales, splitMap, today);

    final double invariant = realized(average) - average.securityPositionSummary.adjustedCostBase;
    // Every row carries its gain rounded to cents, so two sums of rows may differ by a cent per sale
    final double allowed = TOLERANCE * (averageSales.size() + 1);
    boolean shifted = false;
    for (final CostBasisMethod method : List.of(CostBasisMethod.FIFO, CostBasisMethod.LIFO)) {
      final SecurityTransactionSummary summary = summaryMap.get(method);
      final double deviation = realized(summary) - summary.securityPositionSummary.adjustedCostBase - invariant;
      if (Math.abs(deviation) > allowed) {
        findings.add(label + ": " + method + " changes realized gain less remaining cost by " + deviation);
      }
      final boolean differs = Math.abs(realized(summary) - realized(average)) > allowed;
      shifted |= differs;
      if (differs && average.securityPositionSummary.units == 0.0) {
        findings.add(label + ": closed position, " + method + " realizes " + realized(summary) + " instead of "
            + realized(average));
      }
    }
    if (shifted) {
      positionsShifted++;
    }
    csv.add(String.format(Locale.ROOT, "%d;%d;%d;%s;%s;%.8f;%d;%.2f;%.2f;%.2f;%.2f;%.2f;%.2f", tenant.getIdTenant(),
        transactions.getFirst().getIdSecurityaccount(), security.getIdSecuritycurrency(),
        security.getName().replace(';', ','), security.getCurrency(), average.securityPositionSummary.units,
        averageSales.size(), realized(average), realized(summaryMap.get(CostBasisMethod.FIFO)),
        realized(summaryMap.get(CostBasisMethod.LIFO)), average.securityPositionSummary.adjustedCostBase,
        summaryMap.get(CostBasisMethod.FIFO).securityPositionSummary.adjustedCostBase,
        summaryMap.get(CostBasisMethod.LIFO).securityPositionSummary.adjustedCostBase));
  }

  private void checkLots(final String label, final CostBasisMethod method, final SecurityTransactionSummary summary) {
    for (final SecurityTransactionPosition row : summary.transactionPositionList) {
      if (row.transactionGainLoss != null && !Double.isFinite(row.transactionGainLoss)
          || row.transactionGainLossPercentage != null && !Double.isFinite(row.transactionGainLossPercentage)) {
        findings.add(label + ": " + method + " non-finite value in transaction " + row.transaction.getIdTransaction());
      }
      if (row.costBasisIncomplete != null) {
        findings.add(label + ": " + method + " uncovered sale " + row.transaction.getIdTransaction());
      }
      if (method != CostBasisMethod.AVERAGE_COST && row.transaction.getTransactionType() == TransactionType.REDUCE) {
        final double proceeds = row.transaction.getSeucritiesNetPrice() - cost(row.transaction);
        final double lotCost = row.consumedLots.stream().mapToDouble(lot -> lot.cost).sum();
        // The row carries the gain rounded to cents
        if (Math.abs(proceeds - lotCost - row.transactionGainLoss) > TOLERANCE) {
          findings.add(label + ": " + method + " lots of sale " + row.transaction.getIdTransaction() + " cost "
              + lotCost + " but the gain implies " + (proceeds - row.transactionGainLoss));
        }
      }
    }
  }

  /**
   * Replays the average cost of every sale from the transaction rows alone: one pool of units and cost, a sale takes
   * its proportional share.
   */
  private void checkAverageAgainstReplay(final String label, final List<Transaction> transactions,
      final List<SecurityTransactionPosition> averageSales, final Map<Integer, List<Securitysplit>> splitMap,
      final LocalDate today) {
    double poolUnits = 0.0;
    double poolCost = 0.0;
    int saleIndex = 0;
    for (final Transaction transaction : SecurityCalcService.sortForCalculation(transactions)) {
      if (transaction.getTransactionTime().toLocalDate().isAfter(today)) {
        break;
      }
      final double units = transaction.getUnits() == null ? 0.0
          : transaction.getUnits() * Securitysplit.calcSplitFatorForFromDateAndToDate(
              transaction.getSecurity().getIdSecuritycurrency(), transaction.getTransactionTime().toLocalDate(), today,
              splitMap).fromToDateFactor;
      if (transaction.getTransactionType() == TransactionType.ACCUMULATE) {
        poolUnits += units;
        poolCost += transaction.getSeucritiesNetPrice() + cost(transaction);
      } else if (transaction.getTransactionType() == TransactionType.REDUCE) {
        final double saleCost = poolCost * units / poolUnits;
        final double expected = transaction.getSeucritiesNetPrice() - cost(transaction) - saleCost;
        final SecurityTransactionPosition row = averageSales.get(saleIndex++);
        if (row.transaction != transaction || Double.isFinite(expected)
            && Math.abs(expected - row.transactionGainLoss) > TOLERANCE) {
          findings.add(label + ": average cost of sale " + transaction.getIdTransaction() + " gives "
              + row.transactionGainLoss + ", the replay " + expected);
        }
        poolUnits -= units;
        poolCost -= saleCost;
      }
    }
  }

  private static List<SecurityTransactionPosition> sales(final SecurityTransactionSummary summary) {
    return summary.transactionPositionList.stream()
        .filter(row -> row.transaction.getTransactionType() == TransactionType.REDUCE).toList();
  }

  /** Sum of the unrounded gains would need the calculation internals, the rounded rows are exact to a cent each. */
  private static double realized(final SecurityTransactionSummary summary) {
    return sales(summary).stream().mapToDouble(row -> row.transactionGainLoss).sum();
  }

  private static double cost(final Transaction transaction) {
    return (transaction.getTaxCost() == null ? 0.0 : transaction.getTaxCost())
        + (transaction.getTransactionCost() == null ? 0.0 : transaction.getTransactionCost());
  }
}
