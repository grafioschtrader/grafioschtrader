package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.TaxCountry;

public interface TaxCountryJpaRepository extends JpaRepository<TaxCountry, Integer> {
}
