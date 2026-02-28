package grafiosch.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entity field as immutable once the entity (or a related entity) is in active use. Fields with this
 * annotation are compared reflectively by {@link DataHelper#areAnnotatedFieldsEqual(Object, Object, Class)} to detect
 * unauthorized modifications. Typical use case: preventing changes to connector configuration fields after instruments
 * reference the connector.
 */
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface LockedWhenUsed {
}
