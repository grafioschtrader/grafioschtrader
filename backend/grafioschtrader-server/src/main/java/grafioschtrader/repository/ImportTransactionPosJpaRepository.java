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
   * For each <code>imp_trans_pos</code> in <code>idTransactionPosList</code> where no transaction
   * has yet been confirmed (<code>ip.id_transaction IS NULL</code>) but a match is flagged
   * (<code>ip.id_transaction_maybe <> 0 OR ip.id_transaction_maybe IS NULL</code>), this query:
   * <ul>
   *   <li>Joins on <code>transaction</code> by matching security ID, transaction type, cash account, date, and units</li>
   *   <li>Also requires either the cash-account amount to match (<code>t.id_cash_account = ip.cashaccount_amount</code>)
   *       or the recorded quotation to match (<code>ip.quotation = t.quotation</code>)</li>
   * </ul>
   * Results are ordered by import position ID.
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
   *   <li>Filters for unconfirmed positions with a “maybe” flag</li>
   *   <li>Joins on <code>transaction</code> by security ID, transaction type, cash account, date, and units</li>
   *   <li>Requires either matching cash-account amount or matching quotation</li>
   * </ul>
   * Distinct pairs are returned and ordered by import position ID.
   *
   * @param idTransactionHead import header ID whose positions to match
   * @return a two-dimensional Integer array of <code>[id_trans_pos, id_transaction]</code>
   */
  //@formatter:on
  @Query(nativeQuery = true)
  Integer[][] getIdTransactionPosWithPossibleTransactionByIdTransactionHead(Integer idTransactionHead);

}
