package grafioschtrader.validation;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AfterEqualVaildator implements ConstraintValidator<AfterEqual, Date> {
  private AfterEqual annotation;

  @Override
  public void initialize(AfterEqual constraintAnnotation) {
    this.annotation = constraintAnnotation;
  }

  @Override
  public boolean isValid(Date value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    SimpleDateFormat sdf = new SimpleDateFormat(annotation.format());
    try {
      Date afterEqualDate = sdf.parse(annotation.value());
      return !value.before(afterEqualDate);
    } catch (ParseException e) {
      return false;
    }
  }
}
