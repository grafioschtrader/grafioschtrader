package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.SecurityAction;

public interface SecurityActionJpaRepository extends JpaRepository<SecurityAction, Integer> {

  List<SecurityAction> findAllByOrderByActionDateDesc();

  /**
   * Tells whether the given security is the successor of an ISIN change, that is whether it was referenced as the new
   * security of a {@link SecurityAction}. Such a security starts its life at the action date, so a split dated at or
   * before its first historical quote belongs to its predecessor and must not be applied a second time.
   *
   * @param idSecuritycurrency the security to test
   * @return true when at least one ISIN change points to this security as its new security
   */
  boolean existsBySecurityNew_IdSecuritycurrency(Integer idSecuritycurrency);
}
