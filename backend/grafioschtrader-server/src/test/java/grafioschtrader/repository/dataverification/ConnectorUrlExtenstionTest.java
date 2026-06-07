package grafioschtrader.repository.dataverification;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import grafioschtrader.entities.Security;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.test.start.GTforTest;

@SpringBootTest(classes = GTforTest.class)
@ActiveProfiles("prod")
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
