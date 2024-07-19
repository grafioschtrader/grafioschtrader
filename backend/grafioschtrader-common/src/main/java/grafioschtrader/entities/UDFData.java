package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.GlobalConstants;
import grafioschtrader.dynamic.model.udf.UDFDataHelper;
import grafioschtrader.types.UDFDataType;
import grafioschtrader.validation.WebUrlValidator;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

/*

@SqlResultSetMapping(
    name="udfDataResult",
    classes={
        @ConstructorResult(
            targetClass=UDFEntityValues.class,
            columns={
                @ColumnResult(name="idSecurity"),
                @ColumnResult(name="jsonValues")
            }
        )
    }
)
@NamedNativeQuery(name="UDFData.getUdfDataResult", query="""
        SELECT ud.id_entity idSecurity, ud.json_values jsonValues FROM udf_data ud JOIN security s ON 
        ud.id_entity = s.id_securitycurrency JOIN watchlist_sec_cur w ON s.id_securitycurrency = w.id_securitycurrency 
        WHERE w.id_watchlist = ?1 AND ud.id_user = ?2 AND ud.entity = ?3 ORDER BY s.id_securitycurrency""", resultSetMapping="udfDataResult")
*/
@Schema(description = "The data for the user defined fields are persisted into this entity. The JSON support of MaraDB provides the necessary flexibility.")
@Entity
@Table(name = UDFData.TABNAME)
public class UDFData {

  public static final String TABNAME = "udf_data";

  @EmbeddedId
  private UDFDataKey uDFDataKey;

  @Schema(description = "Storage of a map in JSON. This gives us great flexibility. The idUDFMetadata with the prefix 'F' is used as the key.")
  @Type(JsonType.class)
  @Column(columnDefinition = "json_values")
  private Map<String, Object> jsonValues;

  public UDFData() {
  }

  public UDFData(UDFDataKey uDFDataKey, Map<String, Object> jsonValues) {
    super();
    this.uDFDataKey = uDFDataKey;
    this.jsonValues = jsonValues;
  }

  public UDFDataKey getuDFDataKey() {
    return uDFDataKey;
  }

  public void setuDFDataKey(UDFDataKey uDFDataKey) {
    this.uDFDataKey = uDFDataKey;
  }

  public Map<String, Object> getJsonValues() {
    return jsonValues;
  }

  public void setJsonValues(Map<String, Object> jsonValues) {
    this.jsonValues = jsonValues;
  }

  public void checkDataAgainstMetadata(List<UDFMetadata> udfMetadataList) {
    Map<String, UDFMetadata> udfMetadataMap = udfMetadataList.stream()
        .collect(Collectors.toMap(u -> GlobalConstants.UDF_FIELD_PREFIX + u.getIdUDFMetadata(), Function.identity()));
    for (Iterator<Map.Entry<String, Object>> it = jsonValues.entrySet().iterator(); it.hasNext();) {
      Map.Entry<String, Object> entry = it.next();
      UDFMetadata udfMetadata = udfMetadataMap.get(entry.getKey());
      if (udfMetadata == null) {
        it.remove();
      } else {
        if (udfMetadata.getUdfDataType() != UDFDataType.UDF_Boolean) {
          Object value = UDFDataHelper.isFieldSizeForDataType(udfMetadata.getUdfDataType())
              ? checkDataWithFiledSizeTypes(udfMetadata, (String) entry.getValue())
              : checkDataWithoutFieldSize(udfMetadata, (String) entry.getValue());
        }
      }
    }
  }

  private Object checkDataWithFiledSizeTypes(UDFMetadata udfMetadata, String value) {
    int[] prefixSuffix = udfMetadata.getPrefixSuffix();
    Object checkedValue = null;
    switch (udfMetadata.getUdfDataType()) {
    case UDF_Numeric:
      double max = UDFDataHelper.getMaxDecimalValue(prefixSuffix[0], prefixSuffix[1]);
      checkedValue = checkNumeric(value, max * -1, max);
      break;
    case UDF_NumericInteger:
      checkedValue = checkNumericInteger(value, prefixSuffix[0], prefixSuffix[1]);
      break;
    default:
      // UDF_String:
      checkString(value, prefixSuffix[0], prefixSuffix[1]);
      break;

    }
    return checkedValue;
  }

  private Object checkDataWithoutFieldSize(UDFMetadata udfMetadata, String value) {
    switch (udfMetadata.getUdfDataType()) {
    case UDF_DateTimeNumeric:
      return checkLocalDateTime(value);
    case UDF_DateString:
      return checkLocalDate(value);
    default:
      // UDF_URLString:
      WebUrlValidator wuv = new WebUrlValidator();
      if (!wuv.isValid(value, null)) {
        throw new IllegalArgumentException("URL starts with '" + value.substring(0, 20) + "' is not a valid url");
      }
      if (value.length() > GlobalConstants.FIELD_SIZE_MAX_G_WEB_URL) {
        throw new IllegalArgumentException("URL starts with '" + value.substring(0, 20) + "' has more than "
            + GlobalConstants.FIELD_SIZE_MAX_G_WEB_URL + " characters");
      }
      return value;
    }
  }

  private Double checkNumeric(String numericStr, double min, double max) {
    Double numeric = null;
    try {
      numeric = Double.parseDouble(numericStr);
      if (numeric < min || numeric > max) {
        throw new IllegalArgumentException("Decimal Number " + numeric + " is not a range of " + min + " - " + max);
      }
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(numericStr + " is not a decimal number");
    }
    return numeric;
  }

  private Integer checkNumericInteger(String numericStr, int min, int max) {
    Integer numeric = null;
    try {
      numeric = Integer.parseInt(numericStr);
      if (numeric < min || numeric > max) {
        throw new IllegalArgumentException("Integer " + numeric + " is not a range of " + min + " - " + max);
      }
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(numericStr + " is not a integer number");
    }
    return numeric;
  }

  private void checkString(String value, int minLength, int maxLength) {
    if (value.length() < minLength || value.length() > maxLength) {
      throw new IllegalArgumentException("String with length of " + value.length()
          + " characters is not in the range of " + minLength + " - " + maxLength + " characters");
    }
  }

  private LocalDate checkLocalDate(String value) {
    DateTimeFormatter localDateFormatter = DateTimeFormatter.ofPattern(GlobalConstants.STANDARD_DATE_FORMAT);
    try {
      return LocalDate.parse(value, localDateFormatter);
    } catch (DateTimeParseException dte) {
      throw new IllegalArgumentException(value + "  is not a valid date");
    }
  }

  private LocalDateTime checkLocalDateTime(String value) {
    DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    try {
      return LocalDateTime.parse(value, formatter);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(value + " is not a valid date with time");
    }
  }

  @Embeddable
  public static class UDFDataKey extends UserBaseID implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Possibly this construct of the UDF is also used for system-wide input fields. In this case, the user with ID = 0 would be used.")
    @Column(name = "id_user")
    @NotNull
    private Integer idUser;

    @Schema(description = "The name of the entity to which this UDF refers.")
    @Column(name = "entity")
    @NotNull
    private String entity;

    @Schema(description = "The ID of the entity which this record extends.")
    @Column(name = "id_entity")
    @NotNull
    private Integer idEntity;

    public UDFDataKey() {
    }

    public UDFDataKey(Integer idUser, String entity, Integer idEntity) {
      this.idUser = idUser;
      this.entity = entity;
      this.idEntity = idEntity;
    }

    public String getEntity() {
      return entity;
    }

    public Integer getIdEntity() {
      return idEntity;
    }

    @Override
    public int hashCode() {
      return Objects.hash(entity, idEntity, idUser);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if ((obj == null) || (getClass() != obj.getClass())) {
        return false;
      }
      UDFDataKey other = (UDFDataKey) obj;
      return Objects.equals(entity, other.entity) && Objects.equals(idEntity, other.idEntity)
          && Objects.equals(idUser, other.idUser);
    }

    @Override
    public Integer getIdUser() {
      return idUser;
    }

    @Override
    public void setIdUser(Integer idUser) {
      this.idUser = idUser;
    }

    @Override
    @JsonIgnore
    public Integer getId() {
      throw new UnsupportedOperationException("We have a composite key");
    }

  }
}
