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

  @Id
  @Basic(optional = false)
  @NotNull
  @Size(min = 1, max = 45)
  @Column(name = "property_name")
  private String propertyName;

  @Column(name = "property_int")
  private Integer propertyInt;

  @Size(max = 25)
  @Column(name = "property_string")
  private String propertyString;

  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "property_date")
  private LocalDate propertyDate;

  @Lob
  @Column(name = "property_blob")
  private byte[] propertyBlob;

  @Schema(description = "This property will be changed by the system")
  @Column(name = "changed_by_system")
  private boolean changedBySystem = false;

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

  @JsonIgnore
  public byte[] getPropertyBlob() {
    return propertyBlob;
  }

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

  public static String getKeyFromMsgKey(String msgKey) {
    return msgKey.toLowerCase().replaceAll("_", ".");
  }

  public static MaxDefaultDBValueWithKey getMaxDefaultDBValueByMsgKey(final String msgKey) {
    return getMaxDefaultDBValueByKey(getKeyFromMsgKey(msgKey));
  }

  public static MaxDefaultDBValueWithKey getMaxDefaultDBValueByKey(final String key) {
    return new MaxDefaultDBValueWithKey(key, defaultLimitMap.get(key));
  }

  public static void resetDBValueOfKey(final String key) {
    MaxDefaultDBValue mddv = defaultLimitMap.get(key);
    if (mddv != null) {
      mddv.setDbValue(null);
    }
  }

  public void replaceExistingPropertyValue(Globalparameters gpNew) {
    if (gpNew.getPropertyDate() != null && propertyDate != null) {
      this.propertyDate = gpNew.getPropertyDate();
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

  public String checkBlobPropertyBeforeSave(IPropertiesSelfCheck targetObj) throws IOException {
    propertyBlob = propertyBlobAsText.getBytes(StandardCharsets.UTF_8);
    transformBlobPropertiesIntoClass(targetObj);
    return targetObj.checkForValid();
  }

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

  public void transformClassIntoBlobPropertis(Object soruceObj) throws Exception {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      Properties properties = transformClassIntoPropertis(soruceObj);
      properties.store(baos, null);
      this.propertyBlob = baos.toByteArray();
    }
  }

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
