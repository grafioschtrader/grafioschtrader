package grafioschtrader.repository.dataverification;


import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import grafioschtrader.entities.Security;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.test.start.GTforTest;


@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = GTforTest.class)
class ConnectorUrlExtenstionTest {

  @Autowired
  private SecurityJpaRepository securityJpaRepository;
  
  @Test
  void urlExtendsionTest() {
  
    List<Security> securities = securityJpaRepository.findAll();
    for (Security security : securities) {
      securityJpaRepository.checkAndClearSecuritycurrencyConnectors(security);
    }
  }
  
  
}
