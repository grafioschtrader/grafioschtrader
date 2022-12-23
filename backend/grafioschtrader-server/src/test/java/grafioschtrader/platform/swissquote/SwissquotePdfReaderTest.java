package grafioschtrader.platform.swissquote;

import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.platform.PdfReaderTest;
import grafioschtrader.test.start.GTforTest;

@SpringBootTest(classes = GTforTest.class)
public class SwissquotePdfReaderTest extends PdfReaderTest {
  
  private final static String PLATFORM_NAME = "Swissquote";
  
  @Override
  protected String getPlatformName() {
    return PLATFORM_NAME;
  }
  
}
