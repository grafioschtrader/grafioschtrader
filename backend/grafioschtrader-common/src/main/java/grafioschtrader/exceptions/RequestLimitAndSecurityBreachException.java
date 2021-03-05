package grafioschtrader.exceptions;

public class RequestLimitAndSecurityBreachException extends RuntimeException {

  private static final long serialVersionUID = 1L;
  private String fieldToBeChangedInUser;

  public RequestLimitAndSecurityBreachException() {
    super();
  }

  public RequestLimitAndSecurityBreachException(String explanation, String fieldToBeChangedInUser) {
    super(explanation);
    this.fieldToBeChangedInUser = fieldToBeChangedInUser;

  }

  public String getFieldToBeChangedInUser() {
    return fieldToBeChangedInUser;
  }

}
