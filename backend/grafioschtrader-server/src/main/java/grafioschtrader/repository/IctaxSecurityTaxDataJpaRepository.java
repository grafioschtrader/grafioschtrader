package grafioschtrader.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.IctaxSecurityTaxData;

public interface IctaxSecurityTaxDataJpaRepository extends JpaRepository<IctaxSecurityTaxData, Integer> {

  /**
   * Finds ICTax security tax data for the given ISINs and tax year. Joins through tax_upload → tax_year to filter by
   * year. Used for enriching the SecurityDividendsReport with official Swiss tax values.
   *
   * @param isins   collection of ISINs to look up
   * @param taxYear the tax year to query
   * @return list of matching tax data entries with their payments eagerly loaded
   */
  @Query("SELECT d FROM IctaxSecurityTaxData d LEFT JOIN FETCH d.payments "
      + "JOIN TaxUpload u ON d.idTaxUpload = u.idTaxUpload "
      + "JOIN TaxYear y ON u.idTaxYear = y.idTaxYear "
      + "WHERE d.isin IN :isins AND y.taxYear = :taxYear")
  List<IctaxSecurityTaxData> findByIsinInAndTaxYear(Collection<String> isins, short taxYear);

  void deleteByIdTaxUpload(int idTaxUpload);
}
