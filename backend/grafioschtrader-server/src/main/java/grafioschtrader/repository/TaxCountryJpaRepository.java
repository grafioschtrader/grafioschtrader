package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.TaxCountry;

public interface TaxCountryJpaRepository extends JpaRepository<TaxCountry, Integer> {
  boolean existsByCountryCode(String countryCode);

  /** The tax country of an ISO country code, null when the administrator has not created it. */
  TaxCountry findByCountryCode(String countryCode);
}
