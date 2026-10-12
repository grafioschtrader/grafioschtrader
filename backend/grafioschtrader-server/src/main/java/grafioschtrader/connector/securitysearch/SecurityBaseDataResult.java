package grafioschtrader.connector.securitysearch;

import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.types.SecuritySearchStatus;

/**
 * Result of one master data request against one provider.
 *
 * @param providerId id of the provider that was asked
 * @param status     outcome of the request; {@link SecuritySearchStatus#OK} only when data is present
 * @param data       the master data, null unless the status is OK
 * @param detail     short technical explanation of a failure, may be null
 */
public record SecurityBaseDataResult(String providerId, SecuritySearchStatus status, SecurityBaseDataDto data,
    String detail) {

  /**
   * Creates the result of a successful request.
   *
   * @param providerId id of the provider
   * @param data       the master data
   * @return the result with status OK
   */
  public static SecurityBaseDataResult ok(String providerId, SecurityBaseDataDto data) {
    return new SecurityBaseDataResult(providerId, SecuritySearchStatus.OK, data, null);
  }

  /**
   * Creates the result of a request that could not be carried out or delivered nothing usable.
   *
   * @param providerId id of the provider
   * @param status     the failure status
   * @param detail     short explanation, may be null
   * @return the result without data
   */
  public static SecurityBaseDataResult failure(String providerId, SecuritySearchStatus status, String detail) {
    return new SecurityBaseDataResult(providerId, status, null, detail);
  }
}
