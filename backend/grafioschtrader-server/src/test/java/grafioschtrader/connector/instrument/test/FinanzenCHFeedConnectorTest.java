package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.connector.instrument.finanzench.FinanzenCHFeedConnector;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.Language;
import grafioschtrader.types.SpecialInvestmentInstruments;

@SpringBootTest(classes = GTforTest.class)
class FinanzenCHFeedConnectorTest {

  @Test
  void updateSecurityLastPriceTest() {
    final List<Security> securities = new ArrayList<>();

    final FinanzenCHFeedConnector finanzenCHFeedConnector = new FinanzenCHFeedConnector();
    securities.add(createSecurityIntra("etf/zkb-gold-etf-aa-chf-klasse", AssetclassType.COMMODITIES,
        SpecialInvestmentInstruments.ETF, "CH0139101593"));
    securities.add(createSecurityIntra("aktien/swiss_re-aktie", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, "CH0126881561"));
    securities.add(createSecurityIntra("obligationen/swiss_life_holdingsf-anl_201323-obligation-2023-ch0212184078",
        AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT, "CH0212184078"));
    securities.add(createSecurityIntra("index/SLI", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, "CH0030252883"));

    securities.parallelStream().forEach(security -> {
      try {
        finanzenCHFeedConnector.updateSecurityLastPrice(security);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(security.getSLast()).as("Security %s", security.getIdConnectorIntra()).isNotNull().isGreaterThan(0.0);
    });
  }
  

  private Security createSecurityIntra(final String quoteFeedExtend, final AssetclassType assectClass,
      SpecialInvestmentInstruments specialInvestmentInstruments, String isin) {
    return this.createSecurity(quoteFeedExtend, assectClass, specialInvestmentInstruments, isin, null, true, false);

  }

  private Security createSecurity(final String quoteFeedExtend, final AssetclassType assectClass,
      SpecialInvestmentInstruments specialInvestmentInstruments, String isin, String mic, final boolean securityMarket,
      final boolean history) {
    final Security security = new Security();
    if (history) {
      security.setUrlHistoryExtend(quoteFeedExtend);
    } else {
      security.setUrlIntraExtend(quoteFeedExtend);
    }

    if (assectClass != null && specialInvestmentInstruments != null) {
      security.setAssetClass(
          new Assetclass(assectClass, "Bond/Aktien Schweiz", specialInvestmentInstruments, Language.GERMAN));
    }
    security.setIsin(isin);
    security.setStockexchange(new Stockexchange("XXXX", mic, null, null, false, securityMarket));

    return security;
  }

  @Test
  void updateCurrencyPairLastPriceTest() {
    final FinanzenCHFeedConnector finanzenCHFeedConnector = new FinanzenCHFeedConnector();
    final List<Currencypair> currencies = new ArrayList<>();
    currencies.add(createCurrencypairIntra("CHF", "EUR", "devisen/schweizer_franken-euro-kurs"));
    currencies.parallelStream().forEach(currencyPair -> {
      try {
        finanzenCHFeedConnector.updateCurrencyPairLastPrice(currencyPair);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertTrue(currencyPair.getSLast() > 0.0);
    });
  }

  

  private Currencypair createCurrencypairIntra(final String fromCurrency, String toCurrency,
      final String urlIntraExtend) {
    Currencypair currencypair = ConnectorTestHelper.createCurrencyPair("USD", "CHF");
    currencypair.setUrlIntraExtend(urlIntraExtend);
    return currencypair;
  }

}
