package grafioschtrader.platform.saxotrader;

import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.platform.PdfReaderTest;
import grafioschtrader.test.start.GTforTest;

@SpringBootTest(classes = GTforTest.class)
public class SaxoTraderPdfReaderTest extends PdfReaderTest {

  private final static String PLATFORM_NAME = "Saxo Trader";

  @Override
  protected String getPlatformName() {
    return PLATFORM_NAME;
  }
}
