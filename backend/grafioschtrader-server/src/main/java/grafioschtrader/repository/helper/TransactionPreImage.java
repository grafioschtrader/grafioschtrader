package grafioschtrader.repository.helper;

import java.time.LocalDate;

import grafioschtrader.entities.Transaction;

/**
 * Immutable snapshot of the coordinates a {@link Transaction} occupied <em>before</em> it was updated. The hold tables
 * are keyed by cash account plus date and by security account plus security, so an update that moves a transaction to
 * another account or to a later date invalidates hold rows that the new coordinates alone can never reach.
 *
 * <p>
 * It has to be a copy rather than a reference to the existing entity: the "existing entity" handed to
 * {@code saveOnlyAttributes} is the managed instance that {@code merge} writes the new values into, so by the time the
 * transaction has been saved it no longer holds the old values. Capture the snapshot before the save.
 * </p>
 *
 * @param idCashaccount      the cash account the transaction was booked on
 * @param transactionDate    the date part of the former transaction time, matching the {@code tt_date} column the hold
 *                           tables are grouped by
 * @param idSecurityaccount  the security account, or {@code null} for a cash-only transaction
 * @param idSecuritycurrency the security, or {@code null} for a cash-only transaction
 */
public record TransactionPreImage(Integer idCashaccount, LocalDate transactionDate, Integer idSecurityaccount,
    Integer idSecuritycurrency) {

  /**
   * Captures the coordinates of an existing transaction, or returns {@code null} when there is nothing to capture
   * (creation rather than update). Call this before the transaction is saved.
   *
   * <p>
   * A transaction without an id is treated as absent: on the create path of a cash account transfer,
   * {@code UpdateCreate.checkAndSetEntityWithTenant} hands back the very instance being created as the "existing"
   * entity, so identity alone does not tell a create from an update.
   * </p>
   *
   * @param existingEntity the transaction as it is currently stored, may be {@code null} for a new transaction
   * @return the snapshot, or {@code null} when there is no stored predecessor
   */
  public static TransactionPreImage of(Transaction existingEntity) {
    if (existingEntity == null || existingEntity.getIdTransaction() == null) {
      return null;
    }
    return new TransactionPreImage(existingEntity.getCashaccount().getIdSecuritycashAccount(),
        existingEntity.getTransactionDate(), existingEntity.getIdSecurityaccount(),
        existingEntity.getSecurity() == null ? null : existingEntity.getSecurity().getIdSecuritycurrency());
  }

  /**
   * Whether the transaction was booked on a different cash account than the given one, so that the former account's
   * hold rows have to be recalculated as well.
   *
   * @param idCashaccountNow the cash account the transaction is booked on after the update
   * @return true when the cash account changed
   */
  public boolean cashaccountChanged(Integer idCashaccountNow) {
    return !idCashaccount.equals(idCashaccountNow);
  }

  /**
   * Whether the transaction was booked on a different security account or a different security than the given ones, so
   * that the former holdings series has to be rebuilt as well.
   *
   * @param idSecurityaccountNow  the security account after the update
   * @param idSecuritycurrencyNow the security after the update
   * @return true when either changed and the pre-image had a security position at all
   */
  public boolean securityPositionChanged(Integer idSecurityaccountNow, Integer idSecuritycurrencyNow) {
    if (idSecurityaccount == null || idSecuritycurrency == null) {
      return false;
    }
    return !idSecurityaccount.equals(idSecurityaccountNow) || !idSecuritycurrency.equals(idSecuritycurrencyNow);
  }

  /**
   * The earliest date that has to be recalculated when a transaction moves between dates. Hold rows are cumulative
   * running totals, so moving a transaction forward in time leaves every row between the old and the new date carrying
   * the old amount; recalculation therefore has to start at the earlier of the two.
   *
   * @param transactionDateNow the date the transaction carries after the update
   * @return the earlier of the former and the current date
   */
  public LocalDate earliestAffectedDate(LocalDate transactionDateNow) {
    return transactionDate.isBefore(transactionDateNow) ? transactionDate : transactionDateNow;
  }
}
