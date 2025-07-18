package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.common.DateHelper;
import grafiosch.common.UserAccessHelper;
import grafiosch.entities.ProposeChangeEntity;
import grafiosch.entities.ProposeChangeField;
import grafiosch.entities.User;
import grafiosch.repository.ProposeChangeEntityJpaRepository;
import grafiosch.repository.ProposeChangeFieldJpaRepository;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.dto.HistoryquotePeriodDeleteAndCreateMultiple;
import grafioschtrader.entities.HistoryquotePeriod;
import grafioschtrader.entities.Security;
import grafioschtrader.types.HistoryquotePeriodCreateType;

/**
 * Implementation of custom repository methods for {@link HistoryquotePeriod} entities. This class handles logic for
 * adjusting history quote periods based on security activity and for managing user-driven changes to these periods,
 * including proposal mechanisms for users without direct edit rights.
 */
public class HistoryquotePeriodJpaRepositoryImpl implements HistoryquotePeriodJpaRepositoryCustom {

  @Autowired
  private HistoryquotePeriodJpaRepository historyquotePeriodJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private ProposeChangeEntityJpaRepository proposeChangeEntityJpaRepository;

  @Autowired
  private ProposeChangeFieldJpaRepository proposeChangeFieldJpaRepository;

  @Override
  public void adjustHistoryquotePeriod(Security security) {
    List<HistoryquotePeriod> historyquotePeriods = historyquotePeriodJpaRepository
        .findByIdSecuritycurrencyOrderByFromDate(security.getIdSecuritycurrency());
    if (historyquotePeriods.isEmpty()) {
      historyquotePeriodJpaRepository.save(getSystemCreatedPeriod(security));
    } else if (historyquotePeriods.size() == 1) {
      // Adjust period for active period
      HistoryquotePeriod hp = historyquotePeriods.get(0);
      hp.setFromDate(DateHelper.getLocalDate(security.getActiveFromDate()));
      hp.setToDate(DateHelper.getLocalDate(security.getActiveToDate()));
      historyquotePeriodJpaRepository.save(hp);
    } else {
      // There is more than one period
      HistoryquotePeriod hpFirst = historyquotePeriods.get(0);
      if (hpFirst.getFromDate().isAfter(DateHelper.getLocalDate(security.getActiveFromDate()))) {
        hpFirst.setFromDate(DateHelper.getLocalDate(security.getActiveFromDate()));
        historyquotePeriodJpaRepository.save(hpFirst);
      }
      HistoryquotePeriod hpLast = historyquotePeriods.get(historyquotePeriods.size() - 1);
      if (hpLast.getToDate().isBefore(DateHelper.getLocalDate(security.getActiveToDate()))) {
        hpLast.setToDate(DateHelper.getLocalDate(security.getActiveToDate()));
        historyquotePeriodJpaRepository.save(hpLast);
      }
    }

  }

  @Override
  @Transactional
  @Modifying
  public List<HistoryquotePeriod> deleteAndCreateMultiple(User user, HistoryquotePeriodDeleteAndCreateMultiple hpdacm) {

    Security security = securityJpaRepository.findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(
        hpdacm.idSecuritycurrency, user.getIdTenant());
    if (security != null) {
      final BiPredicate<HistoryquotePeriod, HistoryquotePeriod> hpCompare = (hp1,
          hp2) -> hp1.getFromDate().equals(hp2.getFromDate()) && hp1.getPrice() == hp2.getPrice();

      List<HistoryquotePeriod> historyquotePeriodsExisting = historyquotePeriodJpaRepository
          .findByIdSecuritycurrencyAndCreateTypeOrderByFromDate(hpdacm.idSecuritycurrency,
              HistoryquotePeriodCreateType.USER_CREATED.getValue());

      Arrays.sort(hpdacm.getHistoryquotePeriods(), (hp1, hp2) -> hp1.getFromDate().compareTo(hp2.getFromDate()));

      LocalDate activeToDate = ((java.sql.Date) security.getActiveToDate()).toLocalDate();
      List<HistoryquotePeriod> hpsNewSorted = Arrays.stream(hpdacm.getHistoryquotePeriods())
          .filter(hp -> !hp.getFromDate().isAfter(activeToDate)).collect(Collectors.toList());
      if (!DataBusinessHelper.compareCollectionsSorted(hpsNewSorted, historyquotePeriodsExisting, hpCompare)) {
        // Historyquote periods has changed
        return changeHistoryquotePeriods(user, security, hpsNewSorted, hpdacm.getNoteRequest());
      }
    }

    return null;
  }

  /**
   * Creates a system-generated {@link HistoryquotePeriod} for a security, covering its entire active date range
   * and using its denomination as the price.
   *
   * @param security The {@link Security} for which to create the period.
   * @return A new {@link HistoryquotePeriod} instance.
   */
  private HistoryquotePeriod getSystemCreatedPeriod(Security security) {
    if (security.getActiveFromDate() instanceof java.sql.Date) {
      return new HistoryquotePeriod(security.getIdSecuritycurrency(),
          ((java.sql.Date) security.getActiveFromDate()).toLocalDate(),
          ((java.sql.Date) security.getActiveToDate()).toLocalDate(), security.getDenomination());
    } else {
      return new HistoryquotePeriod(security.getIdSecuritycurrency(),
          DateHelper.getLocalDate(security.getActiveFromDate()), DateHelper.getLocalDate(security.getActiveToDate()),
          security.getDenomination());
    }
  }

  /**
   * Handles the logic for changing history quote periods. If the user has rights, it modifies them directly. Otherwise,
   * it creates a change proposal.
   * <p>
   * When modifying directly:
   * <ul>
   * <li>Deletes all existing history quote periods for the security.</li>
   * <li>If the new list of periods is empty, a single system-generated period is created.</li>
   * <li>Otherwise, the new periods are processed: `toDate` of each period is adjusted to abut the `fromDate` of the
   * next, and the overall coverage is aligned with the security's active dates, potentially adding system-generated
   * periods at the start or adjusting the end date of the final period.</li>
   * <li>All new/adjusted periods are saved, and the security's last price might be updated based on these periods.</li>
   * </ul>
   * </p>
   *
   * @param user         The user initiating the change.
   * @param security     The {@link Security} whose periods are to be changed.
   * @param hpsNewSorted The new list of user-defined history quote periods, sorted by their from_date.
   * @param noteRequest  A note from the user explaining the reason for the change, used if a proposal is created.
   * @return The list of newly saved or proposed {@link HistoryquotePeriod} objects.
   */
  private List<HistoryquotePeriod> changeHistoryquotePeriods(User user, Security security,
      List<HistoryquotePeriod> hpsNewSorted, String noteRequest) {
    if (UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, security)) {
      historyquotePeriodJpaRepository.deleteByIdSecuritycurrency(security.getIdSecuritycurrency());
      if (hpsNewSorted.isEmpty()) {
        hpsNewSorted.add(getSystemCreatedPeriod(security));
      } else {
        HistoryquotePeriod prevHp = null;
        for (HistoryquotePeriod hp : hpsNewSorted) {
          hp.setIdSecuritycurrency(security.getIdSecuritycurrency());
          hp.setIdHistoryquotePeriod(null);
          hp.setCreateType(HistoryquotePeriodCreateType.USER_CREATED);
          if (prevHp != null) {
            prevHp.setToDate(hp.getFromDate().minusDays(1));
          }
          prevHp = hp;
        }
        LocalDate activeFromDate = ((java.sql.Date) security.getActiveFromDate()).toLocalDate();
        LocalDate activeToDate = ((java.sql.Date) security.getActiveToDate()).toLocalDate();

        if (activeFromDate.isBefore(hpsNewSorted.get(0).getFromDate())) {
          HistoryquotePeriod hp = getSystemCreatedPeriod(security);
          hp.setToDate(hpsNewSorted.get(0).getFromDate().minusDays(1));
          hpsNewSorted.add(0, hp);
        }
        HistoryquotePeriod hpLast = hpsNewSorted.get(hpsNewSorted.size() - 1);
        hpLast.setToDate(activeToDate);
        hpsNewSorted = historyquotePeriodJpaRepository.saveAll(hpsNewSorted);
        historyquotePeriodJpaRepository.updatLastPriceFromHistoricalPeriod();
      }
    } else {
      // User can't change history quote periods directly if another user created the
      // security -> create a proposal change
      final ProposeChangeEntity proposeChangeEntityNew = proposeChangeEntityJpaRepository.save(new ProposeChangeEntity(
          security.getClass().getSimpleName(), security.getId(), security.getCreatedBy(), noteRequest));
      proposeChangeFieldJpaRepository.save(new ProposeChangeField(Security.HISTORYQUOTE_PERIOD_ARRAY,
          SerializationUtils.serialize(hpsNewSorted.toArray(new HistoryquotePeriod[hpsNewSorted.size()])),
          proposeChangeEntityNew.getIdProposeRequest()));

    }
    return hpsNewSorted;
  }

}
