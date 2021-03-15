package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.text.ParseException;
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

import grafioschtrader.connector.instrument.finanzennet.FinanzenNETFeedConnector;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.Language;
import grafioschtrader.types.SpecialInvestmentInstruments;


/**
 * Sometimes Finanzen.NET can not always satisfy every request.   
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = GTforTest.class)
class FinanzenNETFeedConnectorTest {

  @Test
  void updateSecurityLastPriceTest() {
    final List<Security> securities = new ArrayList<>();
    final FinanzenNETFeedConnector finanzenNETFeedConnector = new FinanzenNETFeedConnector();
                                        
      securities.add(createSecurityIntra("etf/xtrackers-ftse-100-short-daily-swap-etf-1c-lu0328473581",
        AssetclassType.EQUITIES, SpecialInvestmentInstruments.ETF, "FSX"));

    securities.add(createSecurityIntra("index/smi", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, "SIX"));

    securities.add(createSecurityIntra("aktien/lufthansa-aktie@stBoerse_XETRA", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, "FSX"));

    securities.add(createSecurityIntra("anleihen/a19jgw-grande-dixence-anleihe", AssetclassType.FIXED_INCOME,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, "SIX"));
    
   
    securities.add(createSecurityIntra("aktien/apple-aktie@stBoerse_NAS", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, "NASDAQ"));

    securities.add(createSecurityIntra("/etf/xtrackers-ftse-developed-europe-real-estate-etf-1c-lu0489337690",
        AssetclassType.REAL_ESTATE, SpecialInvestmentInstruments.ETF, "FSX"));

    securities.parallelStream().forEach(security -> {
      try {
        System.out.println(security);
        finanzenNETFeedConnector.updateSecurityLastPrice(security);
      } catch (IOException | ParseException e) {
        e.printStackTrace();
      }
      assertThat(security.getSLast()).as("Security %s", security.getIdConnectorIntra()).isNotNull().isGreaterThan(0.0);

    });
  }

  @Test
  void getEodSecurityHistoryTest() {
    final List<Security> securities = new ArrayList<>();
    final FinanzenNETFeedConnector finanzenNETFeedConnector = new FinanzenNETFeedConnector();
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
   // final LocalDate from = LocalDate.parse("04.01.2000", germanFormatter);
    final LocalDate from = LocalDate.parse("24.10.2020", germanFormatter);
    final LocalDate to = LocalDate.parse("04.01.2021", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    securities.add(createSecurityHistorical("index/ftse_mib/historisch", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, "MIL"));

/*    
    securities.add(createSecurityHistorical("historische-kurse/citrix_systems", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, "NASDAQ"));
  
    securities.add(createSecurityHistorical("anleihen/historisch/a1zfhq-syngenta-finance-anleihe", AssetclassType.FIXED_INCOME,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, "SIX"));

    securities.add(createSecurityHistorical("historische-kurse/unicredit", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, "MIL"));
   
    securities.add(createSecurityHistorical("historische-kurse/Daimler", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, "FSX"));

    securities.add(createSecurityHistorical("anleihen/historisch/a1zfhq-syngenta-finance-anleihe",
        AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT, "SIX"));

    securities.add(createSecurityHistorical("rohstoffe/oelpreis/historisch|oelpreis/USD", AssetclassType.COMMODITIES,
        SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, "---"));

    securities.add(createSecurityHistorical("anleihen/historisch/a19jgw-grande-dixence-anleihe",
        AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT, "SIX"));

    securities.add(createSecurityHistorical("anleihen/historisch/a18z3v-residual-debt-services-anleihe",
        AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT, "ZKB"));

    securities.add(createSecurityHistorical("etf/kurse/ishares-atx-etf-de000a0d8q23", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.ETF, "FSX"));

    securities.add(createSecurityHistorical("rohstoffe/oelpreis/historisch|oelpreis/USD|?type=Brent",
        AssetclassType.COMMODITIES, SpecialInvestmentInstruments.CFD, "---"));

    securities.add(createSecurityHistorical("historische-kurse/lufthansa", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, "FSX"));

    securities.add(createSecurityHistorical("historische-kurse/Bayer", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, "FSX"));

    securities.add(createSecurityHistorical("anleihen/historisch/a1zfhq-syngenta-finance-anleihe",
        AssetclassType.FIXED_INCOME, SpecialInvestmentInstruments.DIRECT_INVESTMENT, "SIX"));

    securities.add(createSecurityHistorical("anleihen/historisch/a19vaz-rallye-anleihe", AssetclassType.FIXED_INCOME,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, "SIX"));

    securities
        .add(createSecurityHistorical("etf/historisch/xtrackers-ftse-developed-europe-real-estate-etf-1c-lu0489337690",
            AssetclassType.EQUITIES, SpecialInvestmentInstruments.ETF, "FSX"));

    securities.add(createSecurityHistorical("index/S&P_500/Historisch", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, "NYSE"));

    securities.add(createSecurityHistorical("index/FTSE_MIB/Historisch", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, "MTA"));

    securities.add(createSecurityHistorical("etf/historisch/xtrackers-ftse-100-short-daily-swap-etf-1c-lu0328473581",
        AssetclassType.EQUITIES, SpecialInvestmentInstruments.ETF, "LSE"));
*/
    securities.parallelStream().forEach(security -> {
      List<Historyquote> historyquotes = new ArrayList<>();
      try {
        historyquotes = finanzenNETFeedConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println(historyquotes.get(0));

      assertThat(historyquotes.size()).isGreaterThan(200);
    });

  }

  private Security createSecurityIntra(final String historyExtend, final AssetclassType assectClass,
      SpecialInvestmentInstruments specialInvestmentInstruments, String stockexchangeSymbol) {
    return createSecurity(historyExtend, assectClass, specialInvestmentInstruments, stockexchangeSymbol, false);
  }

  private Security createSecurityHistorical(final String historyExtend, final AssetclassType assectClass,
      SpecialInvestmentInstruments specialInvestmentInstruments, String stockexchangeSymbol) {
    return createSecurity(historyExtend, assectClass, specialInvestmentInstruments, stockexchangeSymbol, true);
  }

  private Security createSecurity(final String urlExtend, final AssetclassType assectClass,
      SpecialInvestmentInstruments specialInvestmentInstruments, String stockexchangeSymbol, boolean historical) {
    final Security security = new Security();
    if (historical) {
      security.setUrlHistoryExtend(urlExtend);
    } else {
      security.setUrlIntraExtend(urlExtend);
    }
    security.setAssetClass(
        new Assetclass(assectClass, "Bond/Aktien Schweiz", specialInvestmentInstruments, Language.GERMAN));
    security.setStockexchange(new Stockexchange("XXXX", stockexchangeSymbol, null, null, false, true));
    return security;
  }

  @Test
  void getEodCurrencyHistoryTest() {

    final FinanzenNETFeedConnector finanzenNETFeedConnector = new FinanzenNETFeedConnector();
    final List<Currencypair> currencies = new ArrayList<>();
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("08.03.2016", germanFormatter);
    final LocalDate to = LocalDate.parse("23.10.2019", germanFormatter);
    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    currencies.add(createCurrencypair("EUR", "USD", "devisen/dollarkurs/historisch"));
    currencies.add(createCurrencypair("USD", "CAD", "devisen/us_dollar-kanadischer_dollar-kurs/historisch"));
    

    currencies.parallelStream().forEach(currencyPair -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = finanzenNETFeedConnector.getEodCurrencyHistory(currencyPair, fromDate, toDate);
      } catch (Exception e) {
        e.printStackTrace();
      }
      assertThat(historyquote.size()).isGreaterThan(1000);
    });
  }

  private Currencypair createCurrencypair(final String fromCurrency, String toCurrency, final String urlHistoryExtend) {
    Currencypair currencypair = ConnectorTestHelper.createCurrencyPair("USD", "CHF");
    currencypair.setUrlHistoryExtend(urlHistoryExtend);
    return currencypair;
  }

}
