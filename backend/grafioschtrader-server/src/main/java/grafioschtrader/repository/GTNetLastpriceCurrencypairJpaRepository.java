package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.GTNetLastpriceCurrencypair;

public interface GTNetLastpriceCurrencypairJpaRepository
    extends JpaRepository<GTNetLastpriceCurrencypair, Integer>, GTNetLastpriceCurrencypairJpaRepositoryCustom {
}
