package grafioschtrader.connector.securitysearch;

import grafioschtrader.types.SecuritySearchStatus;

/**
 * Signals inside a provider adapter that the search cannot continue, together with the status to report.
 * {@link BaseSecuritySearchProvider#search(SecuritySearchQuery)} converts it into a failure result; it never leaves the
 * provider.
 */
public class SearchFailureException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final SecuritySearchStatus status;

  /**
   * @param status the status reported for the search
   * @param detail short technical explanation; must not contain an API key
   */
  public SearchFailureException(SecuritySearchStatus status, String detail) {
    super(detail);
    this.status = status;
  }

  public SecuritySearchStatus getStatus() {
    return status;
  }
}
