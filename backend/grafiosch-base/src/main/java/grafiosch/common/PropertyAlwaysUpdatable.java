package grafiosch.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that marks a field as always updatable by users in the application.
 * This annotation is used to explicitly indicate that a property can be modified
 * by end users regardless of the entity's current state or system conditions.
 * 
 * <p>In many applications, certain fields may have restricted update permissions
 * based on various factors such as:</p>
 * <ul>
 * <li>System-controlled fields that should only be modified by internal processes</li>
 * <li>State-dependent fields that can only be changed when the entity is in specific states</li>
 * </ul>
 * 
 * <p>This annotation serves as a marker to indicate that the annotated field
 * does not have such restrictions and can always be updated by users with
 * appropriate permissions to modify the entity.</p>
 * 
 * <p><strong>Processing:</strong> This annotation is typically processed by
 * security frameworks, form generators, or permission systems to determine
 * field-level update permissions. The presence of this annotation indicates
 * that no special state checks or restrictions should be applied when
 * allowing user modifications to the field.</p>
 * 
 * <p><strong>Inheritance:</strong> This annotation is inherited by subclasses,
 * meaning that if a superclass field is marked as always updatable, the same
 * permission behavior will apply when processing subclass instances.</p>
 * 
 */
@Target({ ElementType.FIELD })
@Retention(value = RetentionPolicy.RUNTIME)
@Inherited
public @interface PropertyAlwaysUpdatable {

}
