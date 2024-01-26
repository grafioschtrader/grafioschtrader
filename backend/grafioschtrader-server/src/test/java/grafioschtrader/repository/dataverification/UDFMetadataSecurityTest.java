package grafioschtrader.repository.dataverification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;

import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;
import grafioschtrader.test.start.GTforTest;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.UDFDataType;

@SpringBootTest(classes = GTforTest.class)
public class UDFMetadataSecurityTest {

  @Autowired
  private UDFMetadataSecurityJpaRepository uDFMetadataSecurityJpaRepository;

  @Test
  @Rollback(false)
  void saveTest() {
    uDFMetadataSecurityJpaRepository.deleteAll();

    UDFMetadataSecurity udf =  new UDFMetadataSecurity();
    udf.setCategoryType(AssetclassType.CONVERTIBLE_BOND);
    udf.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    udf.setIdUser(1);
    udf.setDescription("Rating moodys");
    udf.setUdfDataType(UDFDataType.UDF_String);
    udf.setFieldSize("12");
    udf.setUiOrder((byte) 10);
    uDFMetadataSecurityJpaRepository.save(udf);

    UDFMetadataSecurity udf1 =  new UDFMetadataSecurity();
    udf1.setCategoryType(AssetclassType.CONVERTIBLE_BOND);
    udf1.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    udf1.setIdUser(1);
    udf1.setDescription("Wandlung");
    udf1.setDescriptionHelp("Bediengung der Wandlung");
    udf1.setUdfDataType(UDFDataType.UDF_String);
    udf1.setFieldSize("100");
    udf1.setUiOrder((byte) 20);
    uDFMetadataSecurityJpaRepository.save(udf1);

    UDFMetadataSecurity udf2 =  new UDFMetadataSecurity();
    udf2.setCategoryType(AssetclassType.EQUITIES);
    udf2.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    udf2.setIdUser(1);
    udf2.setDescription("Verlustjahre");
    udf2.setDescriptionHelp("Die Anzahl der letzten Verlustjahre");
    udf2.setUdfDataType(UDFDataType.UDF_NumericInteger);
    udf2.setFieldSize("2");
    udf2.setUiOrder((byte) 30);
    uDFMetadataSecurityJpaRepository.save(udf2);
  }
}
