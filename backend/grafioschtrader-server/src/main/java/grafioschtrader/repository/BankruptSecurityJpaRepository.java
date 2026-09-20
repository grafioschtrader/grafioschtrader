package grafioschtrader.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.dto.IBankruptSecurityWithName;
import grafioschtrader.entities.BankruptSecurity;

/**
 * Repository of the instruments whose issuer no longer supplies price data. The table is small by nature - it holds one
 * row per instrument that has gone silent - so every read here is a full scan by design.
 */
public interface BankruptSecurityJpaRepository
    extends JpaRepository<BankruptSecurity, Integer>, UpdateCreateJpaRepository<BankruptSecurity> {

  Optional<BankruptSecurity> findByIdSecuritycurrency(Integer idSecuritycurrency);

  /**
   * Lists every marked instrument with the fields the maintenance table shows.
   *
   * <p>
   * Two closing dates are reported per row rather than one. {@code lastRealQuoteDate} is the newest price that did not
   * come from a filling ({@code create_type} 3 and 6 are excluded), {@code lastQuoteDate} is the newest price of any
   * kind. Their distance is how far the automatic filling currently reaches, which is the only thing that tells the
   * reader whether the marker is doing its work.
   * </p>
   *
   * <p>
   * Named query: {@code BankruptSecurity.findAllWithSecurityName}
   * </p>
   *
   * @return one row per marker, ordered by instrument name
   */
  @Query(nativeQuery = true)
  List<IBankruptSecurityWithName> findAllWithSecurityName();

  /**
   * Number of closing prices the instrument has at all. An instrument without a single price cannot be filled - the
   * linear filling needs one known price to start from - so a marker on it would silently do nothing and is refused.
   *
   * @param idSecuritycurrency the instrument to count for
   * @return how many {@code historyquote} rows exist for it
   */
  @Query(nativeQuery = true, value = "SELECT count(*) FROM historyquote WHERE id_securitycurrency = ?1")
  long countHistoryquotes(Integer idSecuritycurrency);
}
