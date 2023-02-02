package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import grafioschtrader.entities.Securitycurrency;

@NoRepositoryBean
public interface SecurityCurrencypairJpaRepository<S extends Securitycurrency<S>> extends JpaRepository<S, Integer> {
  S findByIdSecuritycurrency(Integer idSecuritycurrency);
}
