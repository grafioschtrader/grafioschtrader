package grafiosch.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import grafiosch.dynamic.model.DynamicFormPropertyHelps;

@Target({ ElementType.FIELD })
@Retention(value = RetentionPolicy.RUNTIME)
@Inherited
public @interface DynamicFormPropertySupport {
  DynamicFormPropertyHelps[] value() default {};
}
