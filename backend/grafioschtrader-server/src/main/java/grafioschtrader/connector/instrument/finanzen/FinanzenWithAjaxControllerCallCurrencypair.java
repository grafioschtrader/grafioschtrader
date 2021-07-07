package grafioschtrader.connector.instrument.finanzen;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Currencypair;

/*-
 * Finanzen.ch
 * https://www.finanzen.ch/devisen/historisch/euro-us_dollar-kurs
 * https://www.finanzen.ch/ajax/ExchangeRateController_HistoricPriceList/euro-us_dollar-kurs/27.2.2018_27.11.2018
 * Link: devisen/historisch/euro-us_dollar-kurs
 *
 * https://www.finanzen.ch/devisen/bitcoin-franken-kurs/historisch
 * https://www.finanzen.ch/ajax/ExchangeRateController_HistoricPriceList/bitcoin-franken-kurs/26.1.2018_26.11.2018
 *
 * https://www.finanzen.net/devisen/dollarkurs/historisch
 * https://www.finanzen.net/Ajax/ExchangeRateController_HistoricPriceList/dollarkurs/26.1.2014_26.11.2018
 *
 * Finanzen.net
 * https://www.finanzen.net/devisen/dollarkurs/historisch
 * https://www.finanzen.net/Ajax/ExchangeRateController_HistoricPriceList/dollarkurs/8.1.2018_27.11.2018
 * Link: devisen/dollarkurs/historisch
 *
 * https://www.finanzen.net/devisen/bitcoin-franken-kurs/historisch
 * https://www.finanzen.net/Ajax/ExchangeRateController_HistoricPriceList/bitcoin-franken-kurs/27.5.2018_27.11.2018
 * Link: devisen/bitcoin-franken-kurs/historisch
 *
 */
public class FinanzenWithAjaxControllerCallCurrencypair extends FinanzenWithAjaxControllerCall<Currencypair> {
  private final Logger log = LoggerFactory.getLogger(this.getClass());
  private int productUrlPartNo;

  public FinanzenWithAjaxControllerCallCurrencypair(String domain, IFeedConnector feedConnector, Locale locale,
      int productUrlPartNo) {
    super(domain, feedConnector, locale);
    this.productUrlPartNo = productUrlPartNo;
  }

  @Override
  protected String getAjaxUrl(Currencypair currencypair, Date from, Date to) {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(URL_DATE_FORMAT);
    final String[] urlParts = currencypair.getUrlHistoryExtend().split("/");
    final String url = domain + "ajax/ExchangeRateController_HistoricPriceList/" + urlParts[productUrlPartNo] + "/"
        + dateFormat.format(from) + "_" + dateFormat.format(to);
    log.info("In {} for currency pair {} is URL for Ajax call {}", feedConnector.getID(), currencypair.getName(), url);
    return url;
  }

  @Override
  protected String getHistoricalDownloadLink(Currencypair currencypair) {
    return feedConnector.getCurrencypairHistoricalDownloadLink(currencypair);
  }

}
