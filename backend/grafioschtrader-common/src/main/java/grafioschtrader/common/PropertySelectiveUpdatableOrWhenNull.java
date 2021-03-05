package grafioschtrader.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * User can update this property. Some properties can only changed by the system
 * and other properties depend on the state of its entity. Normally it is used
 * when some other data does depends on not changing this property.
 *
 * @author Hugo Graf
 *
 */
@Target({ ElementType.FIELD })
@Retention(value = RetentionPolicy.RUNTIME)
@Inherited
public @interface PropertySelectiveUpdatableOrWhenNull {

}
