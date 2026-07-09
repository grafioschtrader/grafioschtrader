package grafioschtrader.repository;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.entities.TaxYearCorrection;

public interface TaxYearCorrectionJpaRepositoryCustom extends BaseRepositoryCustom<TaxYearCorrection> {

  int delEntityWithTenant(Integer idTaxYearCorrection, Integer idTenant);
}
