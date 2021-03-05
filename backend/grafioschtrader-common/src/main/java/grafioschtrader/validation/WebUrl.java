package grafioschtrader.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.validation.Constraint;
import javax.validation.Payload;

@Target({ ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WebUrlValidator.class)
public @interface WebUrl {

  String message() default "{gt.webUrl}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
