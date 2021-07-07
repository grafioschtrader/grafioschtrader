package grafioschtrader.reportviews;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.entities.Securitycurrency;

/**
 * Contains the close price of a currency pair or security.
 *
 * @author Hugo Graf
 *
 * @param <T>
 */
public abstract class SecuritycurrencyPositionSummary<T extends Securitycurrency<?>> {

  @JsonIgnore
  public T securitycurrency;

  // Use sLast of security or closePrice of history quotes
  public Double closePrice = null;
  public Date closeDate;
}
