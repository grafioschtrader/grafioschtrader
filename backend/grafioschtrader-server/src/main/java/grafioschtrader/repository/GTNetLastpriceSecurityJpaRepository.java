package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.GTNetLastpriceSecurity;

public interface GTNetLastpriceSecurityJpaRepository
    extends JpaRepository<GTNetLastpriceSecurity, Integer>, GTNetLastpriceSecurityJpaRepositoryCustom {
}
