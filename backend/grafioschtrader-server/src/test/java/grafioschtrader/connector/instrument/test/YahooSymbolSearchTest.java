package grafioschtrader.connector.instrument.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.connector.yahoo.YahooSymbolSearch;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.test.start.GTforTest;

@SpringBootTest(classes = GTforTest.class)
public class YahooSymbolSearchTest {

  @Autowired
  private MicProviderMapRepository micProviderMapRepository;

  @Test
  public void testGetSymbolByISINOrSymbolOrName_Securities() {
    YahooSymbolSearch yahooSymbolSearch = new YahooSymbolSearch();

    // Define test cases with the security data.
    List<SymbolSearchTestCase> testCases = Arrays.asList(
        new SymbolSearchTestCase("XSWX", "CH0002075403", "NOVN", "Novartis", "NOVN.SW"),
        new SymbolSearchTestCase("XNAS", "US68389X1054", "ORCL", "Oracle", "ORCL"));

    // Iterate over each test case.
    for (SymbolSearchTestCase testCase : testCases) {
      String result = yahooSymbolSearch.getSymbolByISINOrSymbolOrName(micProviderMapRepository, testCase.mic,
          testCase.isin, testCase.symbol, testCase.name);
      assertEquals(testCase.expectedTicker, result, 
          "For " + testCase.name + " expected ticker " + testCase.expectedTicker + " but got " + result);
    }
  }

  /**
   * Simple container for security test parameters.
   */
  private static class SymbolSearchTestCase {
    String mic;
    String isin;
    String symbol;
    String name;
    String expectedTicker;

    SymbolSearchTestCase(String mic, String isin, String symbol, String name, String expectedTicker) {
      this.mic = mic;
      this.isin = isin;
      this.symbol = symbol;
      this.name = name;
      this.expectedTicker = expectedTicker;
    }
  }
}
