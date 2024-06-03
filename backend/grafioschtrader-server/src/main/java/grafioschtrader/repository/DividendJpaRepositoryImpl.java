package grafioschtrader.repository;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.calendar.IDividendCalendarFeedConnector;
import grafioschtrader.connector.calendar.IDividendCalendarFeedConnector.CalendarDividends;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Globalparameters;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.types.CreateType;

public class DividendJpaRepositoryImpl implements DividendJpaRepositoryCustom {

  @Autowired
  private DividendJpaRepository dividendJpaRepository;

  @Autowired(required = false)
  private List<IFeedConnector> feedConnectors = new ArrayList<>();

  @Autowired(required = false)
  private List<IDividendCalendarFeedConnector> dividendCalendarFeedConnectors = new ArrayList<>();

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Override
  public void appendThruDividendCalendar() {
    Optional<Globalparameters> gpLastAppend = globalparametersJpaRepository
        .findById(Globalparameters.GLOB_KEY_YOUNGEST_DIVIDEND_APPEND_DATE);
    gpLastAppend.ifPresentOrElse(gp -> loadDividendData(gp.getPropertyDate().plusDays(1)),
        () -> loadDividendData(LocalDate.now()));
  }

  private void loadDividendData(LocalDate fromDate) {
    LocalDate now = LocalDate.now();
    List<TradingDaysPlus> tradingDaysPlusList = tradingDaysPlusJpaRepository
        .findByTradingDateBetweenOrderByTradingDate(fromDate, now);
    dividendCalendarFeedConnectors.sort(Comparator.comparingInt(IDividendCalendarFeedConnector::getPriority));
    stepThruEveryCalendarDay(tradingDaysPlusList);
  }

  private void stepThruEveryCalendarDay(List<TradingDaysPlus> tradingDaysPlusList) {
    for (TradingDaysPlus tradingDaysPlus : tradingDaysPlusList) {
      for (IDividendCalendarFeedConnector calendarFeedConnector : dividendCalendarFeedConnectors) {
        try {
          List<CalendarDividends> cd = calendarFeedConnector.getExDateDividend(tradingDaysPlus.getTradingDate());
          if (calendarFeedConnector.supportISIN()) {
            addDividendsByISIN(cd);
          }
        } catch (Exception ex) {
          log.error(ex.getMessage(), ex);
        }
      }
    }
  }

  private void addDividendsByISIN(List<CalendarDividends> calendarDividends) {
    Set<String> isinSet = calendarDividends.stream().map(c -> c.isin).collect(Collectors.toSet());
    Map<String, Security> securtiesMap = securityJpaRepository.findAllByIsinIn(isinSet).stream()
        .collect(Collectors.toMap(Security::getIsin, Function.identity()));
    if (!securtiesMap.isEmpty()) {
      Map<String, List<CalendarDividends>> cdMap = calendarDividends.stream()
          .collect(Collectors.groupingBy(cd -> cd.isin));
      for (Map.Entry<String, List<CalendarDividends>> entry : cdMap.entrySet()) {
        if (securtiesMap.containsKey(entry.getKey())) {
          Security security = securtiesMap.get(entry.getKey());
          loadAllDividendDataFromConnector(security, entry.getValue().stream()
              .map(c -> c.getDivident(security.getIdSecuritycurrency())).collect(Collectors.toList()));
        }
      }
    }

  }

  @Override
  public List<String> periodicallyUpdate() {
    List<String> errorMessages = new ArrayList<>();
    List<Integer> securityIds = dividendJpaRepository.getIdSecurityForPeriodicallyUpdate(
        GlobalConstants.DIVIDEND_FREQUENCY_PLUS_DAY, globalparametersJpaRepository.getMaxDividendRetry());
    List<Security> securities = securityJpaRepository.findAllById(securityIds);
    Map<Integer, List<Dividend>> idSecurityDividendsMap = dividendJpaRepository
        .findByIdSecuritycurrencyInAndCreateTypeOrderByIdSecuritycurrencyAscExDateAsc(securityIds,
            CreateType.ADD_MODIFIED_USER.getValue())
        .stream().collect(Collectors.groupingBy(Dividend::getIdSecuritycurrency, Collectors.toList()));
    List<Integer> idsSplitYoungerAsDividendList = dividendJpaRepository
        .getIdSecuritySplitAfterDividendWhenAdjusted(getDividendAdjustedConnectorsId(), securityIds);

    for (Security security : securities) {
      List<String> errorMessagesSecurity = loadAllDividendDataFromConnectorAndUpdate(security,
          idSecurityDividendsMap.getOrDefault(security.getIdSecuritycurrency(), new ArrayList<>()),
          idsSplitYoungerAsDividendList.contains(security.getIdSecuritycurrency()), new ArrayList<>());
      if (!errorMessagesSecurity.isEmpty()) {
        errorMessages.add("Name: " + security.getName() + " ISIN:" + security.getIsin());
        errorMessages.addAll(errorMessagesSecurity);
      }
    }
    return errorMessages;
  }

  private List<String> getDividendAdjustedConnectorsId() {
    return feedConnectors.stream().filter(IFeedConnector::isDividendSplitAdjusted).map(IFeedConnector::getID)
        .collect(Collectors.toList());
  }

  @Override
  public List<String> loadAllDividendDataFromConnector(Security security) {
    return loadAllDividendDataFromConnector(security, Collections.emptyList());
  }


  private List<String> loadAllDividendDataFromConnector(Security security, List<Dividend> youngestDividends) {
    List<Dividend> userCreatedDividends = dividendJpaRepository.findByIdSecuritycurrencyAndCreateTypeOrderByExDateAsc(
        security.getIdSecuritycurrency(), CreateType.ADD_MODIFIED_USER.getValue());
    List<String> errorMessages = loadAllDividendDataFromConnectorAndUpdate(security, userCreatedDividends, true,
        youngestDividends);
    return errorMessages;
  }

  /**
   * Dividends are only ever added using this method. Existing dividends in the
   * persistence are deleted and then newly created.
   *
   * @param security                  The security concerned
   * @param userCreatedDividends      Dividends created by the user
   * @param replaceAlways
   * @param youngestCalendarDividends
   * @return
   */
  private List<String> loadAllDividendDataFromConnectorAndUpdate(Security security, List<Dividend> userCreatedDividends,
      boolean replaceAlways, List<Dividend> youngestCalendarDividends) {
    List<String> errorMessages = new ArrayList<>();
    short retryDividendLoad = security.getRetryDividendLoad();
    try {
      IFeedConnector connector = ConnectorHelper.getConnectorByConnectorId(feedConnectors,
          security.getIdConnectorDividend(), IFeedConnector.FeedSupport.FS_DIVIDEND);
      List<Dividend> dividendsConnectorRead = connector.getDividendHistory(security,
          LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY));
      retryDividendLoad = 0;
      combineConnectorReadsWithCalendar(dividendsConnectorRead, youngestCalendarDividends);
      security.setDividendEarliestNextCheck(
          DateHelper.setTimeToZeroAndAddDay(new Date(), GlobalConstants.DIVIDEND_FROM_NOW_FOR_NEXT_CHECK_IN_DAYS));
      if (!replaceAlways && dividendsConnectorRead.size() == userCreatedDividends.size()
          || dividendsConnectorRead.isEmpty() || (!userCreatedDividends.isEmpty()
              && dividendsConnectorRead.getLast().getExDate().equals(userCreatedDividends.getLast().getExDate()))) {
        securityJpaRepository.save(security);
        return errorMessages;
      }
      splitAdjustDividends(security.getIdSecuritycurrency(), dividendsConnectorRead,
          connector.isDividendSplitAdjusted());
      updateDividendData(security, dividendsConnectorRead, userCreatedDividends);
    } catch (ParseException pe) {
      log.error(pe.getMessage() + "Offset: " + pe.getErrorOffset(), pe);
      errorMessages.add(pe.getMessage());
    } catch (final Exception ex) {
      retryDividendLoad++;
      log.error(ex.getMessage() + " " + security, ex);
      errorMessages.add(ExceptionUtils.getStackTrace(ex));
    }
    security.setRetryDividendLoad(retryDividendLoad);
    securityJpaRepository.save(security);
    return errorMessages;
  }

  private List<Dividend> combineConnectorReadsWithCalendar(List<Dividend> dividendsConnectorRead,
      List<Dividend> youngestCalendarDividends) {
    if (!youngestCalendarDividends.isEmpty()) {
      Optional<Dividend> maxReadDividendOpt = dividendsConnectorRead.stream()
          .max(Comparator.comparing(Dividend::getExDate));
      if (maxReadDividendOpt.isEmpty()) {
        return youngestCalendarDividends;
      } else if (maxReadDividendOpt.get().getExDate().isBefore(youngestCalendarDividends.getFirst().getExDate())) {
        dividendsConnectorRead.addAll(youngestCalendarDividends);
      }
    }
    return dividendsConnectorRead;
  }

  private void updateDividendData(Security security, List<Dividend> dividendsRead,
      List<Dividend> userCreatedDividends) {
    dividendJpaRepository.deleteByIdSecuritycurrencyAndCreateType(security.getIdSecuritycurrency(),
        CreateType.CONNECTOR_CREATED.getValue());

    DividendSplitsHelper.updateDividendSplitData(security, dividendsRead, userCreatedDividends,
        this.dividendJpaRepository);
  }

  /**
   * Securities can have a split. This must be reflected accordingly in the
   * dividends. Otherwise the dividend yield per share would no longer be correct.
   *
   * @param idSecurity
   * @param dividendsRead
   * @param isSplitAdjusted
   */
  private void splitAdjustDividends(Integer idSecurity, List<Dividend> dividendsRead, boolean isSplitAdjusted) {
    List<Securitysplit> securitysplitList = securitysplitJpaRepository
        .findByIdSecuritycurrencyOrderBySplitDateAsc(idSecurity);

    for (Dividend dividend : dividendsRead) {
      double factor = Securitysplit.calcSplitFatorForFromDate(securitysplitList,
          DateHelper.getDateFromLocalDate(dividend.getExDate()));
      if (isSplitAdjusted) {
        dividend.setAmount(dividend.getAmountAdjusted() * factor);
      } else {
        dividend.setAmountAdjusted(dividend.getAmount() / factor);
      }
    }
  }
}
