package grafiosch.udfalluserfields;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import grafiosch.BaseConstants;
import grafiosch.entities.UDFData;
import grafiosch.entities.UDFData.UDFDataKey;
import grafiosch.entities.UDFMetadata;
import grafiosch.repository.UDFDataJpaRepository;
import grafiosch.types.UDFDataType;

/**
 * Helper class providing static utility methods for handling user-defined
 * fields (UDF) operations, specifically related to JSON serialization and
 * persistent storage interactions.
 * <p>
 * The methods in this class allow inserting, updating, and reading user-defined
 * values based on metadata descriptions. It manages JSON
 * serialization/deserialization and handles type-specific processing such as
 * date-time formatting.
 * <p>
 * 
 * This class should not be instantiated.
 */

public abstract class UDFFieldsHelper {

  private static ObjectMapper objectMapper = new ObjectMapper();
  private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  static {
    objectMapper.registerModule(new JavaTimeModule());
  }

  /**
   * Inserts or updates a value in a JSON-formatted string based on the provided
   * UDF metadata and returns the updated JSON string.
   * <p>
   * This method deserializes the given JSON string into a map, updates the map by
   * adding or replacing the entry with a key constructed from the UDF metadata,
   * and serializes the map back into a JSON string. If the provided value
   * corresponds to a date-time numeric UDF type, it is formatted appropriately
   * before insertion.
   *
   * @param jsonValuesAsString the original JSON string representing a map of
   *                           values; may be {@code null} or empty.
   * @param udfMetadata        metadata describing the UDF, including its ID and
   *                           data type.
   * @param value              the value to insert or update; if {@code null}, the
   *                           key will be associated with {@code null}. If the
   *                           type is date-time numeric, this should be a
   *                           {@link LocalDateTime} instance.
   * @return the updated JSON string after inserting or updating the provided
   *         value.
   * @throws JsonMappingException    if the JSON string cannot be mapped correctly
   *                                 to a map.
   * @throws JsonProcessingException if an error occurs during JSON serialization
   *                                 or deserialization.
   */

  public static String putToTarget(String jsonValuesAsString, UDFMetadata udfMetadata, Object value)
      throws JsonMappingException, JsonProcessingException {
    Map<String, Object> jsonValuesMap = null;
    if (jsonValuesAsString != null) {
      ObjectReader reader = objectMapper.readerFor(Map.class);
      jsonValuesMap = reader.readValue(jsonValuesAsString);
    } else {
      jsonValuesMap = new HashMap<>();
    }
    if (value != null && udfMetadata.getUdfDataType() == UDFDataType.UDF_DateTimeNumeric) {
      value = formatter.format((LocalDateTime) value);
    }
    jsonValuesMap.put(BaseConstants.UDF_FIELD_PREFIX + udfMetadata.getIdUDFMetadata(), value);
    return objectMapper.writeValueAsString(jsonValuesMap);
  }

  /**
   * Inserts or updates a value associated with a specific user into persistent
   * storage based on the provided UDF metadata.
   * <p>
   * Retrieves existing user-defined data (UDFData) or creates new data if it does
   * not exist, then updates the data with the given value. If the provided value
   * corresponds to a date-time numeric UDF type, it is formatted appropriately
   * before insertion.
   *
   * @param udfMetadata          metadata describing the UDF, including its ID and
   *                             data type.
   * @param uDFDataJpaRepository repository for managing UDF data persistence.
   * @param classz               the class reference identifying the context of
   *                             the UDF data.
   * @param id                   UDF_ID_USER and the name of the class and the ID
   *                             of the entity of this class are the key for UDF
   *                             data.
   * @param value                the value to store; if {@code null}, no action is
   *                             performed.
   */
  public static void writeValueToUser0(UDFMetadata udfMetadata, UDFDataJpaRepository uDFDataJpaRepository,
      Class<?> classz, int id, Object value) {
    if (value != null) {
      UDFDataKey udfDataKey = new UDFDataKey(BaseConstants.UDF_ID_USER, classz.getSimpleName(), id);
      UDFData udfData = uDFDataJpaRepository.findById(udfDataKey).orElse(new UDFData(udfDataKey, new HashMap<>()));
      switch (udfMetadata.getUdfDataType()) {
      case UDF_DateTimeNumeric:
        value = formatter.format((LocalDateTime) value);
        break;
      default:
        break;
      }
      udfData.getJsonValues().put(BaseConstants.UDF_FIELD_PREFIX + udfMetadata.getIdUDFMetadata(), value);
      uDFDataJpaRepository.save(udfData);
    }
  }

  
  /**
   * Reads a stored user-defined value from persistent storage based on provided
   * UDF metadata and user identifier.
   * <p>
   * Retrieves the stored value associated with the provided metadata. If the
   * stored value corresponds to a date-time numeric UDF type, it will be parsed
   * into a {@link LocalDateTime} object.
   *
   * @param udfMetadata          metadata describing the UDF, including its ID and
   *                             data type.
   * @param uDFDataJpaRepository repository for accessing UDF data persistence.
   * @param classz               the class reference identifying the context of
   *                             the UDF data.
   * @param id                   UDF_ID_USER and the name of the class and the ID
   *                             of the entity of this class are the key for UDF
   *                             data.
   * @return the stored value; returns {@code null} if no data exists for the
   *         specified criteria.
   */
  public static Object readValueFromUser0(UDFMetadata udfMetadata, UDFDataJpaRepository uDFDataJpaRepository,
      Class<?> classz, int id) {
    Optional<UDFData> udfDataOpt = uDFDataJpaRepository
        .findById(new UDFDataKey(BaseConstants.UDF_ID_USER, classz.getSimpleName(), id));
    Object value = null;
    if (udfDataOpt.isPresent()) {
      Map<String, Object> jsonValues = udfDataOpt.get().getJsonValues();
      if (jsonValues != null) {
        value = jsonValues.get(BaseConstants.UDF_FIELD_PREFIX + udfMetadata.getIdUDFMetadata());
        if (value != null) {
          switch (udfMetadata.getUdfDataType()) {
          case UDF_DateTimeNumeric:
            return LocalDateTime.parse((String) value);
          default:
            break;
          }
        }
      }
    }
    return value;
  }
}
