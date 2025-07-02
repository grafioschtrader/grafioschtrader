package grafiosch.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import grafiosch.dynamic.model.DynamicFormPropertyHelps;

/**
 * Annotation used to specify additional form property helpers for dynamic form generation.
 * This annotation allows developers to apply specialized UI behaviors and validation hints
 * to model class fields that will be processed during dynamic form creation.
 * 
 * <p>When applied to a field, this annotation provides metadata that influences how
 * the field is rendered and behaves in the generated form interface. The annotation
 * accepts an array of {@link DynamicFormPropertyHelps} values that define specific
 * input behaviors such as percentage formatting, password masking, email validation,
 * select options, or future date constraints.</p>
 *
 * <p>This annotation is processed by {@code DynamicModelHelper} during form metadata
 * generation and the specified property helpers are applied to the resulting
 * {@code FieldDescriptorInputAndShow} instances.</p>
 * 
 * <p><strong>Inheritance:</strong> This annotation is inherited by subclasses, meaning
 * that if a superclass field has this annotation, the same property helpers will be
 * applied when processing subclass instances.</p>
 */
@Target({ ElementType.FIELD })
@Retention(value = RetentionPolicy.RUNTIME)
@Inherited
public @interface DynamicFormPropertySupport {
  DynamicFormPropertyHelps[] value() default {};
}
