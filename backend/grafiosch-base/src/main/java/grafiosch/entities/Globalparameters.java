/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafiosch.entities;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.stream.Collectors;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.BaseConstants;
import grafiosch.dto.IPropertiesSelfCheck;
import grafiosch.dto.MaxDefaultDBValue;
import grafiosch.dto.MaxDefaultDBValueWithKey;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Global parameters entity for storing application-wide configuration settings.
 * 
 * <p>
 * This entity provides a flexible key-value store for various types of configuration data including integers, strings,
 * dates, and binary objects. It supports complex configuration through blob properties that can be transformed to/from
 * Java objects using reflection-based serialization.
 * </p>
 * 
 * <h3>Supported Data Types:</h3>
 * <ul>
 * <li><strong>Integer:</strong> Numeric configuration values</li>
 * <li><strong>String:</strong> Text-based settings up to 25 characters</li>
 * <li><strong>Date:</strong> Date-based configuration parameters</li>
 * <li><strong>Blob:</strong> Complex objects serialized as Properties</li>
 * </ul>
 */
@Schema(description = "Contains a global setting configuration")
@Entity
@Table(name = Globalparameters.TABNAME)
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Globalparameters implements Serializable {

  public static final String TABNAME = "globalparameters";

  private static final Logger log = LoggerFactory.getLogger(Globalparameters.class);

  public static final Map<String, MaxDefaultDBValue> defaultLimitMap = new HashMap<>();

  private static final long serialVersionUID = 1L;

  @Schema(description = "Integer value for numeric configuration settings")
  @Id
  @Basic(optional = false)
  @NotNull
  @Size(min = 1, max = 45)
  @Column(name = "property_name")
  private String propertyName;

  @Schema(description = "Integer value for numeric configuration settings")
  @Column(name = "property_int")
  private Integer propertyInt;

  @Schema(description = "String value for text-based configuration settings")
  @Size(max = 25)
  @Column(name = "property_string")
  private String propertyString;

  @Schema(description = "Date value for date-based configuration settings")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "property_date")
  private LocalDate propertyDate;

  @Schema(description = "DateTime value for time-precise configuration settings")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT)
  @Column(name = "property_date_time")
  private LocalDateTime propertyDateTime;

  @Schema(description = "Binary data for complex configuration objects")
  @Lob
  @Column(name = "property_blob")
  private byte[] propertyBlob;

  @Schema(description = "This property will be changed by the system")
  @Column(name = "changed_by_system")
  private boolean changedBySystem = false;

  @Schema(description = """
      Validation rules for property values in DSL format. Supported rules:
      min:N (minimum value), max:N (maximum value), enum:N1,N2,N3 (allowed values),
      pattern:REGEX (regex pattern for strings). Example: "min:1,max:99" or "enum:1,7,12,365".""")
  @Size(max = 100)
  @Column(name = "input_rule")
  private String inputRule;

  @Transient
  private String propertyBlobAsText;

  public Globalparameters() {
  }

  public Globalparameters(String propertyName) {
    this.propertyName = propertyName;
  }

  public Globalparameters(String propertyName, LocalDate propertyDate, boolean changedBySystem) {
    this.propertyName = propertyName;
    this.propertyDate = propertyDate;
    this.changedBySystem = changedBySystem;
  }

  public String getPropertyName() {
    return propertyName;
  }

  public Globalparameters setPropertyName(String propertyName) {
    this.propertyName = propertyName;
    return this;
  }

  public Integer getPropertyInt() {
    return propertyInt;
  }

  public Globalparameters setPropertyInt(Integer propertyInt) {
    this.propertyInt = propertyInt;
    return this;
  }

  public String getPropertyString() {
    return propertyString;
  }

  public Globalparameters setPropertyString(String propertyString) {
    this.propertyString = propertyString;
    return this;
  }

  public LocalDate getPropertyDate() {
    return propertyDate;
  }

  public Globalparameters setPropertyDate(LocalDate propertyDate) {
    this.propertyDate = propertyDate;
    return this;
  }

  public LocalDateTime getPropertyDateTime() {
    return propertyDateTime;
  }

  public Globalparameters setPropertyDateTime(LocalDateTime propertyDateTime) {
    this.propertyDateTime = propertyDateTime;
    return this;
  }

  @JsonIgnore
  public byte[] getPropertyBlob() {
    return propertyBlob;
  }

  /**
   * Gets the blob property as UTF-8 text for properties ending with blob suffix.
   * 
   * @return text representation of blob data, null if not a text blob
   */
  public String getPropertyBlobAsText() {
    return propertyBlob != null && propertyName.endsWith(BaseConstants.BLOB_PROPERTIES)
        ? new String(propertyBlob, StandardCharsets.UTF_8)
        : null;
  }

  public void setPropertyBlobAsText(String propertyBlobAsText) {
    this.propertyBlobAsText = propertyBlobAsText;
  }

  public Globalparameters setPropertyBlob(byte[] propertyBlob) {
    this.propertyBlob = propertyBlob;
    return this;
  }

  public boolean isChangedBySystem() {
    return changedBySystem;
  }

  public void setChangedBySystem(boolean changedBySystem) {
    this.changedBySystem = changedBySystem;
  }

  public String getInputRule() {
    return inputRule;
  }

  public void setInputRule(String inputRule) {
    this.inputRule = inputRule;
  }

  /**
   * Converts message key format to property key format.
   * 
   * @param msgKey message key with underscores
   * @return property key with dots in lowercase
   */
  public static String getKeyFromMsgKey(String msgKey) {
    return msgKey.toLowerCase().replaceAll("_", ".");
  }

  /**
   * Gets maximum default database value by message key.
   * 
   * @param msgKey message key format
   * @return maximum default value with key wrapper
   */
  public static MaxDefaultDBValueWithKey getMaxDefaultDBValueByMsgKey(final String msgKey) {
    return getMaxDefaultDBValueByKey(getKeyFromMsgKey(msgKey));
  }

  /**
   * Gets maximum default database value by property key.
   * 
   * @param key property key format
   * @return maximum default value with key wrapper
   */
  public static MaxDefaultDBValueWithKey getMaxDefaultDBValueByKey(final String key) {
    return new MaxDefaultDBValueWithKey(key, defaultLimitMap.get(key));
  }

  /**
   * Resets the database value for a specific configuration key.
   * 
   * @param key the configuration key to reset
   */
  public static void resetDBValueOfKey(final String key) {
    MaxDefaultDBValue mddv = defaultLimitMap.get(key);
    if (mddv != null) {
      mddv.setDbValue(null);
    }
  }

  /**
   * Replaces the existing property value with a new value of the same type.
   * 
   * @param gpNew new global parameter with updated value
   * @throws IllegalArgumentException if property types don't match
   */
  public void replaceExistingPropertyValue(Globalparameters gpNew) {
    if (gpNew.getPropertyDate() != null && propertyDate != null) {
      this.propertyDate = gpNew.getPropertyDate();
    } else if (gpNew.getPropertyDateTime() != null && propertyDateTime != null) {
      this.propertyDateTime = gpNew.getPropertyDateTime();
    } else if (gpNew.getPropertyInt() != null && propertyInt != null) {
      this.propertyInt = gpNew.getPropertyInt();
    } else if (gpNew.getPropertyString() != null && propertyString != null) {
      this.propertyString = gpNew.getPropertyString();
    } else if (gpNew.getPropertyBlob() != null && propertyBlob != null) {
      setPropertyBlob(gpNew.getPropertyBlob());
    } else {
      throw new IllegalArgumentException();
    }
  }

  /**
   * Validates blob property by converting text to binary and checking object validity.
   * 
   * @param targetObj object implementing self-check validation
   * @return validation error message or null if valid
   * @throws IOException if blob conversion fails
   */
  public String checkBlobPropertyBeforeSave(IPropertiesSelfCheck targetObj) throws IOException {
    propertyBlob = propertyBlobAsText.getBytes(StandardCharsets.UTF_8);
    transformBlobPropertiesIntoClass(targetObj);
    return targetObj.checkForValid();
  }

  /**
   * Transforms blob properties into a Java object using reflection.
   * 
   * @param targetObj target object to populate with property values
   * @return populated target object
   */
  public Object transformBlobPropertiesIntoClass(Object targetObj) {
    Properties properties = new Properties();
    try (ByteArrayInputStream bais = new ByteArrayInputStream(propertyBlob)) {
      properties.load(bais);
      transformBlobPropertiesIntoClass(properties, targetObj);
    } catch (Exception e) {
      log.error("failed transform properties into class", e);
    }
    return targetObj;
  }

  /**
   * Transforms blob properties into a Java object using reflection.
   * 
   * @param targetObj target object to populate with property values
   * @return populated target object
   */
  private Object transformBlobPropertiesIntoClass(Properties properties, Object targetObj) {
    List<Field> allFields = Arrays.asList(targetObj.getClass().getDeclaredFields());
    List<Field> mapFields = allFields.stream().filter(f -> Map.class.isAssignableFrom(f.getType()))
        .collect(Collectors.toList());

    for (Entry<Object, Object> property : properties.entrySet()) {
      try {
        setPropertyToClass(targetObj, allFields, mapFields, (String) property.getKey(), (String) property.getValue());
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        log.error("failed transform blob into class", e);
      }
    }
    return targetObj;
  }

  /**
   * Sets a property value on target object using reflection with type conversion.
   * 
   * @param targetObj target object
   * @param allFields all fields of target class
   * @param mapFields map fields of target class
   * @param propertyName property name to set
   * @param propertyValue property value as string
   */
  private void setPropertyToClass(Object targetObj, List<Field> allFields, List<Field> mapFields, String propertyName,
      String propertyValue) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

    Field field = allFields.stream().filter(f -> f.getName().equals(propertyName)).findAny().orElse(null);
    if (field != null) {
      Class<?> fieldType = field.getType();
      if (fieldType == String.class) {
        field.set(targetObj, propertyValue);
      } else if (fieldType == int.class || fieldType == Integer.class) {
        field.set(targetObj, Integer.parseInt(propertyValue));
      } else if (fieldType == double.class || fieldType == Double.class) {
        field.set(targetObj, Double.parseDouble(propertyValue));
      } else if (fieldType == boolean.class || fieldType == Boolean.class) {
        field.set(targetObj, Boolean.parseBoolean(propertyValue));
      }
    } else {
      // Set value to string map
      if (mapFields.size() == 1) {
        @SuppressWarnings("unchecked")
        Map<String, String> map = (Map<String, String>) mapFields.get(0).get(targetObj);
        map.put(propertyName, propertyValue);
      }
    }
  }

  /**
   * Transforms Java object into blob properties using reflection.
   * 
   * @param soruceObj source object to serialize
   * @throws Exception if transformation fails
   */
  public void transformClassIntoBlobPropertis(Object soruceObj) throws Exception {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      Properties properties = transformClassIntoPropertis(soruceObj);
      properties.store(baos, null);
      this.propertyBlob = baos.toByteArray();
    }
  }

  /**
   * Transforms Java object into Properties using reflection.
   * 
   * @param soruceObj source object to convert
   * @return Properties object with field values
   */
  private Properties transformClassIntoPropertis(Object soruceObj)
      throws IllegalArgumentException, IllegalAccessException {
    Field[] fields = soruceObj.getClass().getDeclaredFields();
    var properties = new Properties();
    for (Field field : fields) {
      if (Map.class.isAssignableFrom(field.getType())) {
        ((Map<?, ?>) field.get(soruceObj)).forEach((fieldName, value) -> properties.put(fieldName, value));
      } else {
        properties.put(field.getName(), "" + field.get(soruceObj));
      }
    }
    return properties;
  }

  @Override
  public int hashCode() {
    int hash = 0;
    hash += (propertyName != null ? propertyName.hashCode() : 0);
    return hash;
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof Globalparameters)) {
      return false;
    }
    Globalparameters other = (Globalparameters) object;
    if ((this.propertyName == null && other.propertyName != null)
        || (this.propertyName != null && !this.propertyName.equals(other.propertyName))) {
      return false;
    }
    return true;
  }

}
