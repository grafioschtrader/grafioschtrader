package grafioschtrader.repository.dataverification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.UDFMetadataGeneral;
import grafioschtrader.repository.UDFMetadataGeneralJpaRepository;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.UDFDataType;

@SpringBootTest(classes = GTforTest.class)
public class UDFMetadataGeneralTest {

  @Autowired
  private UDFMetadataGeneralJpaRepository uDFMetadataGeneralJpaRepository;
  
  @Test
  @Rollback(false)
  void saveTest() {
    UDFMetadataGeneral udf =  new UDFMetadataGeneral();
    udf.setEntity(Assetclass.TABNAME);
    udf.setIdUser(1);
    udf.setDescription("2023 Nicht nutzen");
    udf.setUdfDataType(UDFDataType.UDF_String);
    udf.setFieldSize("10");
    udf.setUiOrder((byte) 10);
    uDFMetadataGeneralJpaRepository.save(udf);
  }
}
