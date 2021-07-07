package grafioschtrader.entities.projection;

/**
 * IFormulaInSecurity can not be used directly for a spring data projection. But
 * with this interface it works.
 *
 * @author Hugo Graf
 *
 */
public interface IFormulaSecurityLoad extends IFormulaInSecurity {

  @Override
  default boolean isCalculatedPrice() {
    return getFormulaPrices() != null;
  }
}
