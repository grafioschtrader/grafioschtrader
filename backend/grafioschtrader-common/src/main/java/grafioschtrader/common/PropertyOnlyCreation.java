package grafioschtrader.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This propertey can only updated by the user when the entity is created.
 *
 * @author Hugo Graf
 *
 */
@Target({ ElementType.FIELD })
@Retention(value = RetentionPolicy.RUNTIME)
@Inherited
public @interface PropertyOnlyCreation {

}
