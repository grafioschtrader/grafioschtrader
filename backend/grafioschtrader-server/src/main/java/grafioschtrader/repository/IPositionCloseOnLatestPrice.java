package grafioschtrader.repository;

import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.reportviews.SecuritycurrencyPositionSummary;

public interface IPositionCloseOnLatestPrice<S extends Securitycurrency<S>, U extends SecuritycurrencyPositionSummary<S>> {
  void calculatePositionClose(U securitycurrencyPositionSummary, Double price);
}
