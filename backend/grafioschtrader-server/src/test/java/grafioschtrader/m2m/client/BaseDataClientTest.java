package grafioschtrader.m2m.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import grafioschtrader.entities.Security;
import grafioschtrader.test.start.GTforTest;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = GTforTest.class,
webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class BaseDataClientTest {

  @Autowired
  private BaseDataClient baseDataClient;
  
  @Test
  void getSecurityByIsinAndCurrencyTest() {
  }
}
