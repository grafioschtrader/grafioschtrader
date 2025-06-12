package grafioschtrader.repository;

import java.util.List;

import grafiosch.entities.User;
import grafioschtrader.dto.HistoryquotePeriodDeleteAndCreateMultiple;
import grafioschtrader.entities.HistoryquotePeriod;
import grafioschtrader.entities.Security;

public interface HistoryquotePeriodJpaRepositoryCustom {

  /**
   * Adjusts the history quote periods of a given security to align with its active date range.
   * <ul>
   * <li>If no periods exist, a default system-generated period covering the security's active_from_date to
   * active_to_date is created using the security's denomination.</li>
   * <li>If a single period exists, its from_date and to_date are updated to match the security's active_from_date and
   * active_to_date.</li>
   * <li>If multiple periods exist, the from_date of the earliest period is adjusted if it's after the security's
   * active_from_date, and the to_date of the latest period is adjusted if it's before the security's
   * active_to_date.</li>
   * </ul>
   * This ensures that historical price data coverage corresponds to the security's activity window.
   *
   * @param security The {@link Security} entity for which history quote periods need to be adjusted.
   */
  void adjustHistoryquotePeriod(Security security);

 
  /**
   * Deletes existing user-created history quote periods for a security and creates new ones based on the provided list.
   * If the user has direct editing rights for the security, changes are applied immediately. Otherwise, a change
   * proposal is created for review.
   * <p>
   * The method filters out any new periods with a from_date after the security's active_to_date. If no actual changes
   * are detected between the existing and new periods, no action is taken and null is returned.
   * </p>
   *
   * @param user   The {@link User} initiating the change.
   * @param hpdacm A {@link HistoryquotePeriodDeleteAndCreateMultiple} DTO containing the ID of the security, the new
   *               list of {@link HistoryquotePeriod}s, and a note for the request.
   * @return A list of the newly created/proposed {@link HistoryquotePeriod}s if changes were made, or {@code null} if
   *         the security is not found, not accessible, or if no effective changes were requested.
   * @throws SecurityException If the security is not accessible by the user's tenant.
   */
  List<HistoryquotePeriod> deleteAndCreateMultiple(User user, HistoryquotePeriodDeleteAndCreateMultiple hpdacm);
}
