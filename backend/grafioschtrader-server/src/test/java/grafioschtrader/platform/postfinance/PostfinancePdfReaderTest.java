package grafioschtrader.platform.postfinance;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import grafioschtrader.platform.PdfReaderTest;
import grafioschtrader.test.start.GTforTest;

@SpringBootTest(classes = GTforTest.class)
@ActiveProfiles("prod")
class PostfinancePdfReaderTest extends PdfReaderTest {

  private final static String PLATFORM_NAME = "Postfinance";

  @Override
  protected String getPlatformName() {
    return PLATFORM_NAME;
  }
}
