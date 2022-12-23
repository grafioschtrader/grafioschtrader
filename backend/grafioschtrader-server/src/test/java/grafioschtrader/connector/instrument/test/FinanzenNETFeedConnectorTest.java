package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.finanzennet.FinanzenNETFeedConnector;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.Language;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Sometimes Finanzen.NET can not always satisfy every request.
 */
@SpringBootTest(classes = GTforTest.class)
class FinanzenNETFeedConnectorTest {

  @Test
  void updateSecurityLastPriceTest() {
    final List<Security> securities = new ArrayList<>();
    final FinanzenNETFeedConnector finanzenNETFeedConnector = new FinanzenNETFeedConnector();

    securities.add(createSecurityIntra("rohstoffe/goldpreis/chf",
        AssetclassType.COMMODITIES, SpecialInvestmentInstruments.DIRECT_INVESTMENT, null, ""));

    securities.add(createSecurityIntra("rohstoffe/oelpreis",
        AssetclassType.COMMODITIES, SpecialInvestmentInstruments.CFD, null, ""));
  
    securities.add(createSecurityIntra("fonds/uniimmo-europa-de0009805515",
        AssetclassType.REAL_ESTATE, SpecialInvestmentInstruments.MUTUAL_FUND, null, GlobalConstants.STOCK_EX_MIC_XETRA));
  
    securities.add(createSecurityIntra("etf/xtrackers-ftse-100-short-daily-swap-etf-1c-lu0328473581",
        AssetclassType.EQUITIES, SpecialInvestmentInstruments.ETF, null, GlobalConstants.STOCK_EX_MIC_XETRA));

    securities.add(createSecurityIntra("index/smi", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.NON_INVESTABLE_INDICES, null, GlobalConstants.STOCK_EX_MIC_SIX));
        
     securities.add(createSecurityIntra("aktien/lufthansa-aktie@stBoerse_XETRA", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, null, GlobalConstants.STOCK_EX_MIC_XETRA));
    
    securities.add(createSecurityIntra("anleihen/a19jgw-grande-dixence-anleihe", AssetclassType.FIXED_INCOME,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, null, GlobalConstants.STOCK_EX_MIC_SIX));

    securities.add(createSecurityIntra("aktien/apple-aktie@stBoerse_NAS", AssetclassType.EQUITIES,
        SpecialInvestmentInstruments.DIRECT_INVESTMENT, null, GlobalConstants.STOCK_EX_MIC_NASDAQ));

    securities.add(createSecurityIntra("/etf/xtrackers-ftse-developed-europe-real-estate-etf-1c-lu0489337690",
        AssetclassType.REAL_ESTATE, SpecialInvestmentInstruments.ETF, null, GlobalConstants.STOCK_EX_MIC_XETRA));

    securities.parallelStream().forEach(security -> {
      try {
        finanzenNETFeedConnector.updateSecurityLastPrice(security);
      } catch (IOException | ParseException e) {
        e.printStackTrace();
      }
      System.out.println(security);
      assertThat(security.getSLast()).as("Security %s", security.getIdConnectorIntra()).isNotNull().isGreaterThan(0.0);

    });
  }

 

  private Security createSecurityIntra(final String historyExtend, final AssetclassType assectClass,
      SpecialInvestmentInstruments specialInvestmentInstruments, String isin, String stockexchangeSymbol) {
    return createSecurity(historyExtend, assectClass, specialInvestmentInstruments, isin, stockexchangeSymbol, false);
  }


  private Security createSecurity(final String urlExtend, final AssetclassType assectClass,
      SpecialInvestmentInstruments specialInvestmentInstruments, String isin, String mic,
      boolean historical) {
    final Security security = new Security();
    if (historical) {
      security.setUrlHistoryExtend(urlExtend);
    } else {
      security.setUrlIntraExtend(urlExtend);
    }
    security.setIsin(isin);
    security.setAssetClass(
        new Assetclass(assectClass, "Bond/Aktien Schweiz", specialInvestmentInstruments, Language.GERMAN));
    security.setStockexchange(new Stockexchange("XXXX", mic, null, null, false, true));
    return security;
  }

  

}
