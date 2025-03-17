package grafioschtrader.connector.instrument.ecb;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.BaseFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.repository.EcbExchangeRatesRepository;
import grafioschtrader.repository.EcbExchangeRatesRepository.CalcRates;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.HistoryquoteCreateType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * This connector offers the Euro foreign exchange reference rates. This
 * connector differs from the others in that the historical price data is read
 * directly from the repository. Cross rates are calculated, e.g. for the
 * USD/CHF currency pair, the EUR/USD and EUR/CHF currency rates are used.
 *
 * The check for an existing currency pair can be checked via the repository.
 */
@Component
public class EcbCrossRateConnector extends BaseFeedConnector {

  @Autowired
  private EcbExchangeRatesRepository ecbExchangeRatesRepository;

  private static Map<FeedSupport, FeedIdentifier[]> supportedFeed;
  static {
    supportedFeed = new HashMap<>();
    supportedFeed.put(FeedSupport.FS_HISTORY, new FeedIdentifier[] { FeedIdentifier.CURRENCY });
  }

  public EcbCrossRateConnector() {
    super(supportedFeed, "ecb", "ECB Cross Rate CET 16:00", null, EnumSet.noneOf(UrlCheck.class));
  }

  @Override
  public String getCurrencypairHistoricalDownloadLink(final Currencypair currencypair) {
    return EcbLoader.ECB_BASE_URL + EcbLoader.ECB_SINGLE_DAY_EXTEND;
  }

  @Override
  public List<Historyquote> getEodCurrencyHistory(final Currencypair currencyPair, final Date from, final Date to)
      throws IOException, ParseException, InterruptedException {
    List<CalcRates> calcRates = null;
    final List<Historyquote> historyquotes = new ArrayList<>();

    if (currencyPair.getFromCurrency().equals(GlobalConstants.MC_EUR)) {
      calcRates = ecbExchangeRatesRepository.getRatesByFromToDate(currencyPair.getToCurrency(), from, to, true);
    } else if (currencyPair.getToCurrency().equals(GlobalConstants.MC_EUR)) {
      calcRates = ecbExchangeRatesRepository.getRatesByFromToDate(currencyPair.getFromCurrency(), from, to, false);
    } else {
      // Cross currency calculation
      calcRates = ecbExchangeRatesRepository.getCrossCurrencyRateForPeriod(currencyPair.getFromCurrency(),
          currencyPair.getToCurrency(), from, to);
    }
    for (CalcRates calcRate : calcRates) {
      historyquotes.add(new Historyquote(currencyPair.getIdSecuritycurrency(), HistoryquoteCreateType.CONNECTOR_CREATED,
          calcRate.getDate(), calcRate.getRate()));
    }
    return historyquotes;
  }

  @Override
  protected <S extends Securitycurrency<S>> boolean clearAndCheckUrlPatternSecuritycurrencyConnector(
      Securitycurrency<S> securitycurrency, FeedSupport feedSupport, String urlExtend, String errorMsgKey,
      FeedIdentifier feedIdentifier, SpecialInvestmentInstruments specialInvestmentInstruments,
      AssetclassType assetclassType) {

    boolean clear = super.clearAndCheckUrlPatternSecuritycurrencyConnector(securitycurrency, feedSupport, urlExtend,
        errorMsgKey, feedIdentifier, specialInvestmentInstruments, assetclassType);
    checkForExistenceCurrencies((Currencypair) securitycurrency);
    return clear;
  }

  private void checkForExistenceCurrencies(Currencypair currencypair) {
    String[] currencies = ecbExchangeRatesRepository.checkForExistenceCurrencies(currencypair.getFromCurrency(),
        currencypair.getToCurrency());
    if (!((currencies.length == 1 && (currencypair.getFromCurrency().equals(GlobalConstants.MC_EUR)
        || currencypair.getToCurrency().equals(GlobalConstants.MC_EUR))) || currencies.length == 2)) {
      throw new GeneralNotTranslatedWithArgumentsException("gt.connector.ecb.not.supported.currency", null);
    }
  }

}
