package grafiosch.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as required during CSV import operations.
 *
 * <p>When a field is annotated with this annotation, the CSV import process will:
 * <ul>
 *   <li>Require that the corresponding column exists in the CSV header</li>
 *   <li>Require that the value is non-empty for each data row</li>
 *   <li>Throw a {@link grafiosch.exceptions.DataViolationException} if the column is missing</li>
 * </ul>
 *
 * <p>This annotation is processed by {@link CSVImportHelper#getHeaderFieldNameMapping} during
 * the header mapping phase of CSV import.
 *
 * <p>Example usage:
 * <pre>
 * public class MyEntity {
 *   &#64;ImportDataRequired
 *   &#64;PropertyAlwaysUpdatable
 *   private String currency;  // Must be present in CSV
 *
 *   &#64;PropertyAlwaysUpdatable
 *   private String isin;      // Optional in CSV
 * }
 * </pre>
 *
 * @see CSVImportHelper
 * @see FieldColumnMapping
 */
@Target({ ElementType.FIELD })
@Retention(value = RetentionPolicy.RUNTIME)
@Inherited
public @interface ImportDataRequired {

}

