package grafioschtrader.repository;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.entities.InstrumentLogo;

public interface InstrumentLogoJpaRepositoryCustom extends BaseRepositoryCustom<InstrumentLogo> {

  /**
   * Normalizes, sanitizes and validates a user-uploaded {@link InstrumentLogo} draft without persisting anything. Used
   * both by the pre-validation endpoint in Dialog (D) and immediately before persistence on create.
   */
  void validateForCreate(InstrumentLogo instrumentLogo);

  /**
   * Normalizes, validates and (when replacement SVG markup is supplied) sanitizes an update to an existing
   * {@link InstrumentLogo} without persisting anything. Uniqueness checks exclude {@code existingEntity}.
   */
  void validateForUpdate(InstrumentLogo instrumentLogo, InstrumentLogo existingEntity);
}
