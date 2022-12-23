package grafioschtrader.platform.migrosbank;

import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.platform.PdfReaderTest;
import grafioschtrader.test.start.GTforTest;

@SpringBootTest(classes = GTforTest.class)
public class MigrosBankPdfReaderTest extends PdfReaderTest {

  private final static String PLATFORM_NAME = "Migros Bank";

  @Override
  protected String getPlatformName() {
    return PLATFORM_NAME;
  }
}
