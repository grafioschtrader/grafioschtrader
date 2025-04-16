package grafioschtrader.reports.udfalluserfields;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonProcessingException;

import grafiosch.BaseConstants;
import grafiosch.common.EnumHelper;
import grafiosch.repository.UDFDataJpaRepository;
import grafiosch.types.IUDFSpecialType;
import grafiosch.udfalluserfields.UDFFieldsHelper;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;

public abstract class AllUserFieldsSecurity {

  @Autowired
  protected UDFDataJpaRepository uDFDataJpaRepository;
  
  @Autowired
  private UDFMetadataSecurityJpaRepository uDFMetadataSecurityJpaRepository;
  private static final Logger log = LoggerFactory.getLogger(AllUserFieldsSecurity.class);

  protected void putValueToJsonValue(SecuritycurrencyUDFGroup securitycurrencyUDFGroup,
      UDFMetadataSecurity udfMetaDataSecurity, int idSecurity, Object value, boolean persits) {
    try {
      if (securitycurrencyUDFGroup.idWatchlist != null) {
        String jsonValuesAsString = securitycurrencyUDFGroup.getUdfEntityValues().get(idSecurity);
        String jsonValuesMapAsString = UDFFieldsHelper.putToTarget(jsonValuesAsString, udfMetaDataSecurity, value);
        securitycurrencyUDFGroup.getUdfEntityValues().put(idSecurity, jsonValuesMapAsString);
      }
      if (persits) {
        UDFFieldsHelper.writeValueToUser0(udfMetaDataSecurity, uDFDataJpaRepository, Security.class, idSecurity, value);
      }
    } catch (JsonProcessingException e) {
      log.error("Failed to read or write JSON with field for all users", e);
    }
  }

  protected UDFMetadataSecurity getMetadataSecurity(IUDFSpecialType udfSpecialType) {
    return uDFMetadataSecurityJpaRepository.getByUdfSpecialTypeAndIdUser(udfSpecialType.getValue(),
        BaseConstants.UDF_ID_USER);
  }

  protected boolean matchAssetclassAndSpecialInvestmentInstruments(UDFMetadataSecurity udfMetadataSecurity,
      Assetclass assetclass) {
    return EnumHelper.contains(assetclass.getCategoryType(), udfMetadataSecurity.getCategoryTypes()) && EnumHelper
        .contains(assetclass.getSpecialInvestmentInstrument(), udfMetadataSecurity.getSpecialInvestmentInstruments());
  }

}
