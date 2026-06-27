package grafiosch.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import grafiosch.dynamic.model.DynamicFormPropertyHelps;

/**
 * Marks an entity field as a participant in a dynamically generated input form and carries the
 * metadata required to place and render it. The presence of this annotation is what selects a
 * field for form generation; the actual validation constraints (required, length, range, pattern,
 * …) still come from the standard Jakarta Bean Validation annotations on the same field and are
 * read separately by {@code DynamicModelHelper}.
 *
 * <p>This annotation lets the backend entity remain the single source of truth for an input form:
 * the frontend no longer hard-codes max length, required flags or numeric ranges that the entity
 * already declares.</p>
 *
 * <h3>Dialog membership and ordering ({@link #uiOrder()})</h3>
 * <p>{@code uiOrder} is a comma separated list of {@code dialogId.position} tokens. A single token
 * such as {@code "1.3"} places the field at position 3 of dialog 1. A field that appears in several
 * dialogs lists several tokens, e.g. {@code "1.4,2.1"} = position 4 in dialog 1 and position 1 in
 * dialog 2. Entities with only one form simply use {@code "1.x"}. The serving endpoint filters by
 * the requested dialog id and sorts by position.</p>
 *
 * <h3>Label ({@link #labelKey()})</h3>
 * <p>By default the frontend derives the translation key from the field name (HeqF convention,
 * e.g. {@code borrowingRate} → {@code BORROWING_RATE}). Set {@code labelKey} only when an explicit
 * key is required because the derived one would be wrong or conflict (e.g. {@code "CASHACCOUNT_NAME"}
 * for an inherited {@code name} field).</p>
 *
 * <p><strong>Inheritance:</strong> inherited by subclasses, so a form field declared on a base
 * entity is picked up when generating the form for a subclass.</p>
 */
@Target({ ElementType.FIELD })
@Retention(value = RetentionPolicy.RUNTIME)
@Inherited
public @interface DynamicFormField {

  /**
   * Comma separated list of {@code dialogId.position} tokens controlling in which dialog(s) the
   * field appears and at which position. Example: {@code "1.3"} or {@code "1.4,2.1"}.
   */
  String uiOrder();

  /**
   * Optional UI hints that influence how the input is rendered (email, password, select options,
   * percentage). Mirrors {@link DynamicFormPropertySupport#value()}.
   */
  DynamicFormPropertyHelps[] helps() default {};

  /**
   * Optional explicit translation key for the field label. When empty the frontend derives the key
   * from the field name (HeqF convention).
   */
  String labelKey() default "";

  /**
   * Maximum number of integer digits for a numeric input. Use this for floating point fields
   * ({@code Double}/{@code Float}) where {@code @Digits} cannot be used because Hibernate would try to
   * apply a column scale, which is invalid for SQL floating point types. A value of 0 means unset.
   */
  int integerLimit() default 0;

  /**
   * Maximum number of fraction digits for a numeric input. See {@link #integerLimit()}. A value of 0
   * means unset.
   */
  int fractionLimit() default 0;
}
