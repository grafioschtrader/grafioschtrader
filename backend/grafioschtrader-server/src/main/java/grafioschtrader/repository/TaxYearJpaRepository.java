package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.TaxYear;

public interface TaxYearJpaRepository extends JpaRepository<TaxYear, Integer> {

  List<TaxYear> findByIdTaxCountryOrderByTaxYearDesc(int idTaxCountry);

  /**
   * Reports whether any country has a tax year row for the given year. Used to decide whether a year posted by a client
   * is one the installation actually knows, rather than any value the {@code SMALLINT} column would accept.
   *
   * @param taxYear the calendar year to look for
   * @return true when at least one {@link TaxYear} row carries that year
   */
  boolean existsByTaxYear(Short taxYear);
}
