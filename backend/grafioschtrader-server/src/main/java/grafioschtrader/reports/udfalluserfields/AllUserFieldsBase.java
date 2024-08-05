package grafioschtrader.reports.udfalluserfields;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.EnumHelper;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFData;
import grafioschtrader.entities.UDFData.UDFDataKey;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup;
import grafioschtrader.repository.UDFDataJpaRepository;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;
import grafioschtrader.types.UDFDataType;
import grafioschtrader.types.UDFSpecialType;

public abstract class AllUserFieldsBase {

  @Autowired
  private UDFMetadataSecurityJpaRepository uDFMetadataSecurityJpaRepository;

  @Autowired
  protected UDFDataJpaRepository uDFDataJpaRepository;

  protected static ObjectMapper objectMapper = new ObjectMapper();
  private static final Logger log = LoggerFactory.getLogger(AllUserFieldsBase.class);
  private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  static {
    objectMapper.registerModule(new JavaTimeModule());
  }

  protected void putValueToJsonValue(SecuritycurrencyUDFGroup securitycurrencyUDFGroup,
      UDFMetadataSecurity udfMetaDataSecurity, int idSecurity, Object value, boolean persits) {
    try {
      if (securitycurrencyUDFGroup.idWatchlist != null) {
        String jsonValuesAsString = securitycurrencyUDFGroup.getUdfEntityValues().get(idSecurity);
        String jsonValuesMapAsString = putToTarget(jsonValuesAsString, udfMetaDataSecurity, idSecurity, value);
        securitycurrencyUDFGroup.getUdfEntityValues().put(idSecurity, jsonValuesMapAsString);
      }
      if (persits) {
        writeValueToUser0(udfMetaDataSecurity, idSecurity, value);
      }
    } catch (JsonProcessingException e) {
      log.error("Failed to read or write JSON with field for all users", e);
    }
  }

  private String putToTarget(String jsonValuesAsString, UDFMetadataSecurity udfMetaDataSecurity, int idSecurity,
      Object value) throws JsonMappingException, JsonProcessingException {
    Map<String, Object> jsonValuesMap = null;
    if (jsonValuesAsString != null) {
      ObjectReader reader = objectMapper.readerFor(Map.class);
      jsonValuesMap = reader.readValue(jsonValuesAsString);
    } else {
      jsonValuesMap = new HashMap<>();
    }
    if (value != null && udfMetaDataSecurity.getUdfDataType() == UDFDataType.UDF_DateTimeNumeric) {
      value = formatter.format((LocalDateTime) value);
    }
    jsonValuesMap.put(GlobalConstants.UDF_FIELD_PREFIX + udfMetaDataSecurity.getIdUDFMetadata(), value);
    return objectMapper.writeValueAsString(jsonValuesMap);
  }

  protected void writeValueToUser0(UDFMetadataSecurity udfMetaDataSecurity, int idSecurity, Object value) {
    if (value != null) {
      UDFDataKey udfDataKey = new UDFDataKey(GlobalConstants.UDF_ID_USER, Security.class.getSimpleName(), idSecurity);
      UDFData udfData = uDFDataJpaRepository.findById(udfDataKey).orElse(new UDFData(udfDataKey, new HashMap<>()));
      switch (udfMetaDataSecurity.getUdfDataType()) {
      case UDF_DateTimeNumeric:
        value = formatter.format((LocalDateTime) value);
        break;
      }
      udfData.getJsonValues().put(GlobalConstants.UDF_FIELD_PREFIX + udfMetaDataSecurity.getIdUDFMetadata(), value);
      uDFDataJpaRepository.save(udfData);
    }
  }

  /**
   * User 0 may already contain the value, so return it.
   * 
   * @param securitycurrencyUDFGroup
   * @param udfMetaDataSecurity
   * @param idSecurity
   * @return
   */
  protected Object readValueFromUser0(UDFMetadataSecurity udfMetaDataSecurity, int idSecurity) {
    Optional<UDFData> udfDataOpt = uDFDataJpaRepository
        .findById(new UDFDataKey(GlobalConstants.UDF_ID_USER, Security.class.getSimpleName(), idSecurity));
    Object value = null;
    if (udfDataOpt.isPresent()) {
      Map<String, Object> jsonValues = udfDataOpt.get().getJsonValues();
      if (jsonValues != null) {
        value = jsonValues.get(GlobalConstants.UDF_FIELD_PREFIX + udfMetaDataSecurity.getIdUDFMetadata());
        if (value != null) {
          switch (udfMetaDataSecurity.getUdfDataType()) {
          case UDF_DateTimeNumeric:
            return LocalDateTime.parse((String) value);
          }
        }
      }
    }
    return value;
  }

  protected UDFMetadataSecurity getMetadataSecurity(UDFSpecialType udfSpecialType) {
    return uDFMetadataSecurityJpaRepository.getByUdfSpecialTypeAndIdUser(udfSpecialType.getValue(),
        GlobalConstants.UDF_ID_USER);
  }

  protected boolean matchAssetclassAndSpecialInvestmentInstruments(UDFMetadataSecurity udfMetadataSecurity,
      Assetclass assetclass) {
    return EnumHelper.contains(assetclass.getCategoryType(), udfMetadataSecurity.getCategoryTypes()) && EnumHelper
        .contains(assetclass.getSpecialInvestmentInstrument(), udfMetadataSecurity.getSpecialInvestmentInstruments());
  }

}
