package grafioschtrader.priceupdate.historyquote;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.common.UserAccessHelper;
import grafioschtrader.dto.HisotryqouteLinearFilledSummary;
import grafioschtrader.dto.IDateAndClose;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.User;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.types.HistoryquoteCreateType;

@Service
public class HistoryquoteQualityService {

  @Autowired
  SecurityJpaRepository securityJpaRepository;

  @Autowired
  HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private MessageSource messages;

  @Transactional
  public HisotryqouteLinearFilledSummary fillHistoryquoteGapsLinear(final Integer idSecuritycurrency,
      boolean moveWeekendToFriday) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final Double[] firstLastPrice = new Double[2];
    final List<LocalDate> missingDays = new ArrayList<>();
    final List<Historyquote> missingHistoryquoteList = new ArrayList<>();
    HisotryqouteLinearFilledSummary hisotryqouteLinearFilledSummary = new HisotryqouteLinearFilledSummary();

    Security security = securityJpaRepository.getReferenceById(idSecuritycurrency);
    if ((UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, security)
        && security.getActiveToDate().before(new Date()) && security.getIdTenantPrivate() == null)
        || UserAccessHelper.isAdmin(user)) {
      if (moveWeekendToFriday) {
        moveWeekendDayToBusinessDay(idSecuritycurrency, hisotryqouteLinearFilledSummary);
      }

      List<IDateAndClose> dateAndClose = historyquoteJpaRepository
          .getClosedAndMissingHistoryquoteByIdSecurity(idSecuritycurrency);
      dateAndClose.forEach(dac -> {
        hisotryqouteLinearFilledSummary.requiredClosing++;
        if (dac.getClose() == null) {
          missingDays.add(dac.getDate());
        } else if (missingDays.isEmpty()) {
          firstLastPrice[0] = dac.getClose();
        } else {
          // Get close price but missing days is not empty
          firstLastPrice[1] = dac.getClose();
          addHistoryquotesLiniear(idSecuritycurrency, firstLastPrice, missingDays, missingHistoryquoteList,
              hisotryqouteLinearFilledSummary);
        }
      });

    } else {
      throw new SecurityException(GlobalConstants.STEAL_DATA_SECURITY_BREACH);
    }

    this.hisotryqouteLinearFill(user, idSecuritycurrency, firstLastPrice, missingDays, hisotryqouteLinearFilledSummary,
        missingHistoryquoteList);

    return hisotryqouteLinearFilledSummary;
  }

  private void hisotryqouteLinearFill(final User user, final Integer idSecuritycurrency, final Double[] firstLastPrice,
      final List<LocalDate> missingDays, HisotryqouteLinearFilledSummary hisotryqouteLinearFilledSummary,
      final List<Historyquote> missingHistoryquoteList) {
    if (!missingDays.isEmpty()) {
      if (firstLastPrice[0] != null || firstLastPrice[1] != null) {
        addHistoryquotesLiniear(idSecuritycurrency, firstLastPrice, missingDays, missingHistoryquoteList,
            hisotryqouteLinearFilledSummary);
      } else {
        // Not a single day with a close price was found
        hisotryqouteLinearFilledSummary.message = messages.getMessage("gt.not.single.valid.close", null,
            user.createAndGetJavaLocale());
        hisotryqouteLinearFilledSummary.warning = true;
      }
    }
    if (!hisotryqouteLinearFilledSummary.warning) {
      hisotryqouteLinearFilledSummary.message = messages.getMessage("gt.success", null, user.createAndGetJavaLocale());
    }
    historyquoteJpaRepository.saveAll(missingHistoryquoteList);
    hisotryqouteLinearFilledSummary.createdHistoryquotes = missingHistoryquoteList.size();

  }

  private void moveWeekendDayToBusinessDay(final Integer idSecuritycurrency,
      HisotryqouteLinearFilledSummary hisotryqouteLinearFilledSummary) {
    List<Historyquote> historyquotes = historyquoteJpaRepository.findByIdFridayAndWeekend(idSecuritycurrency);
    List<Historyquote> historyquotesForDelete = new ArrayList<>();
    List<Historyquote> historyquotesForSave = new ArrayList<>();
    for (int i = 0; i < historyquotes.size() - 1; i++) {
      Historyquote historyquote = historyquotes.get(i);
      LocalDate localDate = ((java.sql.Date) historyquote.getDate()).toLocalDate();
      if (localDate.getDayOfWeek() == DayOfWeek.SUNDAY || localDate.getDayOfWeek() == DayOfWeek.SATURDAY) {
        LocalDate dayBefore = localDate.minusDays(1);
        LocalDate historyqouteDayBefore = ((java.sql.Date) historyquotes.get(i + 1).getDate()).toLocalDate();
        if (dayBefore.isEqual(historyqouteDayBefore)) {
          // Remove it -> exists other history quote
          historyquotesForDelete.add(historyquote);
        } else {
          historyquote.setDate(Date.from(dayBefore.atStartOfDay(ZoneId.systemDefault()).toInstant()));
          if (localDate.getDayOfWeek() == DayOfWeek.SATURDAY) {
            // Modify Saturday history quote to Friday
            historyquotesForSave.add(historyquote);
          } else {
            i++;
          }
        }
      }
    }
    hisotryqouteLinearFilledSummary.removedWeekendDays = historyquotesForDelete.size();
    historyquoteJpaRepository.deleteAll(historyquotesForDelete);
    hisotryqouteLinearFilledSummary.movedWeekendDays = historyquotesForSave.size();
    historyquoteJpaRepository.saveAll(historyquotesForSave);
  }

  private void addHistoryquotesLiniear(final Integer idSecuritycurrency, final Double[] firstLastPrice,
      final List<LocalDate> missingDays, final List<Historyquote> missingHistoryquoteList,
      HisotryqouteLinearFilledSummary hisotryqouteLinearFilledSummary) {
    Double slope = null;
    double startPrice = 0;
    if (firstLastPrice[0] != null && firstLastPrice[1] != null) {
      // Price before and after gap is available
      slope = (firstLastPrice[1] - firstLastPrice[0]) / (missingDays.size() + 1);
      startPrice = firstLastPrice[0];
    } else if (firstLastPrice[0] != null && firstLastPrice[1] == null
        || firstLastPrice[0] == null && firstLastPrice[1] != null) {
      // Price only before -> Gap on the beginning of history quotes or Price only
      // after -> Gap on the end of history
      // quotes
      slope = 0.0;
      if (firstLastPrice[0] != null) {
        startPrice = firstLastPrice[0];
        hisotryqouteLinearFilledSummary.createdHistoryquotesEnd = missingDays.size();
      } else {
        startPrice = firstLastPrice[1];
        hisotryqouteLinearFilledSummary.createdHistoryquotesStart = missingDays.size();
      }

    }
    if (slope != null) {
      for (int i = 0; i < missingDays.size(); i++) {
        missingHistoryquoteList
            .add(new Historyquote(idSecuritycurrency, HistoryquoteCreateType.FILLED_CLOSED_LINEAR_TRADING_DAY,
                DateHelper.getDateFromLocalDate(missingDays.get(i)), startPrice + (i + 1) * slope));
      }
      hisotryqouteLinearFilledSummary.gapsTotalFilled++;
      missingDays.clear();
      firstLastPrice[0] = firstLastPrice[1];
      firstLastPrice[1] = null;

    }
  }
}
