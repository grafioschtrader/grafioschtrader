package grafioschtrader.m2m.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.test.start.GTforTest;

@SpringBootTest(classes = GTforTest.class,
webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class BaseDataClientTest {

  @Autowired
  private BaseDataClient baseDataClient;
  
  @Test
  void getSecurityByIsinAndCurrencyTest() {
  }
}
