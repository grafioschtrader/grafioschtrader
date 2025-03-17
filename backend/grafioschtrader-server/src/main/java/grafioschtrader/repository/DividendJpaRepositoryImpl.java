package grafioschtrader.repository;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
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

import grafiosch.common.DateHelper;
import grafiosch.entities.Globalparameters;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafioschtrader.GlobalConstants;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.calendar.IDividendCalendarFeedConnector;
import grafioschtrader.connector.calendar.IDividendCalendarFeedConnector.CalendarDividends;
import grafioschtrader.connector.instrument.BaseFeedApiKeyConnector;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.dto.SecurityKeyISINCurrency;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.service.GlobalparametersService;
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
  private GlobalparametersService globalparametersService;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Override
  public List<Security> appendThruDividendCalendar() {
    Map<Integer, Security> missingConnectorSecurities = new HashMap<>();
    Optional<Globalparameters> gpLastAppend = globalparametersJpaRepository
        .findById(GlobalParamKeyDefault.GLOB_KEY_YOUNGEST_DIVIDEND_APPEND_DATE);
    gpLastAppend.ifPresentOrElse(gp -> loadDividendData(gp.getPropertyDate().plusDays(1), missingConnectorSecurities),
        () -> loadDividendData(LocalDate.now(), missingConnectorSecurities));
    return missingConnectorSecurities.values().stream().sorted(Comparator.comparing(Security::getName))
        .collect(Collectors.toList());
  }

  private void loadDividendData(LocalDate fromDate, Map<Integer, Security> missingConnectorSecurities) {
    LocalDate now = LocalDate.now();
    if (!fromDate.isAfter(now)) {
      List<TradingDaysPlus> tradingDaysPlusList = tradingDaysPlusJpaRepository
          .findByTradingDateBetweenOrderByTradingDate(fromDate, now);
      if (!tradingDaysPlusList.isEmpty()) {
        dividendCalendarFeedConnectors.sort(Comparator.comparingInt(IDividendCalendarFeedConnector::getPriority));
        stepThruEveryCalendarDay(tradingDaysPlusList, missingConnectorSecurities);
      }
    }

  }

  private void stepThruEveryCalendarDay(List<TradingDaysPlus> tradingDaysPlusList,
      Map<Integer, Security> missingConnectorSecurities) {
    for (TradingDaysPlus tradingDaysPlus : tradingDaysPlusList) {
      for (IDividendCalendarFeedConnector calendarFeedConnector : dividendCalendarFeedConnectors) {
        try {
          List<CalendarDividends> cd = calendarFeedConnector.getExDateDividend(tradingDaysPlus.getTradingDate());
          if (calendarFeedConnector.supportISIN()) {
            addDividendsByISIN(cd, missingConnectorSecurities);
          }
        } catch (Exception ex) {
          log.error(ex.getMessage(), ex);
        }
      }
    }
    Globalparameters gp = new Globalparameters(GlobalParamKeyDefault.GLOB_KEY_YOUNGEST_DIVIDEND_APPEND_DATE);
    gp.setPropertyDate(tradingDaysPlusList.getLast().getTradingDate());
    gp.setChangedBySystem(true);
    globalparametersJpaRepository.save(gp);
  }

  private void addDividendsByISIN(List<CalendarDividends> calendarDividends,
      Map<Integer, Security> missingConnectorSecurities) {
    Set<String> isinSet = calendarDividends.stream().map(c -> c.isin).collect(Collectors.toSet());
    Map<SecurityKeyISINCurrency, Security> securitiesMap = securityJpaRepository.findAllByIsinIn(isinSet).stream()
        .collect(Collectors.toMap(security -> new SecurityKeyISINCurrency(security.getIsin(), security.getCurrency()),
            Function.identity()));
    if (!securitiesMap.isEmpty()) {
      Map<SecurityKeyISINCurrency, List<CalendarDividends>> cdMap = calendarDividends.stream()
          .collect(Collectors.groupingBy(cd -> new SecurityKeyISINCurrency(cd.isin, cd.currency)));
      for (Map.Entry<SecurityKeyISINCurrency, List<CalendarDividends>> entry : cdMap.entrySet()) {
        if (securitiesMap.containsKey(entry.getKey())) {
          Security security = securitiesMap.get(entry.getKey());
          if (security.getIdConnectorDividend() != null) {
            loadAllDividendDataFromConnector(security, entry.getValue().stream()
                .map(c -> c.getDivident(security.getIdSecuritycurrency())).collect(Collectors.toList()));
          } else {
            missingConnectorSecurities.put(security.getIdSecuritycurrency(), security);
          }

        }
      }
    }
  }

  @Override
  public List<String> periodicallyUpdate() {
    List<String> errorMessages = new ArrayList<>();
    List<Integer> securityIds = dividendJpaRepository.getIdSecurityForPeriodicallyUpdate(
        GlobalConstants.DIVIDEND_FREQUENCY_PLUS_DAY, globalparametersService.getMaxDividendRetry());
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
    IFeedConnector connector = ConnectorHelper.getConnectorByConnectorId(feedConnectors,
        security.getIdConnectorDividend(), IFeedConnector.FeedSupport.FS_DIVIDEND);
    try {
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
    return replaceApiKey(connector, errorMessages);
  }

  private List<String> replaceApiKey(IFeedConnector connector, List<String> errorMessages) {
    if (connector instanceof BaseFeedApiKeyConnector keyConnector) {
      for (int i = 0; i < errorMessages.size(); i++) {
        errorMessages.set(i, keyConnector.hideApiKeyForError(errorMessages.get(i)));
      }
    }
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
