package grafioschtrader.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.IctaxExchangeRate;

public interface IctaxExchangeRateJpaRepository extends JpaRepository<IctaxExchangeRate, Integer> {

  /**
   * Returns every official exchange rate of one tax year, for the editable table below the tax year node. The import
   * also uses it to find the rows it has to update rather than insert.
   *
   * @param idTaxYear the tax year whose rates are wanted
   * @return the rates of that year, ordered by currency
   */
  List<IctaxExchangeRate> findByIdTaxYearOrderByCurrency(Integer idTaxYear);

  /**
   * Returns the rates of several tax years at once, for the year to currency lookup of the Swiss dividend report.
   *
   * @param idTaxYears the tax years to read
   * @return every rate of those years
   */
  List<IctaxExchangeRate> findByIdTaxYearIn(Collection<Integer> idTaxYears);

  /**
   * Counts the imported rates per tax year, so the tree can hide the expansion toggle on a year that has none.
   *
   * @return array pairs of {@code id_tax_year} and row count
   */
  @Query("SELECT r.idTaxYear, COUNT(r) FROM IctaxExchangeRate r GROUP BY r.idTaxYear")
  List<Object[]> countGroupedByIdTaxYear();
}
