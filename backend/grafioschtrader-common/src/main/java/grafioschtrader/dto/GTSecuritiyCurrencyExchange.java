package grafioschtrader.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

import grafioschtrader.entities.GTNetExchange;
import grafioschtrader.entities.Securitycurrency;

/**
 * DTO containing securities or currency pairs with their GTNetExchange configurations.
 *
 * @param <T> the type of securitycurrency (Security or Currencypair)
 */
public class GTSecuritiyCurrencyExchange<T extends Securitycurrency<T>> {
  /** List of all securities or currency pairs */
  public List<T> securitiescurrenciesList;

  /** Map of idSecuritycurrency to GTNetExchange config (only entries that exist in DB) */
  public Map<Integer, GTNetExchange> exchangeMap;

  /** Set of idSecuritycurrency that have supplier details (for expandable rows) */
  public Set<Integer> idSecuritycurrenies;
}
