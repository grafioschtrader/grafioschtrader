package grafioschtrader.repository.dataverification;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFData;
import grafioschtrader.entities.UDFData.UDFDataKey;
import grafioschtrader.repository.UDFDataJpaRepository;
import grafioschtrader.test.start.GTforTest;

@SpringBootTest(classes = GTforTest.class)
public class UDFDataTest {

  @Autowired
  private UDFDataJpaRepository uDFDataJpaRepository;
  
  
  @Test
  @Rollback(false)
  void saveReadData() {
    Map<String, String> values = new HashMap<>();
    values.put("Überschuldet", "true");
    values.put("ShortSkala", "8");
    
    
   UDFData udfData = new UDFData(new UDFDataKey(0, Security.TABNAME, 1), values);
   uDFDataJpaRepository.save(udfData);
  }
}
