package grafioschtrader.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.validation.Constraint;
import javax.validation.Payload;

@Constraint(validatedBy = NonZeroFloatValidator.class)
@Target( { ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface NonZeroFloatConstraint {
    String message() default "{gt.nonZero}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
