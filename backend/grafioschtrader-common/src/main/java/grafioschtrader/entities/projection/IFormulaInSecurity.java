package grafioschtrader.entities.projection;

public interface IFormulaInSecurity {
  Integer getIdLinkSecuritycurrency();

  String getFormulaPrices();

  Integer getIdSecuritycurrency();

  boolean isCalculatedPrice();

}
