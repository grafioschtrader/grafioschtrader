package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.entities.TaxYear;

public interface TaxYearJpaRepository extends JpaRepository<TaxYear, Integer> {

  List<TaxYear> findByIdTaxCountryOrderByTaxYearDesc(int idTaxCountry);
}
