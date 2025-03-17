package grafioschtrader.reports.udfalluserfields;

import grafiosch.types.IUDFSpecialType;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup;

/**
 * Certain user-defined fields are intended for all users. These are
 * automatically filled with values. A supplier of such values for these fields
 * must implement this interface.
 */
public interface IUDFForEveryUser {

  void addUDFForEveryUser(SecuritycurrencyUDFGroup securitycurrencyUDFGroup, boolean recreate);

  IUDFSpecialType getUDFSpecialType();

  /**
   * Return true if this is also to be performed as a background task.
   */
  boolean mayRunInBackground();
}
