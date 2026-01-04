package grafioschtrader.dto;

import java.util.List;
import java.util.Set;

import grafioschtrader.entities.Securitycurrency;

/**
 * DTO containing securities or currency pairs with their GTNet exchange configurations.
 * The GTNet fields (gtNetLastpriceRecv, gtNetHistoricalRecv, gtNetLastpriceSend, gtNetHistoricalSend)
 * are now directly on the Securitycurrency entity.
 *
 * @param <T> the type of securitycurrency (Security or Currencypair)
 */
public class GTSecuritiyCurrencyExchange<T extends Securitycurrency<T>> {
  /** List of all securities or currency pairs (includes GTNet fields directly) */
  public List<T> securitiescurrenciesList;

  /** Set of idSecuritycurrency that have supplier details (for expandable rows) */
  public Set<Integer> idSecuritycurrenies;
}
