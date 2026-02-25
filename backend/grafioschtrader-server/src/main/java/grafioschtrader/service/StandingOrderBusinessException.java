package grafioschtrader.service;

/**
 * Thrown when a standing order execution fails due to an expected business condition (no price available,
 * calculated units <= 0, etc.). Distinguished from unexpected runtime exceptions so the failure log can
 * categorize the error into {@code businessError} vs {@code unexpectedError}.
 */
public class StandingOrderBusinessException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public StandingOrderBusinessException(String message) {
    super(message);
  }
}
