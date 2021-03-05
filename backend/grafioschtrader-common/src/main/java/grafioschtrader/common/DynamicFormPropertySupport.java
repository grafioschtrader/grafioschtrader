package grafioschtrader.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import grafioschtrader.dynamic.model.DynamicFormPropertyHelps;

@Target({ ElementType.FIELD })
@Retention(value = RetentionPolicy.RUNTIME)
@Inherited
public @interface DynamicFormPropertySupport {
  DynamicFormPropertyHelps[] value() default {};
}
