package grafiosch.common;

import java.lang.reflect.Field;

/**
 * Represents a mapping between a CSV column index and an entity field for import operations.
 *
 * <p>This class is used during CSV import to associate each column in the CSV file with its
 * corresponding field in the target entity class. It stores:
 * <ul>
 *   <li>The column index in the CSV file (0-based)</li>
 *   <li>The target field in the entity class</li>
 *   <li>Whether the field is required (must have a value)</li>
 * </ul>
 *
 * <p>The mapping is typically created by {@link CSVImportHelper#getHeaderFieldNameMapping}
 * based on matching CSV header names to entity field names.
 *
 * @see CSVImportHelper
 * @see ImportDataRequired
 */
public class FieldColumnMapping {

  /** The 0-based column index in the CSV file */
  public final int col;

  /** The target field in the entity class */
  public final Field field;

  /** Whether this field is required (must have a non-empty value) */
  public boolean required;

  /**
   * Creates a new field-column mapping.
   *
   * @param col the 0-based column index in the CSV file
   * @param field the target field in the entity class
   */
  public FieldColumnMapping(int col, Field field) {
    this.col = col;
    this.field = field;
  }

  @Override
  public String toString() {
    return "FieldColumnMapping [col=" + col + ", field=" + field.getName() + ", required=" + required + "]";
  }
}
