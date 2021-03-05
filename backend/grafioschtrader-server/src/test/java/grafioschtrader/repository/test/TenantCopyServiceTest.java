package grafioschtrader.repository.test;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import grafioschtrader.repository.CopyTenantService;
import grafioschtrader.test.start.GTforTest;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = GTforTest.class)
public class TenantCopyServiceTest {
  
  @Autowired
  private CopyTenantService tenantCopyService;
  
  
  @Test
  @Disabled
  void copyTest() {
    tenantCopyService.copyTenant(22, 25);
  }
}
