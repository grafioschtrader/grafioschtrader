package grafioschtrader.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.ImportTransactionPos;

public interface ImportTransactionPosJpaRepository
    extends JpaRepository<ImportTransactionPos, Integer>, ImportTransactionPosJpaRepositoryCustom {

  Optional<ImportTransactionPos> findByIdTransaction(Integer idTransaction);

  List<ImportTransactionPos> findByIdTransactionHeadAndIdTenant(Integer idTransactionHead, Integer idTenant);

  ImportTransactionPos findByIdTransactionPosAndIdTenant(Integer idTransactionPos, Integer idTenant);

  //@formatter:off
  /**
   * Finds potential transaction matches for the specified import position IDs.
   * Is used to indicate to the user that the item to be imported may already exist as a transcation.
   * <p>
   * For each <code>imp_trans_pos</code> in <code>idTransactionPosList</code> that has no transaction yet
   * (<code>ip.id_transaction IS NULL</code>) and whose detection was not switched off by the user
   * (<code>ip.id_transaction_maybe <> 0 OR ip.id_transaction_maybe IS NULL</code>), this query:
   * <ul>
   *   <li>Joins on <code>transaction</code> by matching security, transaction type, the securities account of the
   *       import head, the day and the units</li>
   *   <li>Also requires either the total amount to match at cent level, compared by absolute value because a
   *       purchase is negative on the transaction but may still be positive on a position whose total has not been
   *       calculated yet, or the quotation to match (<code>ip.quotation = t.quotation</code>)</li>
   * </ul>
   * Results are ordered by import position ID.
   * <p>
   * Named query: ImportTransactionPos.getIdTransactionPosWithPossibleTransactionByIdTransactionPos
   *
   * @param idTransactionPosList list of import position IDs to evaluate
   * @return a two-dimensional Integer array where each element is
   *         <code>[id_trans_pos, id_transaction]</code>
   */
  //@formatter:on
  @Query(nativeQuery = true)
  Integer[][] getIdTransactionPosWithPossibleTransactionByIdTransactionPos(List<Integer> idTransactionPosList);

  //@formatter:off
  /**
   * Finds potential transaction matches for all import positions under the given header.
   * <p>
   * Identical matching logic to the position-level lookup, scoped to <code>ip.id_trans_head = ?1</code>:
   * <ul>
   *   <li>Considers positions without a transaction whose detection was not switched off by the user</li>
   *   <li>Joins on <code>transaction</code> by security, transaction type, securities account, day and units</li>
   *   <li>Requires either the same total amount (absolute value, at cent level) or the same quotation</li>
   * </ul>
   * Distinct pairs are returned and ordered by import position ID.
   * <p>
   * Named query: ImportTransactionPos.getIdTransactionPosWithPossibleTransactionByIdTransactionHead
   *
   * @param idTransactionHead import header ID whose positions to match
   * @return a two-dimensional Integer array of <code>[id_trans_pos, id_transaction]</code>
   */
  //@formatter:on
  @Query(nativeQuery = true)
  Integer[][] getIdTransactionPosWithPossibleTransactionByIdTransactionHead(Integer idTransactionHead);

  //@formatter:off
  /**
   * Finds import transaction positions that match the given ISIN and currency but have no security assigned.
   * Used to auto-assign securities after GTNet import successfully links or creates a security.
   * <p>
   * Currency matching logic:
   * <ul>
   *   <li>Matches against <code>currency_security</code> if present</li>
   *   <li>Falls back to <code>currency_account</code> if <code>currency_security</code> is null</li>
   * </ul>
   *
   * Named query: ImportTransactionPos.findByIsinAndCurrencyWithNoSecurity
   *
   * @param isin the ISIN to match
   * @param currency the currency to match against
   * @return list of import transaction positions without assigned security matching the ISIN and currency
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<ImportTransactionPos> findByIsinAndCurrencyWithNoSecurity(String isin, String currency);

}
