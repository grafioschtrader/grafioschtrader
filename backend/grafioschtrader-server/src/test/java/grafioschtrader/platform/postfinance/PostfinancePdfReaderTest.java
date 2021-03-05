package grafioschtrader.platform.postfinance;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import grafioschtrader.platform.PdfReaderTest;
import grafioschtrader.test.start.GTforTest;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = GTforTest.class)
class PostfinancePdfReaderTest extends PdfReaderTest {

  private final static String PLATFORM_NAME = "Postfinance";

  @Override
  protected String getPlatformName() {
    return PLATFORM_NAME;
  }
}
