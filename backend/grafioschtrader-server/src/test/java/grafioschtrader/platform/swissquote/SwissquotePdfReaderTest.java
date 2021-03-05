package grafioschtrader.platform.swissquote;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import grafioschtrader.platform.PdfReaderTest;
import grafioschtrader.test.start.GTforTest;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = GTforTest.class)
public class SwissquotePdfReaderTest extends PdfReaderTest {
  
  private final static String PLATFORM_NAME = "Swissquote";
  
  @Override
  protected String getPlatformName() {
    return PLATFORM_NAME;
  }
  
}
