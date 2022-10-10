package grafioschtrader.m2m.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import grafioschtrader.entities.Security;
import grafioschtrader.test.start.GTforTest;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = GTforTest.class,
webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class BaseDataClientTest {

  @Test
  void getSecurityByIsinAndCurrencyTest() {
    BaseDataClient baseDataClient = new BaseDataClient(); 
    Security security = baseDataClient.getSecurityByIsinAndCurrency("IE00B5M4WH52", "CHF");
    assertThat(security).isNotNull();
  }
}
