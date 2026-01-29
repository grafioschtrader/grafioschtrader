package grafiosch.common;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.reflect.FieldUtils;

import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;

/**
 * Utility class providing common CSV import functionality for mapping CSV headers to entity fields
 * and parsing CSV data lines into entity objects.
 *
 * <p>This class supports flexible header-to-field mapping using annotations to determine which
 * entity fields are importable. It handles:
 * <ul>
 *   <li>Dynamic header mapping based on {@link PropertyAlwaysUpdatable} and {@link PropertyOnlyCreation} annotations</li>
 *   <li>Required field validation using annotation</li>
 *   <li>Value conversion for different data types (dates, numbers, strings)</li>
 *   <li>Uppercase conversion for string fields when configured</li>
 * </ul>
 *
 * <p>Example CSV format:
 * <pre>
 * isin;tickerSymbol;currency
 * US0378331005;AAPL;USD
 * DE0007164600;SAP;EUR
 * </pre>
  */
public class CSVImportHelper {

  /** Default CSV field separator (semicolon) */
  public static final String CSV_FIELD_SEPARATOR = ";";

  /**
   * Creates a mapping between CSV column headers and entity fields based on annotations.
   *
   * <p>This method inspects the target entity class for fields annotated with
   * {@link PropertyAlwaysUpdatable} or {@link PropertyOnlyCreation}, then matches them against
   * the CSV header columns. Fields annotated with {@link ImportDataRequired} are marked as required
   * in the mapping.
   *
   * <p>Header matching is case-sensitive and matches the end of field names to allow for flexible
   * naming conventions (e.g., "ticker_symbol" matches field "tickerSymbol" if the field name ends
   * with the header value after trimming).
   *
   * @param headerLine the CSV header line containing column names separated by {@link #CSV_FIELD_SEPARATOR}
   * @param entityClass the target entity class to map columns to
   * @param user the current user (used for locale in error messages)
   * @return list of {@link FieldColumnMapping} objects representing the column-to-field mappings
   * @throws DataViolationException if a required field (annotated with {@link ImportDataRequired})
   *         is not found in the CSV headers
   */
  public static List<FieldColumnMapping> getHeaderFieldNameMapping(String headerLine, Class<?> entityClass,
      final User user) {
    return getHeaderFieldNameMapping(headerLine, entityClass, user,
        Set.of(PropertyAlwaysUpdatable.class, PropertyOnlyCreation.class));
  }

  /**
   * Creates a mapping between CSV column headers and entity fields based on custom annotations.
   *
   * <p>This overload allows specifying custom annotation classes to determine which fields are
   * importable, providing flexibility for different import scenarios.
   *
   * @param headerLine the CSV header line containing column names separated by {@link #CSV_FIELD_SEPARATOR}
   * @param entityClass the target entity class to map columns to
   * @param user the current user (used for locale in error messages)
   * @param importableAnnotations set of annotation classes that mark fields as importable
   * @return list of {@link FieldColumnMapping} objects representing the column-to-field mappings
   * @throws DataViolationException if a required field (annotated with {@link ImportDataRequired})
   *         is not found in the CSV headers
   */
  public static List<FieldColumnMapping> getHeaderFieldNameMapping(String headerLine, Class<?> entityClass,
      final User user, Set<Class<? extends Annotation>> importableAnnotations) {
    List<Field> fields = DataHelper.getFieldByPropertiesAnnotation(entityClass, importableAnnotations);
    final String[] columnHeader = headerLine.split(CSV_FIELD_SEPARATOR);
    List<FieldColumnMapping> fieldColumnMappings = new ArrayList<>();

    for (int i = 0; i < columnHeader.length; i++) {
      final int colIndex = i;
      final String headerName = columnHeader[i].trim().toLowerCase();

      // Match header to field name (case-insensitive, supports snake_case to camelCase)
      fields.stream()
          .filter(field -> field.getName().toLowerCase().equals(headerName)
              || field.getName().toLowerCase().endsWith(headerName.replace("_", "")))
          .findFirst()
          .ifPresent(field -> fieldColumnMappings.add(new FieldColumnMapping(colIndex, field)));
    }

    // Check that all required fields are present in the mapping
    final List<Field> fieldsRequired = FieldUtils.getFieldsListWithAnnotation(entityClass, ImportDataRequired.class);
    for (Field fieldRequired : fieldsRequired) {
      Optional<FieldColumnMapping> fcmOpt = fieldColumnMappings.stream()
          .filter(fcm -> fcm.field.getName().equals(fieldRequired.getName()))
          .findFirst();
      if (fcmOpt.isEmpty()) {
        throw new DataViolationException(fieldRequired.getName(), "import.field.missing", fieldRequired.getName(),
            user.getLocaleStr());
      } else {
        fcmOpt.get().required = true;
      }
    }

    return fieldColumnMappings;
  }

  /**
   * Parses a CSV data line and populates an entity object using the provided field mappings.
   *
   * <p>This method handles value conversion for different data types using the provided
   * {@link ValueFormatConverter}. Empty values are skipped unless the field is marked as required.
   * String values can optionally be converted to uppercase.
   *
   * @param <T> the entity type
   * @param line the CSV data line to parse
   * @param lineNumber the line number in the CSV file (used for error messages)
   * @param entity the entity instance to populate
   * @param fieldColumnMappings the column-to-field mappings from {@link #getHeaderFieldNameMapping}
   * @param valueFormatConverter converter for handling date and number formats (can be null for simple string fields)
   * @param uppercaseStrings if true, string values are converted to uppercase
   * @param user the current user (used for locale in error messages)
   * @throws DataViolationException if a value cannot be converted to the target field type
   */
  public static <T> void parseLineToEntity(String line, int lineNumber, T entity,
      List<FieldColumnMapping> fieldColumnMappings, ValueFormatConverter valueFormatConverter,
      boolean uppercaseStrings, final User user) {
    final String[] data = line.split(CSV_FIELD_SEPARATOR, -1);

    for (FieldColumnMapping fieldColumnMapping : fieldColumnMappings) {
      if (fieldColumnMapping.col >= data.length) {
        continue;
      }

      try {
        String value = data[fieldColumnMapping.col].trim();

        if (value.isEmpty() && !fieldColumnMapping.required) {
          continue;
        }

        // Remove surrounding quotes if present
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
          value = value.substring(1, value.length() - 1);
        }

        // Uppercase string values if configured
        if (uppercaseStrings && fieldColumnMapping.field.getType() == String.class && !value.isEmpty()) {
          value = value.toUpperCase();
        }

        if (valueFormatConverter != null) {
          valueFormatConverter.convertAndSetValue(entity, fieldColumnMapping.field.getName(), value,
              fieldColumnMapping.field.getType(), true);
        } else {
          // Simple string assignment for basic cases
          fieldColumnMapping.field.setAccessible(true);
          fieldColumnMapping.field.set(entity, value.isEmpty() ? null : value);
        }
      } catch (Exception e) {
        throw new DataViolationException(fieldColumnMapping.field.getName(), "import.filed.format", lineNumber,
            user.getLocaleStr());
      }
    }
  }

  /**
   * Splits a CSV line using the standard separator.
   *
   * @param line the CSV line to split
   * @return array of field values
   */
  public static String[] splitCSVLine(String line) {
    return line.split(CSV_FIELD_SEPARATOR, -1);
  }
}
