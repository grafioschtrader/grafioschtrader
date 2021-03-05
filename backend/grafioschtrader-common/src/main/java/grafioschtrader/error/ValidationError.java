package grafioschtrader.error;

import java.util.ArrayList;
import java.util.List;

public class ValidationError {

  private final List<FieldError> fieldErrors = new ArrayList<>();

  public ValidationError() {
  }

  public void addFieldError(final String path, final String message) {
    final FieldError error = new FieldError(path, message);
    fieldErrors.add(error);
  }

  public List<FieldError> getFieldErrors() {
    return fieldErrors;
  }
}
