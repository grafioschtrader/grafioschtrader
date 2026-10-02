package grafioschtrader.report.pdf;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.dto.PerformanceReportSettings;
import grafioschtrader.repository.TenantJpaRepository;

/** Separate write path: a failed PDF request can never alter a tenant's remembered settings. */
@Service
public class PerformanceReportSettingsService {
  private final TenantJpaRepository tenants;
  private final PerformanceReportValidation validation;

  public PerformanceReportSettingsService(TenantJpaRepository tenants, PerformanceReportValidation validation) {
    this.tenants = tenants;
    this.validation = validation;
  }

  @Transactional(readOnly = true)
  public PerformanceReportSettings get(Integer idTenant) {
    return tenants.findById(idTenant).orElseThrow().getReportSettings();
  }

  @Transactional
  public void save(Integer idTenant, PerformanceReportSettings settings) {
    validation.settings(settings);
    var tenant = tenants.findById(idTenant).orElseThrow();
    tenant.setReportSettings(settings);
    tenants.save(tenant);
  }
}
