package grafioschtrader.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.rest.UpdateCreateDeleteWithTenantJpaRepository;
import grafioschtrader.entities.TaxYearCorrection;

public interface TaxYearCorrectionJpaRepository extends JpaRepository<TaxYearCorrection, Integer>,
    TaxYearCorrectionJpaRepositoryCustom, UpdateCreateDeleteWithTenantJpaRepository<TaxYearCorrection> {

  /**
   * Finds all corrections of a tenant for the given tax years. Used by the dividends report to apply overrides
   * for the report year and to flag securities with a correction in the previous year.
   *
   * @param idTenant the tenant identifier
   * @param taxYears the tax years to load, typically the report year and the previous year
   * @return matching corrections, possibly empty
   */
  List<TaxYearCorrection> findByIdTenantAndTaxYearIn(Integer idTenant, Collection<Short> taxYears);

  /**
   * Finds all corrections of a tenant for one security across all tax years, newest year first. Feeds the
   * maintenance dialog in the frontend.
   *
   * @param idTenant            the tenant identifier
   * @param idSecuritycurrency  the security identifier
   * @return matching corrections ordered by tax year descending
   */
  List<TaxYearCorrection> findByIdTenantAndIdSecuritycurrencyOrderByTaxYearDesc(Integer idTenant,
      Integer idSecuritycurrency);

  /**
   * Finds the single correction of a tenant for one security and tax year. Used for the uniqueness validation
   * before saving.
   *
   * @param idTenant           the tenant identifier
   * @param idSecuritycurrency the security identifier
   * @param taxYear            the tax year
   * @return the matching correction or empty
   */
  Optional<TaxYearCorrection> findByIdTenantAndIdSecuritycurrencyAndTaxYear(Integer idTenant,
      Integer idSecuritycurrency, Short taxYear);

  int deleteByIdTaxYearCorrectionAndIdTenant(Integer idTaxYearCorrection, Integer idTenant);
}
