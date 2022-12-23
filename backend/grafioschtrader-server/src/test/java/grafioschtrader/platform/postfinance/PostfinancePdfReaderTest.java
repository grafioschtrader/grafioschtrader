package grafioschtrader.platform.postfinance;

import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.platform.PdfReaderTest;
import grafioschtrader.test.start.GTforTest;

@SpringBootTest(classes = GTforTest.class)
class PostfinancePdfReaderTest extends PdfReaderTest {

  private final static String PLATFORM_NAME = "Postfinance";

  @Override
  protected String getPlatformName() {
    return PLATFORM_NAME;
  }
}
