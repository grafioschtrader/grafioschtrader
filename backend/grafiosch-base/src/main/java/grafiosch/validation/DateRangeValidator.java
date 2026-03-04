package grafiosch.validation;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DateRangeValidator implements ConstraintValidator<DateRange, Object> {

  private String startField;
  private String endField;

  @Override
  public void initialize(DateRange dateRange) {
    startField = dateRange.start();
    endField = dateRange.end();
  }

  @Override
  public boolean isValid(Object object, ConstraintValidatorContext constraintValidatorContext) {
    try {
      Object startDate = getFieldValue(object, startField);
      if (startDate instanceof LocalDateTime startLdt) {
        LocalDateTime endLocalDateTime = (LocalDateTime) getFieldValue(object, endField);
        return startLdt.isBefore(endLocalDateTime);
      } else if (startDate instanceof LocalDate startLd) {
        LocalDate endLocalDate = (LocalDate) getFieldValue(object, endField);
        return startLd.isBefore(endLocalDate);
      }
      return false;
    } catch (Throwable e) {
      // log error
      return false;
    }
  }

  private Object getFieldValue(Object object, String fieldName) throws Exception {
    Class<?> clazz = object.getClass();
    Field field = clazz.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(object);
  }

}
