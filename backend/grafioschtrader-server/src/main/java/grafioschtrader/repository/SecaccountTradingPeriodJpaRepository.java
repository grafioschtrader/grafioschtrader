package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.SecaccountTradingPeriod;

/**
 * Repository for managing trading period definitions of security accounts. CRUD is primarily cascaded from the parent
 * {@link grafioschtrader.entities.Securityaccount} entity.
 */
public interface SecaccountTradingPeriodJpaRepository extends JpaRepository<SecaccountTradingPeriod, Integer> {

  List<SecaccountTradingPeriod> findByIdSecuritycashAccount(Integer idSecuritycashAccount);
}
