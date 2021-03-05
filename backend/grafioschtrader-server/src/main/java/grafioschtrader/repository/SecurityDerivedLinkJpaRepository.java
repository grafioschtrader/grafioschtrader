package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.SecurityDerivedLink;
import grafioschtrader.entities.SecurityDerivedLink.SecurityDerivedLinkKey;

public interface SecurityDerivedLinkJpaRepository
    extends JpaRepository<SecurityDerivedLink, SecurityDerivedLinkKey>, SecurityDerivedLinkJpaRepositoryCustom {

  List<SecurityDerivedLink> findByIdEmIdSecuritycurrencyOrderByIdEmIdSecuritycurrency(Integer idSecuritycurrency);

  void deleteByIdEmIdSecuritycurrency(Integer idSecuritycurrency);

}
