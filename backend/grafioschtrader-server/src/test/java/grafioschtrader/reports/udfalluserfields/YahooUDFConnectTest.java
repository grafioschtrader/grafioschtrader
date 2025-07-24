package grafioschtrader.reports.udfalluserfields;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.GlobalConstants;
import grafioschtrader.connector.instrument.test.ConnectorTestHelper;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.test.start.GTforTest;

@SpringBootTest(classes = GTforTest.class)
public class YahooUDFConnectTest {

  @Autowired
  private MicProviderMapRepository micProviderMapRepository;

  private YahooUDFConnect yahooUDFConnect = new YahooUDFConnect();

  @Test
  void evaluateYahooSymbol() {
    Map<String, Security> testData = new LinkedHashMap<>();
    testData.put("NESN.SW", ConnectorTestHelper.createSecurityAndStockexchange("Nestlé AG",
        ConnectorTestHelper.ISIN_Nestle, "NESN", GlobalConstants.STOCK_EX_MIC_SIX));
    testData.put("IDTL.SW", ConnectorTestHelper.createSecurityAndStockexchange("iShares 20+ Year Treasury Bond ETF",
        ConnectorTestHelper.ISIN_TLT, "IDTL", GlobalConstants.STOCK_EX_MIC_SIX));
    testData.put("ADS.DE", ConnectorTestHelper.createSecurityAndStockexchange("Adidas AG", "DE000A1EWWW0", "ADS",
        GlobalConstants.STOCK_EX_MIC_XETRA));
    testData.put("PKO.WA", ConnectorTestHelper.createSecurityAndStockexchange("PKO Bank Polski", "PLPKO0000016", "PKO",
        GlobalConstants.STOCK_EX_MIC_WARSAW));

    for (Map.Entry<String, Security> entry : testData.entrySet()) {
      String expected = entry.getKey();
      Security sec = entry.getValue();
      String actual = yahooUDFConnect.getYahooSymbolThruSymbolSearch(sec, micProviderMapRepository);
      assertEquals(expected, actual, () -> String.format("Expected Yahoo symbol for %s on %s to be %s, but was %s",
          sec.getName(), sec.getStockexchange().getMic(), expected, actual));
    }
  }

  @Test
  void extractNextEarningDateTest() {
    try {
      // CrumbManager.setCookie();

     // List<String> symbols = Arrays.asList("DOW");
     List<String> symbols = Arrays.asList("ADSK", "NVDA",  "BIDU");
      LocalDateTime now = LocalDateTime.now();

      for (String symbol : symbols) {
        LocalDateTime nextEarningDate = yahooUDFConnect.extractNextEarningDate(symbol);

        System.out.println("Symbol:" + symbol + " Next earning date:" + nextEarningDate);
        // must not be null
        assertNotNull("Next earning date should not be null for " + symbol, nextEarningDate);

        // must be today or in the future
        assertTrue("Next earning date for " + symbol + " should be today or in the future, but was " + nextEarningDate,
            !nextEarningDate.isBefore(now));
      }
    } catch (IOException e) {
      e.printStackTrace();
      fail("IOException occurred during test: " + e.getMessage());
    }
  }
}
