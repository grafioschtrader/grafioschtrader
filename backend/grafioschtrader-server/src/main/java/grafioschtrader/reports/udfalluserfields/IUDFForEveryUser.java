package grafioschtrader.reports.udfalluserfields;

import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup;
import grafioschtrader.types.UDFSpecialType;

public interface IUDFForEveryUser {
  
   void addUDFForEveryUser(SecuritycurrencyUDFGroup securitycurrencyUDFGroup, boolean recreate);
   UDFSpecialType getUDFSpecialType();
   
   boolean mayRunInBackground();
}
