package grafioschtrader.service;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections4.map.MultiKeyMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetLastpriceCurrencypair;
import grafioschtrader.entities.GTNetLastpriceSecurity;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.GTNetJpaRepository;
import grafioschtrader.repository.GTNetLastpriceCurrencypairJpaRepository;
import grafioschtrader.repository.GTNetLastpriceSecurityJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Service for orchestrating intraday price updates from GTNet providers.
 *
 * This service acts as the consumer-side coordinator for the intraday price sharing feature.
 * It manages the flow of price data from GTNet providers into the local Security and Currencypair tables.
 *
 * <h3>Planned Workflow</h3>
 * <ol>
 *   <li>Find active providers (domains with SS_OPEN state and lastpriceConsumerUsage > 0)</li>
 *   <li>Query each provider for their current price data</li>
 *   <li>Merge newer prices into local GTNetLastprice* tables</li>
 *   <li>Update local Security/Currencypair entities with the merged prices</li>
 *   <li>Log operations to GTNetLastpriceLog (and optionally GTNetLastpriceDetailLog)</li>
 * </ol>
 *
 * <h3>Current Status</h3>
 * This service is incomplete. The readUpdateGetLastpriceValues method exists but is not connected
 * to the update workflow. Key TODOs:
 * <ul>
 *   <li>Implement M2M calls to fetch prices from remote providers</li>
 *   <li>Complete the MultiKeyMap-based merging logic for currency pairs</li>
 *   <li>Add similar logic for securities (by ISIN)</li>
 *   <li>Wire the service into scheduled jobs or on-demand refresh endpoints</li>
 * </ul>
 *
 * @see GTNet#lastpriceConsumerUsage for provider priority configuration
 * @see GTNetLastpriceCurrencypair for the currency pair price cache
 * @see GTNetLastpriceSecurity for the security price cache
 */
@Service
public class GTNetLastpriceService {

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private GTNetLastpriceSecurityJpaRepository gtNetLastpriceSecurityJpaRepository;

  @Autowired
  private GTNetLastpriceCurrencypairJpaRepository gtTNetLastpriceCurrencypairJpaRepository;

  public GTNetLastpriceService.SecurityCurrency updateLastpriceIncludeSupplier(List<Security> securyties,
      List<Currencypair> currencypairs, List<Currencypair> currenciesNotInList) {

    List<GTNet> gtNetsOpen = gtNetJpaRepository.findByGtNetConfig_LastpriceConsumerUsageAndLastpriceServerState(
        GTNetServerStateTypes.SS_OPEN.getValue(), GTNetServerStateTypes.SS_OPEN.getValue());
    currencypairJpaRepository.updateLastPriceByList(currenciesNotInList);

    return new GTNetLastpriceService.SecurityCurrency(securityJpaRepository.updateLastPriceByList(securyties),
        currencypairJpaRepository.updateLastPriceByList(currencypairs));
  }

  private List<GTNetLastpriceCurrencypair> readUpdateGetLastpriceValues(List<Currencypair> currencypairs) {
    List<String> fromCurrencies = currencypairs.stream().map(Currencypair::getFromCurrency)
        .collect(Collectors.toList());
    List<String> toCurrencies = currencypairs.stream().map(Currencypair::getToCurrency).collect(Collectors.toList());

    List<GTNetLastpriceCurrencypair> gtNetLastpriceList = gtTNetLastpriceCurrencypairJpaRepository
        .getLastpricesByListByFromAndToCurrencies(fromCurrencies, toCurrencies);
    MultiKeyMap<String, GTNetLastpriceCurrencypair> lastCurrencyPriceMap = new MultiKeyMap<>();

    return gtNetLastpriceList;
  }

  public static class SecurityCurrency {
    public List<Security> securities;
    public List<Currencypair> currencypairs;

    public SecurityCurrency(final List<Security> securities, final List<Currencypair> currencypairs) {
      super();
      this.securities = securities;
      this.currencypairs = currencypairs;
    }
  }
}
