package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.investing.InvestingConnector;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper.SecurityHistoricalDate;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.types.SpecialInvestmentInstruments;

class InvestingConnectorTest extends BaseFeedConnectorCheck {

  private InvestingConnector investingConnector = new InvestingConnector();

  @Test
  void updateSecurityLastPriceTest() {
    updateSecurityLastPrice();
  }

  @Override
  protected List<SecurityHistoricalDate> getHistoricalSecurities() {
    List<SecurityHistoricalDate> hisoricalDate = new ArrayList<>();
    try {
      hisoricalDate.add(new SecurityHistoricalDate("CAC 40", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES,
          "indices/france-40"));
      hisoricalDate
          .add(new SecurityHistoricalDate("db x-trackers Emerging MARKETS LIQUID EUROBOND INDEX ETF (EUR)22.10.2010",
              SpecialInvestmentInstruments.ETF, "etfs/db-em-liquid-eurobond---eur"));
      hisoricalDate.add(new SecurityHistoricalDate("Apple Inc (AAPL)", SpecialInvestmentInstruments.DIRECT_INVESTMENT,
          "equities/apple-computer-inc"));
      hisoricalDate.add(new SecurityHistoricalDate("MOEX Russia (IMOEX)", SpecialInvestmentInstruments.NON_INVESTABLE_INDICES,
          "indices/mcx"));
      hisoricalDate.add(new SecurityHistoricalDate("Bitcoin Tracker EUR XBT Provider (SE0007525332)", SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT,
          "etfs/bitcoin-tracker-eur-xbt-provider"));
      hisoricalDate
      .add(new SecurityHistoricalDate("iShares MSCI Emerging Markets ETF (EEM)",
          SpecialInvestmentInstruments.ETF, "etfs/ishares-msci-emg-markets"));
    } catch (ParseException pe) {
      pe.printStackTrace();
    }
    return hisoricalDate;
  }

  @Test
  void updateCurrencyPairLastPriceTest() {

    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_EUR, GlobalConstants.MC_CHF));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.CC_BTC, GlobalConstants.MC_USD));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_CHF, "AUD"));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_USD, GlobalConstants.MC_CHF));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_USD, GlobalConstants.MC_EUR));
    currencies.add(ConnectorTestHelper.createCurrencyPair(GlobalConstants.MC_GBP, GlobalConstants.MC_EUR));

    currencies.parallelStream().forEach(currencyPair -> {
      try {
        investingConnector.updateCurrencyPairLastPrice(currencyPair);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println(String.format("%s/%s last:%f change: %f", currencyPair.getFromCurrency(),
          currencyPair.getToCurrency(), currencyPair.getSLast(), currencyPair.getSChangePercentage()));
      assertThat(currencyPair.getSLast()).isNotNull().isGreaterThan(0.0);
    });
  }

  @Override
  protected IFeedConnector getIFeedConnector() {
    return investingConnector;
  }

}
