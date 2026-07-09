package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.repository.RepositoryHelper;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.TaxYearCorrection;
import jakarta.transaction.Transactional;

public class TaxYearCorrectionJpaRepositoryImpl extends BaseRepositoryImpl<TaxYearCorrection>
    implements TaxYearCorrectionJpaRepositoryCustom {

  @Autowired
  private TaxYearCorrectionJpaRepository taxYearCorrectionJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private IctaxSecurityTaxDataJpaRepository ictaxSecurityTaxDataJpaRepository;

  @Override
  public TaxYearCorrection saveOnlyAttributes(TaxYearCorrection taxYearCorrection,
      TaxYearCorrection existingEntity, Set<Class<? extends Annotation>> updatePropertyLevelClasses)
      throws Exception {
    validateBeforeSave(taxYearCorrection);
    return RepositoryHelper.saveOnlyAttributes(taxYearCorrectionJpaRepository, taxYearCorrection, existingEntity,
        updatePropertyLevelClasses);
  }

  @Override
  @Transactional
  public int delEntityWithTenant(Integer idTaxYearCorrection, Integer idTenant) {
    return taxYearCorrectionJpaRepository.deleteByIdTaxYearCorrectionAndIdTenant(idTaxYearCorrection, idTenant);
  }

  /**
   * Validates a correction before it is persisted: only one correction may exist per tenant, security and tax
   * year; the two override fields are mutually exclusive; and an override is only allowed when ICTax data exists
   * for the security's ISIN in that tax year (otherwise only a comment may be stored).
   *
   * @param tyc the correction to validate
   */
  private void validateBeforeSave(TaxYearCorrection tyc) {
    Optional<TaxYearCorrection> existing = taxYearCorrectionJpaRepository
        .findByIdTenantAndIdSecuritycurrencyAndTaxYear(tyc.getIdTenant(), tyc.getIdSecuritycurrency(),
            tyc.getTaxYear());
    if (existing.isPresent() && !existing.get().getIdTaxYearCorrection().equals(tyc.getIdTaxYearCorrection())) {
      throw new DataViolationException("tax.year", "gt.taxyearcorrection.exists",
          new Object[] { tyc.getTaxYear() });
    }
    if (tyc.isUseTaxableAmount() && tyc.getTaxableIncome() != null) {
      throw new DataViolationException("taxable.income", "gt.taxyearcorrection.mutual.exclusive", null);
    }
    if (tyc.isUseTaxableAmount() || tyc.getTaxableIncome() != null) {
      Security security = securityJpaRepository.findById(tyc.getIdSecuritycurrency()).orElse(null);
      String isin = security == null ? null : security.getIsin();
      if (isin == null || isin.isEmpty()
          || ictaxSecurityTaxDataJpaRepository.findByIsinInAndTaxYear(Set.of(isin), tyc.getTaxYear()).isEmpty()) {
        throw new DataViolationException("tax.year", "gt.taxyearcorrection.no.ictax.data",
            new Object[] { tyc.getTaxYear() });
      }
    }
  }
}
