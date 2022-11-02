package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.finanzen.FinanzenHelper;
import grafioschtrader.connector.instrument.finanzench.FinanzenCHFeedConnector;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.Language;
import grafioschtrader.types.SpecialInvestmentInstruments;

@ExtendWith(SpringExtension.class)
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

  @Test
  void getEodSecurityHistoryTest() {
    final List<Security> securities = new ArrayList<>();

    final FinanzenCHFeedConnector finanzenCHFeedConnector = new FinanzenCHFeedConnector();

    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("01.03.2020", germanFormatter);
    final LocalDate to = LocalDate.parse("19.05.2021", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    securities.add(createSecurity("derivate/historisch/ch0033333326/qmh", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT, "CH0033333326", GlobalConstants.STOCK_EX_MIC_SIX, true,
        true));
    securities.add(createSecurity("obligationen/historisch/impleniasf-anl_201424-obligation-2024-ch0253592767/SWX",
        AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT, "CH0253592767",
        GlobalConstants.STOCK_EX_MIC_SIX, true, true));
    securities.add(createSecurity("index/historisch/FTSE_MIB", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, "IT0003465736", GlobalConstants.STOCK_EX_MIC_ITALY, true,
        true));
    securities.add(
        createSecurity("etf/historisch/ishares-european-property-yield-etf-ie00b0m63284/fse", AssetclassType.EQUITIES,
            SpecialInvestmentInstruments.ETF, "IE00B0M63284", GlobalConstants.STOCK_EX_MIC_XETRA, true, true));
    securities.add(createSecurity("kurse/historisch/ubs/swx", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, "CH0244767585", GlobalConstants.STOCK_EX_MIC_SIX, true, true));

    securities.parallelStream().forEach(security -> {
      List<Historyquote> historyquotes = new ArrayList<>();
      try {
        historyquotes = finanzenCHFeedConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(historyquotes.size()).isGreaterThan(220);
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

  @Test
  void getEodCurrencyHistoryTest() {

    final FinanzenCHFeedConnector finanzenCHFeedConnector = new FinanzenCHFeedConnector();
    final List<Currencypair> currencies = new ArrayList<>();
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("08.03.2016", germanFormatter);
    final LocalDate to = LocalDate.parse("22.04.2021", germanFormatter);
    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    currencies.add(createCurrencypairHistory("EUR", "NOK", "devisen/historisch/euro-norwegische_krone-kurs"));
    currencies.add(createCurrencypairHistory("ETH", "CHF", "devisen/historisch/ethereum-franken-kurs"));
    currencies.add(createCurrencypairHistory("EUR", "USD", "devisen/historisch/euro-us_dollar-kurs"));

    currencies.parallelStream().forEach(currencyPair -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = finanzenCHFeedConnector.getEodCurrencyHistory(currencyPair, fromDate, toDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
      System.out.println("Size:" + historyquote.size());
      assertThat(historyquote.size()).isGreaterThan(1000);

    });
  }

  private Currencypair createCurrencypairHistory(final String fromCurrency, String toCurrency,
      final String urlHistoryExtend) {
    Currencypair currencypair = ConnectorTestHelper.createCurrencyPair("USD", "CHF");
    currencypair.setUrlHistoryExtend(urlHistoryExtend);
    return currencypair;
  }

  private Currencypair createCurrencypairIntra(final String fromCurrency, String toCurrency,
      final String urlIntraExtend) {
    Currencypair currencypair = ConnectorTestHelper.createCurrencyPair("USD", "CHF");
    currencypair.setUrlIntraExtend(urlIntraExtend);
    return currencypair;
  }

}
