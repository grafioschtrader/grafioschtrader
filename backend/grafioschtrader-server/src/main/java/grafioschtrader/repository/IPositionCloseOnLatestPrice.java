package grafioschtrader.repository;

import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.reportviews.SecuritycurrencyPositionSummary;

/**
 * The calculation of an individual open position can be carried out in different ways. Normally, a hypothetical closing
 * position is created. In addition, the percentage profit/loss may also be calculated. This is why this interface
 * exists.
 * 
 * @param <S> Normally it is a security
 * @param <U> Evaluation which SecuritycurrencyPositionSummary extended+-
 */
public interface IPositionCloseOnLatestPrice<S extends Securitycurrency<S>, U extends SecuritycurrencyPositionSummary<S>> {
  void calculatePositionClose(U securitycurrencyPositionSummary, Double price);
}
