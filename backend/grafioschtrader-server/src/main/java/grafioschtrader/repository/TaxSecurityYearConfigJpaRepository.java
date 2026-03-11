package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.TaxSecurityYearConfig;
import grafioschtrader.entities.TaxSecurityYearConfigId;

/**
 * Repository for managing per-security tax exclusion configuration. A row means the security is excluded from the
 * eCH-0196 tax statement export for that tenant and year.
 */
public interface TaxSecurityYearConfigJpaRepository
    extends JpaRepository<TaxSecurityYearConfig, TaxSecurityYearConfigId> {

  List<TaxSecurityYearConfig> findByIdIdTenantAndIdTaxYear(int idTenant, short taxYear);
}
