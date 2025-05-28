package grafioschtrader.instrument;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import grafiosch.exceptions.DataViolationException;
import grafiosch.types.OperationType;
import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.ProposedMarginFinanceCost;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.types.TransactionType;

public class SecurityMarginUnitsCheck {

  /**
   * The system checks whether the margin transaction is valid based on the number
   * of units. The system also checks whether a possible deletion command for an
   * opening margin position could be executed. Possible splits are also included
   * in the calculation.
   * 
   * @param securitysplitJpaRepository Repository of splits
   * @param operationyType             What kind of operation
   * @param transactions               The other transactions.
   * @param targetTransaction          The transaction under review.
   * @param security                   The margin instrument.
   */
  public static void checkUnitsIntegrity(final SecuritysplitJpaRepository securitysplitJpaRepository,
      final OperationType operationyType, final List<Transaction> transactions, final Transaction targetTransaction,
      final Security security) {

    final DataViolationException dataViolationException = new DataViolationException();
    if (operationyType == OperationType.DELETE) {
      if (targetTransaction.isMarginOpenPosition() && !transactions.isEmpty()) {
        dataViolationException.addDataViolation(GlobalConstants.UNITS, "margin.open.not.removable",
            targetTransaction.getTransactionTime());
      }
    } else {
      addUpdateTransaction(securitysplitJpaRepository, transactions, targetTransaction, dataViolationException);
    }
    if (dataViolationException.hasErrors()) {
      throw dataViolationException;
    }

  }

  /**
   * The system checks whether the margin transaction is valid based on the number
   * of units. Possible splits are also included in the calculation.
   * 
   * @param securitysplitJpaRepository Repository of splits
   * @param transactions               The other transactions.
   * @param targetTransaction          The transaction under review.
   * @param dataViolationException     Contains the possible error if the
   *                                   validation of the number of units in this
   *                                   transaction exceeds the limit.
   */
  private static void addUpdateTransaction(final SecuritysplitJpaRepository securitysplitJpaRepository,
      final List<Transaction> transactions, final Transaction targetTransaction,
      final DataViolationException dataViolationException) {

    final Map<Integer, List<Securitysplit>> securitySplitMap = securitysplitJpaRepository
        .getSecuritysplitMapByIdSecuritycurrency(targetTransaction.getSecurity().getIdSecuritycurrency());

    Double unitsRequired = Math
        .abs(transactions.stream()
            .filter(t -> t.getTransactionType() == TransactionType.ACCUMULATE
                || t.getTransactionType() == TransactionType.REDUCE)
            .map(t -> t.getUnits()
                * Securitysplit.calcSplitFatorForFromDate(t.getSecurity().getIdSecuritycurrency(),
                    t.getTransactionTime(), securitySplitMap)
                * (t.getTransactionType() == TransactionType.ACCUMULATE ? 1 : -1))
            .reduce(0.0, (a, b) -> a + b));

    if (targetTransaction.isMarginOpenPosition()) {
      double unitsInOpenTransaction = targetTransaction.getUnits()
          * Securitysplit.calcSplitFatorForFromDate(targetTransaction.getSecurity().getIdSecuritycurrency(),
              targetTransaction.getTransactionTime(), securitySplitMap);

      if (unitsInOpenTransaction < unitsRequired) {
        dataViolationException.addDataViolation(GlobalConstants.UNITS, "units.open.not.enough",
            targetTransaction.getTransactionTime());
      }
    } else {
      if (targetTransaction.getTransactionType() != TransactionType.FINANCE_COST) {
        // close position
        if (targetTransaction.getUnits() > unitsRequired) {
          dataViolationException.addDataViolation(GlobalConstants.UNITS, "units.open.too.many",
              targetTransaction.getTransactionTime());
        }
        Transaction openTransaction = transactions.stream()
            .filter(t -> t.getIdTransaction().equals(targetTransaction.getConnectedIdTransaction())).findFirst().get();
        targetTransaction.setSplitFactorFromBaseTransaction(Securitysplit.calcSplitFatorForFromDateAndToDate(
            targetTransaction.getSecurity().getIdSecuritycurrency(), openTransaction.getTransactionTime(),
            targetTransaction.getTransactionTime(), securitySplitMap).fromToDateFactor);

      }
    }
  }

  /**
   * Based on the opening margin position and the daily financing costs specified
   * at that time, a proposal for the costs is suggested to the user. Financing
   * costs already paid and partial sales are taken into account.
   * 
   * @param transactionJpaRepository Repository for transactions
   * @param idTenant                 The tenant's ID for security reasons.
   * @param idTransaction            The ID of the opening margining position
   *                                 transaction.
   * @return The proposed financing costs resulting from this calculation.
   */
  public static ProposedMarginFinanceCost getEstimatedFinanceCost(TransactionJpaRepository transactionJpaRepository,
      final Integer idTenant, Integer idTransaction) {

    TreeMap<LocalDate, Double> dayUnitsMap = new TreeMap<>();
    List<Transaction> transactions = transactionJpaRepository
        .getMarginForIdTenantAndIdTransactionOrderByTransactionTime(idTenant, idTransaction);
    if (!transactions.isEmpty()) {
      Transaction openTransaction = transactions.getFirst();
      Double dailyFinanceCost = openTransaction.getAssetInvestmentValue1();
      if (dailyFinanceCost != null) {
        double openUnits = openTransaction.getUnits();
        LocalDate lastPaidDay = openTransaction.getTransactionDate();
        dayUnitsMap.put(lastPaidDay, openUnits);

        for (int i = 1; i < transactions.size(); i++) {
          Transaction transaction = transactions.get(i);
          if (transaction.getTransactionType() == TransactionType.FINANCE_COST) {
            lastPaidDay = lastPaidDay.plusDays(transaction.getUnits().longValue());
            dayUnitsMap.put(lastPaidDay, removeBeforeFromTreeMap(dayUnitsMap, lastPaidDay));
          } else if (transaction.getConnectedIdTransaction() != null) {
            // Close position
            openUnits -= transaction.getUnits();
            dayUnitsMap.put(transaction.getTransactionDate(), openUnits);
          }
        }
        return calcFinanceCost(openTransaction.getUnits(), dailyFinanceCost, dayUnitsMap);
      }
    }
    return new ProposedMarginFinanceCost();
  }

  private static ProposedMarginFinanceCost calcFinanceCost(double openUnits, Double dailyFinanceCost,
      TreeMap<LocalDate, Double> dayUnitsMap) {
    double rollingUnit = 0;
    double unitDailyCost = dailyFinanceCost / openUnits;
    ProposedMarginFinanceCost pMFC = new ProposedMarginFinanceCost();

    for (Map.Entry<LocalDate, Double> dayUnit : dayUnitsMap.entrySet()) {
      if (pMFC.untilDate != null) {
        long days = ChronoUnit.DAYS.between(pMFC.untilDate, dayUnit.getKey());
        pMFC.financeCost += days * rollingUnit * unitDailyCost;
        pMFC.daysToPay += days;
      }
      pMFC.untilDate = dayUnit.getKey();
      rollingUnit = dayUnit.getValue();
    }
    if (pMFC.daysToPay == 0 && LocalDate.now().isAfter(dayUnitsMap.lastEntry().getKey())) {
      pMFC.untilDate = LocalDate.now();
      pMFC.daysToPay = (int) ChronoUnit.DAYS.between(dayUnitsMap.lastEntry().getKey(), pMFC.untilDate);
      pMFC.financeCost += pMFC.daysToPay * rollingUnit * unitDailyCost;

    }
    return pMFC;
  }

  private static double removeBeforeFromTreeMap(TreeMap<LocalDate, Double> dayUnitsMap, LocalDate removeBeforDate) {
    Iterator<LocalDate> iter = dayUnitsMap.keySet().iterator();
    double units = 0;
    while (iter.hasNext()) {
      LocalDate date = iter.next();
      if (date.isBefore(removeBeforDate)) {
        units = dayUnitsMap.get(date);
        iter.remove();
      } else {
        return units;
      }
    }
    return 0;
  }

}
