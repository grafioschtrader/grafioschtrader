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

/**
 * Validation and calculation utility for margin trading instrument units integrity and finance cost estimation.
 * 
 * <p>
 * This utility class provides sophisticated validation and calculation services specific to margin trading instruments
 * such as CFDs (Contracts for Difference) and Forex positions. It ensures the mathematical and business rule integrity
 * of margin positions while providing tools for calculating accumulated finance costs over time.
 * </p>
 * 
 * <h3>Core Responsibilities:</h3>
 * <dl>
 * <dt><strong>Units Integrity Validation</strong></dt>
 * <dd>Validates that margin transactions follow proper opening and closing rules, ensuring positions cannot be 
 * over-closed and that opening positions have sufficient units to cover all subsequent closing transactions.</dd>
 * 
 * <dt><strong>Corporate Action Handling</strong></dt>
 * <dd>Applies security split adjustments to maintain position integrity across corporate actions, ensuring that
 * split-adjusted units are used in all validation calculations.</dd>
 * 
 * <dt><strong>Finance Cost Calculation</strong></dt>
 * <dd>Calculates accumulated daily finance costs for leveraged positions, accounting for partial position closures
 * and varying position sizes over time.</dd>
 * 
 * </dl>
 * 
 * <h3>Margin Trading Business Rules:</h3>
 * <ul>
 * <li><strong>Opening Position Constraints:</strong> Opening positions must have sufficient units to cover all current and future closing transactions</li>
 * <li><strong>Closing Position Constraints:</strong> Closing transactions cannot exceed the available units from associated opening positions</li>
 * <li><strong>Deletion Restrictions:</strong> Opening positions with associated closing transactions cannot be deleted to maintain audit integrity</li>
 * <li><strong>Finance Cost Attribution:</strong> Daily holding costs are attributed to specific opening positions and adjusted for partial closures</li>
 * </ul>
 * 
 * <h3>Split Factor Integration:</h3>
 * <p>
 * All unit calculations automatically incorporate security splits to ensure that validation remains accurate
 * after corporate actions. Split factors are applied consistently across opening positions, closing transactions,
 * and finance cost calculations to maintain mathematical integrity.
 * </p>
 * 
 * <h3>Finance Cost Methodology:</h3>
 * <p>
 * Finance costs are calculated on a per-unit-per-day basis, with automatic adjustment for:
 * </p>
 */ 
public class SecurityMarginUnitsCheck {

  /**
   * Validates the units integrity of margin transactions according to business rules.
   * 
   * <p>
   * Performs comprehensive validation of margin transaction units, ensuring business rules
   * for leveraged trading are enforced. Validation logic adapts based on operation type.
   * </p>
   * 
   * <h4>Validation Rules:</h4>
   * <ul>
   * <li><strong>DELETE:</strong> Opening positions cannot be deleted if they have closing transactions</li>
   * <li><strong>ADD/UPDATE:</strong> Opening positions must have sufficient units, closing transactions cannot exceed available units</li>
   * </ul>
   * 
   * @param securitysplitJpaRepository repository for security split data and corporate action adjustments
   * @param operationyType the operation type (ADD, UPDATE, DELETE) determining validation rules
   * @param transactions existing transactions for unit balance calculations
   * @param targetTransaction the transaction being validated
   * @param security the margin instrument being traded
   * 
   * @throws DataViolationException if validation rules are violated
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
   * Performs detailed validation of ADD/UPDATE margin transactions with units integrity checks.
   * 
   * <p>
   * Calculates net position requirements by filtering relevant transactions, applying split factors,
   * and ensuring opening positions can cover all closing requirements.
   * </p>
   * 
   * <h4>Validation Process:</h4>
   * <ul>
   * <li>Calculates total closing requirements from ACCUMULATE and REDUCE transactions</li>
   * <li>Applies security split factors for corporate action adjustments</li>
   * <li>For opening positions: validates sufficient units to cover closures</li>
   * <li>For closing positions: validates not exceeding available units</li>
   * </ul>
   * 
   * @param securitysplitJpaRepository repository for security split data
   * @param transactions existing transactions for unit balance calculations
   * @param targetTransaction the transaction being validated
   * @param dataViolationException exception collector for validation errors
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
   * Calculates estimated finance costs for margin positions based on position history and daily rates.
   * 
   * <p>
   * Provides finance cost calculation accounting for position lifecycle including partial closures,
   * finance cost payments, and varying position sizes over time.
   * </p>
   * 
   * <h4>Calculation Features:</h4>
   * <ul>
   * <li><strong>Daily Rate Basis:</strong> Costs calculated per unit per day from opening position rate</li>
   * <li><strong>Position Tracking:</strong> Maintains running balance accounting for partial closures</li>
   * <li><strong>Payment History:</strong> Considers previous payments to avoid double-charging</li>
   * <li><strong>Current Calculation:</strong> Extends to present date for unpaid positions</li>
   * </ul>
   * 
   * @param transactionJpaRepository repository for transaction data with security filtering
   * @param idTenant tenant ID for security validation and data access control
   * @param idTransaction ID of the opening margin position transaction
   * @return calculated finance costs, days to pay, and end date, or empty if no rate specified
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

  /**
   * Performs detailed finance cost calculation based on position size changes over time.
   * 
   * <p>
   * Implements core calculation algorithm processing chronological position changes to determine
   * accumulated costs using precise date arithmetic and proportional cost allocation.
   * </p>
   * 
   * <h4>Algorithm:</h4>
   * <ol>
   * <li>Calculate per-unit daily cost rate</li>
   * <li>Process position size changes chronologically</li>
   * <li>For each period: calculate days × units × dailyRate</li>
   * <li>Extend to current date if position has unpaid costs</li>
   * </ol>
   * 
   * @param openUnits original units in opening position (for unit cost calculation)
   * @param dailyFinanceCost total daily finance cost for original position size
   * @param dayUnitsMap chronologically ordered position size changes (date → units)
   * @return calculated costs, days, and end date
   */
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

  /**
   * Removes historical entries from position tracking map and returns most recent position size.
   * 
   * <p>
   * Cleans up position tracking data when finance cost payments reset the calculation baseline.
   * Removes entries before the reset date while preserving the effective position size.
   * </p>
   * 
   * @param dayUnitsMap position tracking map to clean up (modified in place)
   * @param removeBeforDate date before which entries should be removed
   * @return position size effective just before removal date, or 0.0 if none exists
   */
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
