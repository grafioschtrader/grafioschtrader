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

import grafioschtrader.common.DataHelper;
import grafioschtrader.common.DateHelper;
import grafioschtrader.common.UserAccessHelper;
import grafioschtrader.dto.HistoryquotePeriodDeleteAndCreateMultiple;
import grafioschtrader.entities.HistoryquotePeriod;
import grafioschtrader.entities.ProposeChangeEntity;
import grafioschtrader.entities.ProposeChangeField;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.User;
import grafioschtrader.types.HistoryquotePeriodCreateType;

public class HistoryquotePeriodJpaRepositoryImpl implements HistoryquotePeriodJpaRepositoryCustom {

  @Autowired
  HistoryquotePeriodJpaRepository historyquotePeriodJpaRepository;

  @Autowired
  SecurityJpaRepository securityJpaRepository;

  @Autowired
  ProposeChangeEntityJpaRepository proposeChangeEntityJpaRepository;

  @Autowired
  ProposeChangeFieldJpaRepository proposeChangeFieldJpaRepository;

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
      if (!DataHelper.compareCollectionsSorted(hpsNewSorted, historyquotePeriodsExisting, hpCompare)) {
        // Historyquote periods has changed
        return changeHistoryquotePeriods(user, security, hpsNewSorted, hpdacm.getNoteRequest());
      }
    }

    return null;
  }

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
        historyquotePeriodJpaRepository.updatLastPrice();
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
