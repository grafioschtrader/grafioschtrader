package grafioschtrader.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.SecurityDerivedLink;
import grafioschtrader.entities.SecurityDerivedLink.SecurityDerivedLinkKey;

public interface SecurityDerivedLinkJpaRepository
    extends JpaRepository<SecurityDerivedLink, SecurityDerivedLinkKey>, SecurityDerivedLinkJpaRepositoryCustom {

  List<SecurityDerivedLink> findByIdEmIdSecuritycurrencyOrderByIdEmIdSecuritycurrency(Integer idSecuritycurrency);

  /**
   * Returns the formula links of several derived securities in one query, used to order their intraday calculation.
   *
   * @param idsSecuritycurrency the ids of the derived securities
   * @return the additional links (variables other than the main link) of these securities
   */
  List<SecurityDerivedLink> findByIdEmIdSecuritycurrencyIn(Collection<Integer> idsSecuritycurrency);

  void deleteByIdEmIdSecuritycurrency(Integer idSecuritycurrency);

}
