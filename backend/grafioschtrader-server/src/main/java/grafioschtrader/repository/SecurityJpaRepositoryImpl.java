/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.repository;

import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.common.UserAccessHelper;
import grafioschtrader.connector.ConnectorHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.connector.instrument.IFeedConnector.FeedSupport;
import grafioschtrader.dto.InstrumentStatisticsResult;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityDerivedLink;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.entities.User;
import grafioschtrader.priceupdate.ThruCalculationHelper;
import grafioschtrader.priceupdate.historyquote.BaseHistoryquoteThru;
import grafioschtrader.priceupdate.historyquote.HistoryquoteThruCalculation;
import grafioschtrader.priceupdate.historyquote.HistoryquoteThruConnector;
import grafioschtrader.priceupdate.historyquote.IHistoryquoteLoad;
import grafioschtrader.priceupdate.historyquote.SecurityCurrencyMaxHistoryquoteData;
import grafioschtrader.priceupdate.intraday.IIntradayLoad;
import grafioschtrader.priceupdate.intraday.IntradayThruCalculation;
import grafioschtrader.priceupdate.intraday.IntradayThruConnector;
import grafioschtrader.reports.InstrumentStatisticsSummary;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityGrouped;
import grafioschtrader.reportviews.historyquotequality.HistoryquoteQualityHead;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyPosition;
import grafioschtrader.repository.SecurityJpaRepository.SplitAdjustedHistoryquotes;
import grafioschtrader.search.SecuritySearchBuilder;
import grafioschtrader.search.SecuritycurrencySearch;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.HistoryquoteCreateType;
import grafioschtrader.types.TaskDataExecPriority;
import grafioschtrader.types.TaskType;
import jakarta.annotation.PostConstruct;

public class SecurityJpaRepositoryImpl extends SecuritycurrencyService<Security, SecurityPositionSummary>
    implements IPositionCloseOnLatestPrice<Security, SecurityPositionSummary>, SecurityJpaRepositoryCustom {

  private IHistoryquoteLoad<Security> historyquoteThruCalculation;
  private IIntradayLoad<Security> intradayThruCalculation;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  protected CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  protected StockexchangeJpaRepository stockexchangeJpaRepository;

  @Autowired
  private SecurityDerivedLinkJpaRepository securityDerivedLinkJpaRepository;

  @Autowired
  private HistoryquotePeriodJpaRepository historyquotePeriodJpaRepository;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Autowired
  private MessageSource messages;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  // Circular Dependency -> Lazy
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityRepository;

  @Autowired
  public void setHoldSecurityaccountSecurityRepository(
      @Lazy final HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityRepository) {
    this.holdSecurityaccountSecurityRepository = holdSecurityaccountSecurityRepository;
  }

  @PostConstruct
  private void postConstruct() {
    historyquoteThruConnector = new HistoryquoteThruConnector<>(entityManager, globalparametersJpaRepository,
        feedConnectorbeans, this, Security.class);
    historyquoteThruCalculation = new HistoryquoteThruCalculation<>(securityJpaRepository, historyquoteJpaRepository,
        securityDerivedLinkJpaRepository, globalparametersJpaRepository, this);
    intradayThruConnector = new IntradayThruConnector<>(securityJpaRepository, globalparametersJpaRepository,
        feedConnectorbeans, this);
    intradayThruCalculation = new IntradayThruCalculation<>(globalparametersJpaRepository, securityJpaRepository,
        securityDerivedLinkJpaRepository);
  }

  ////////////////////////////////////////////////////////////////
  // Historical prices
  ////////////////////////////////////////////////////////////////

  @Override
  public List<Security> catchAllUpSecurityHistoryquote(List<Integer> idsStockexchange) {
    List<Security> securities = historyquoteThruConnector.catchAllUpSecuritycurrencyHistoryquote(idsStockexchange);
    securities.addAll(historyquoteThruCalculation.catchAllUpSecuritycurrencyHistoryquote(null));
    if (idsStockexchange == null || idsStockexchange.isEmpty()) {
      stockexchangeJpaRepository.updateHistoricalUpdateWithNowForAll();
    } else {
      stockexchangeJpaRepository.updateHistoricalUpdateWithNowByIdsStockexchange(idsStockexchange);
    }
    return securities;
  }

  @Override
  public void reloadAsyncFullHistoryquote(Security securitycurrency) {
    historyquoteThruConnector.reloadAsyncFullHistoryquote(securityServiceAsyncExectuion, this, securitycurrency);
  }

  @Override
  public void reloadAsyncFullHistoryquoteExternal(Integer idSecuritycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final Security security = securityJpaRepository.getReferenceById(idSecuritycurrency);
    if (!UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, security)) {
      throw new SecurityException(GlobalConstants.LIMIT_SECURITY_BREACH);
    }
    historyquoteThruConnector.reloadAsyncFullHistoryquote(securityServiceAsyncExectuion, this, security);
  }

  @Override
  public List<Security> tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(Integer idTenant,
      Integer idWatchlist) {
    return historyquoteThruConnector.fillHistoryquoteForSecuritiesCurrencies(
        securityJpaRepository.findByIdTenantAndIdWatchlistWhenRetryHistroyGreaterThan0(idTenant, idWatchlist),
        DateHelper.getCalendar(new Date()));
  }

  @Override
  @Transactional
  public HistoryquoteQualityHead getHistoryquoteQualityHead(HistoryquoteQualityGrouped groupedBy) {
    return this.historyquoteThruConnector.getHistoryquoteQualityHead(groupedBy, securityJpaRepository, messages);
  }

  /// call back for historyquoteThruConnector
  /////////////////////////////////////////////
  @Override
  public List<Historyquote> getHistoryQuote(final Security security, final Date fromDate, final Date toDate,
      final IFeedConnector feedConnector) throws Exception {
    return feedConnector.getEodSecurityHistory(security, fromDate, toDate);
  }

  @Override
  @Transactional
  @Modifying
  public Security rebuildSecurityCurrencypairHisotry(Security security) {
    historyquoteJpaRepository.removeAllSecurityHistoryquote(security.getIdSecuritycurrency());
    return getHistorquoteLoad(security).createHistoryQuotesAndSave(securityJpaRepository, security, null, null);
  }

  @Override
  @Transactional
  @Modifying
  public Security catchUpSecurityCurrencypairHisotry(Security security, final Date fromDate, final Date toDate) {
    security = securityJpaRepository.findByIdSecuritycurrency(security.getIdSecuritycurrency());
    return getHistorquoteLoad(security).createHistoryQuotesAndSave(securityJpaRepository, security, fromDate, toDate);
  }

  @Override
  protected boolean historyNeedToBeReloaded(final Security securityCurrencyChanged, final Security targetSecurity) {
    if (securityCurrencyChanged.getActiveToDate().getTime() <= System.currentTimeMillis()) {
      return false;
    }
    if (securityCurrencyChanged.isDerivedInstrument()) {
      return hasDerivedFieldsChanged(securityCurrencyChanged, targetSecurity);
    } else {
      return !(Objects.equals(securityCurrencyChanged.getIdConnectorHistory(), targetSecurity.getIdConnectorHistory())
          && Objects.equals(securityCurrencyChanged.getUrlHistoryExtend(), targetSecurity.getUrlHistoryExtend()));
    }
  }

  private boolean hasDerivedFieldsChanged(final Security securityCurrencyChanged, final Security targetSecurity) {
    boolean isEqual = Objects.equals(securityCurrencyChanged.getFormulaPrices(), targetSecurity.getFormulaPrices())
        && Objects.equals(securityCurrencyChanged.getIdLinkSecuritycurrency(),
            targetSecurity.getIdLinkSecuritycurrency())
        && securityCurrencyChanged.getSecurityDerivedLinks().length == targetSecurity.getSecurityDerivedLinks().length;

    Arrays.sort(securityCurrencyChanged.getSecurityDerivedLinks(),
        Comparator.comparingInt(SecurityDerivedLink::getIdLinkSecuritycurrency));
    Arrays.sort(targetSecurity.getSecurityDerivedLinks(),
        Comparator.comparingInt(SecurityDerivedLink::getIdLinkSecuritycurrency));

    for (int i = 0; isEqual && i < securityCurrencyChanged.getSecurityDerivedLinks().length; i++) {
      isEqual = securityCurrencyChanged.getSecurityDerivedLinks()[i]
          .equals(targetSecurity.getSecurityDerivedLinks()[i]);
    }
    return !isEqual;
  }

  @Override
  public void setDividendDownloadLink(SecuritycurrencyPosition<Security> securitycurrencyPosition) {
    if (securitycurrencyPosition.securitycurrency.getIdConnectorDividend() != null) {
      IFeedConnector iFeedConnector = ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans,
          securitycurrencyPosition.securitycurrency.getIdConnectorDividend(), IFeedConnector.FeedSupport.DIVIDEND);
      securitycurrencyPosition.dividendUrl = iFeedConnector
          .getDividendHistoricalDownloadLink(securitycurrencyPosition.securitycurrency);
    }
  }

  @Override
  protected IHistoryquoteLoad<Security> getHistorquoteLoad(Security security) {
    return security.isDerivedInstrument() ? historyquoteThruCalculation : historyquoteThruConnector;
  }

  ////////////////////////////////////////////////////////////////
  // Intraday prices
  ////////////////////////////////////////////////////////////////

  @Override
  public List<Security> updateLastPriceByList(List<Security> securities) {
    List<Security> securitiesRc = intradayThruConnector.updateLastPriceOfSecuritycurrency(
        securities.stream().filter(s -> !s.isDerivedInstrument()).collect(Collectors.toList()));
    securitiesRc.addAll(intradayThruCalculation.updateLastPriceOfSecuritycurrency(
        securities.stream().filter(Security::isDerivedInstrument).collect(Collectors.toList())));
    return securitiesRc;
  }
  /*
   * @Override public void updateAllLastPrice() { List<Security> securities =
   * securityJpaRepository.findAll();
   * intradayThruConnector.updateLastPriceOfSecuritycurrency(
   * securities.stream().filter(s ->
   * !s.isDerivedInstrument()).collect(Collectors.toList()));
   * intradayThruCalculation.updateLastPriceOfSecuritycurrency(
   * securities.stream().filter(Security::isDerivedInstrument).collect(Collectors.
   * toList())); }
   */

  @Override
  protected Security updateLastPriceSecurityCurrency(final Security security, final short maxIntraRetry,
      final int scIntradayUpdateTimeout) {
    return getIntradayLoad(security).updateLastPriceSecurityCurrency(security, maxIntraRetry, scIntradayUpdateTimeout);
  }

  /// call back for intradayThruConnector
  /////////////////////////////////////////////
  @Override
  public void updateIntraSecurityCurrency(final Security securitycurrency, final IFeedConnector feedConcector)
      throws Exception {
    feedConcector.updateSecurityLastPrice(securitycurrency);
  }

  protected IIntradayLoad<Security> getIntradayLoad(Security security) {
    return security.isDerivedInstrument() ? intradayThruCalculation : intradayThruConnector;
  }

  ////////////////////////////////////////////////////////////////
  // General procedures
  ////////////////////////////////////////////////////////////////
  @Override
  public SecurityCurrencypairJpaRepository<Security> getJpaRepository() {
    return securityJpaRepository;
  }

  @Override
  public void calculatePositionClose(final SecurityPositionSummary securityPositionSummary, final Double lastPrice) {
    securityPositionSummary.calcGainLossByPrice(lastPrice);
  }

  @Override
  public List<SecurityPositionSummary> processOpenPositionsWithActualPrice(final Date untilDate,
      final Map<Security, SecurityPositionSummary> summarySecurityMap) {

    final List<SecurityPositionSummary> securityPositionSummaryList = summarySecurityMap.entrySet().stream()
        .filter(map -> map.getValue().units != 0).map(map -> map.getValue()).collect(Collectors.toList());
    if (!securityPositionSummaryList.isEmpty()) {
      this.securityJpaRepository.calcGainLossBasedOnDateOrNewestPrice(securityPositionSummaryList, untilDate);
    }
    return securityPositionSummaryList;
  }

  @Override
  public void calcGainLossBasedOnDateOrNewestPrice(final List<SecurityPositionSummary> securitycurrencyPositionSummary,
      final Date untilDate) {
    super.calcGainLossBasedOnDateOrNewestPrice(securitycurrencyPositionSummary, this, untilDate);
  }

  @Override
  public List<SecurityCurrencyMaxHistoryquoteData<Security>> getMaxHistoryquoteResult(final short maxHistoryRetry,
      BaseHistoryquoteThru<Security> baseHistoryquoteThru, List<Integer> idsStockexchange) {
    if (baseHistoryquoteThru instanceof HistoryquoteThruCalculation) {
      return securityJpaRepository.getMaxHistoryquoteWithCalculation(maxHistoryRetry);
    } else {
      return idsStockexchange != null && idsStockexchange.size() > 0
          ? securityJpaRepository.getMaxHistoryquoteWithConnectorForExchange(maxHistoryRetry, idsStockexchange)
          : securityJpaRepository.getMaxHistoryquoteWithConnector(maxHistoryRetry);
    }
  }

  @Override
  public List<Security> searchByCriteria(final SecuritycurrencySearch securitycurrencySearch) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return searchBuilderWithExclusion(null, null, securitycurrencySearch, user.getIdTenant());
  }

  @Override
  public List<Security> searchBuilderWithExclusion(final Integer idWatchlist, Integer idCorrelationSet,
      final SecuritycurrencySearch securitycurrencySearch, final Integer idTenant) {

    if (securitycurrencySearch.getAssetclassType() == AssetclassType.CURRENCY_PAIR) {
      return Collections.emptyList();
    } else {
      List<Integer> idSecurityList = null;
      if (securitycurrencySearch.getWithHoldings()) {
        idSecurityList = this.holdSecurityaccountSecurityRepository.getIdSecurityByIdTenantWithHoldings(idTenant);
      }
      return this.securityJpaRepository.findAll(
          new SecuritySearchBuilder(idWatchlist, idCorrelationSet, securitycurrencySearch, idTenant, idSecurityList));
    }
  }

  @Override
  public List<Security> tryUpToIntradayDataWhenRetryIntraLoadGreaterThan0(Integer idTenant, Integer idWatchlist) {
    return intradayThruConnector.updateLastPriceOfSecuritycurrency(securityJpaRepository
        .findWithConnectorByIdTenantAndIdWatchlistWhenRetryIntraGreaterThan0(idTenant, idWatchlist), (short) -1);
  }

  @Override
  public List<Security> getTradableSecuritiesByTenantAndIdWatschlist(Integer idWatchlist) throws ParseException {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return securityJpaRepository.getTradableSecuritiesByTenantAndIdWatschlist(user.getIdTenant(), idWatchlist);

  }

  @Override
  public List<Security> findByActiveToDateGreaterThanEqualOrderByName(final String dateString) throws ParseException {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final Date untilDate = new SimpleDateFormat(GlobalConstants.SHORT_STANDARD_DATE_FORMAT).parse(dateString);
    return securityJpaRepository.findByActiveToDateGreaterThanEqualAndIdTenantPrivateIsNullOrIdTenantPrivateOrderByName(
        untilDate, user.getIdTenant());
  }

  @Override
  public SplitAdjustedHistoryquotes isYoungestSplitHistoryquotePossibleAdjusted(Security security,
      List<Securitysplit> securitysplits, boolean useConnector) throws Exception {

    SplitAdjustedHistoryquotes sah = SplitAdjustedHistoryquotes.NOT_DETCTABLE;
    Optional<Securitysplit> maxSecuritysplitOpt = securitysplits.stream()
        .max(Comparator.comparing(Securitysplit::getSplitDate));
    if (!maxSecuritysplitOpt.isEmpty()) {
      Securitysplit maxSplit = maxSecuritysplitOpt.get();
      if (maxSplit.getFactor() < 1.0 && maxSplit.getFactor() >= 1 / GlobalConstants.DETECT_SPLIT_ADJUSTED_FACTOR_STEP
          || maxSplit.getFactor() > 1.0
              && maxSplit.getFactor() >= 1 + 1 / GlobalConstants.DETECT_SPLIT_ADJUSTED_FACTOR_STEP) {

        Date fromDate = DateHelper.setTimeToZeroAndAddDay(maxSplit.getSplitDate(),
            GlobalConstants.SPLIT_DAYS_FOR_AVERAGE_CALC * -1);
        Date toDate = DateHelper.setTimeToZeroAndAddDay(maxSplit.getSplitDate(),
            GlobalConstants.SPLIT_DAYS_FOR_AVERAGE_CALC);

        List<Historyquote> historyquotes = useConnector ? getDataByConnnector(security, fromDate, toDate)
            : historyquoteJpaRepository
                .findByIdSecuritycurrencyAndDateBetweenOrderByDate(security.getIdSecuritycurrency(), fromDate, toDate);

        OptionalDouble averageCloseBeforeOpt = historyquotes.stream()
            .filter(h -> h.getDate().before(maxSplit.getSplitDate())).mapToDouble(h -> h.getClose()).average();
        OptionalDouble averageCloseAfterOpt = historyquotes.stream()
            .filter(h -> !h.getDate().before(maxSplit.getSplitDate())).mapToDouble(h -> h.getClose()).average();

        if (averageCloseBeforeOpt.isPresent() && averageCloseAfterOpt.isPresent()) {
          double changeFactor = averageCloseAfterOpt.getAsDouble() / averageCloseBeforeOpt.getAsDouble();
          // If the historical prices after and before the split date are similar, it is
          // assumed that the split is included in the historical prices.
          return maxSplit.getFactor() >= 1
              && changeFactor < 1 / maxSplit.getFactor() + 1 / GlobalConstants.DETECT_SPLIT_ADJUSTED_FACTOR_STEP
                  ? SplitAdjustedHistoryquotes.NOT_ADJUSTED
                  : maxSplit.getFactor() < 1
                      && changeFactor < 1 * maxSplit.getFactor() + 1 / GlobalConstants.DETECT_SPLIT_ADJUSTED_FACTOR_STEP
                          ? SplitAdjustedHistoryquotes.NOT_ADJUSTED
                          : SplitAdjustedHistoryquotes.ADJUSTED;
        }
      }
    }
    return sah;
  }

  private List<Historyquote> getDataByConnnector(Security security, Date fromDate, Date toDate) throws Exception {
    IFeedConnector feedConnector = ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans,
        security.getIdConnectorHistory(), IFeedConnector.FeedSupport.HISTORY);
    return getHistoryQuote(security, fromDate, toDate, feedConnector);
  }

  @Override
  protected Security beforeSave(Security security, Security existingSecurity, User user) throws Exception {
    Security cloneSecurity = null;
    if (security.getIdTenantPrivate() != null && !user.getIdTenant().equals(security.getIdTenantPrivate())) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
    security.clearProperties();
    ThruCalculationHelper.checkFormulaAgainstInstrumetLinks(security, user.getLocaleStr());
    if (existingSecurity != null && existingSecurity.isDerivedInstrument()) {
      setSecurityDerivedLinks(security, existingSecurity);
    }
    if (existingSecurity != null) {
      try {
        cloneSecurity = new Security();
        BeanUtils.copyProperties(cloneSecurity, existingSecurity);
      } catch (IllegalAccessException | InvocationTargetException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }

    return cloneSecurity;
  }

  @Override
  public void checkAndClearSecuritycurrencyConnectors(final Security security) {
    super.checkAndClearSecuritycurrencyConnectors(security);
    if (security.getIdConnectorDividend() != null) {
      FeedSupport fd = IFeedConnector.FeedSupport.DIVIDEND;
      IFeedConnector fc = ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans,
          security.getIdConnectorDividend(), fd);
      fc.checkAndClearSecuritycurrencyUrlExtend(security, fd);
    }
    if (security.getIdConnectorSplit() != null) {
      FeedSupport fd = IFeedConnector.FeedSupport.SPLIT;
      IFeedConnector fc = ConnectorHelper.getConnectorByConnectorId(feedConnectorbeans, security.getIdConnectorSplit(),
          fd);
      fc.checkAndClearSecuritycurrencyUrlExtend(security, fd);
    }
  }

  @Override
  protected void afterSave(Security security, Security securityBefore, User user, boolean historyAccessHasChanged) {

    if (historyAccessHasChanged) {
      taskDataChangeJpaRepository
          .save(new TaskDataChange(TaskType.SECURITY_LOAD_HISTORICAL_INTRA_PRICE_DATA, TaskDataExecPriority.PRIO_NORMAL,
              LocalDateTime.now(), security.getIdSecuritycurrency(), Security.class.getSimpleName()));
    }

    if ((securityBefore != null
        && !(StringUtils.equals(security.getIdConnectorSplit(), securityBefore.getIdConnectorSplit())
            && StringUtils.equals(security.getUrlSplitExtend(), securityBefore.getUrlSplitExtend())))
        || (securityBefore == null && security.getIdConnectorSplit() != null)) {
      // Split connector has changed
      taskDataChangeJpaRepository
          .save(new TaskDataChange(TaskType.SECURITY_SPLIT_UPDATE_FOR_SECURITY, TaskDataExecPriority.PRIO_NORMAL,
              LocalDateTime.now().plusMinutes(5), security.getIdSecuritycurrency(), Security.class.getSimpleName()));
    }

    if ((securityBefore != null
        && !(StringUtils.equals(security.getIdConnectorDividend(), securityBefore.getIdConnectorDividend())
            && StringUtils.equals(security.getUrlDividendExtend(), securityBefore.getUrlDividendExtend())))
        || (securityBefore == null && security.getIdConnectorDividend() != null)) {
      // Dividend connector has changed
      taskDataChangeJpaRepository
          .save(new TaskDataChange(TaskType.SECURITY_DIVIDEND_UPDATE_FOR_SECURITY, TaskDataExecPriority.PRIO_NORMAL,
              LocalDateTime.now().plusMinutes(5), security.getIdSecuritycurrency(), Security.class.getSimpleName()));
    }
  }

  private void setSecurityDerivedLinks(Security security, Security existingSecurity) {
    List<SecurityDerivedLink> securityDerivedLinks = securityDerivedLinkJpaRepository
        .findByIdEmIdSecuritycurrencyOrderByIdEmIdSecuritycurrency(existingSecurity.getIdSecuritycurrency());
    existingSecurity
        .setSecurityDerivedLinks(securityDerivedLinks.toArray(new SecurityDerivedLink[securityDerivedLinks.size()]));
    for (int i = 0; i < security.getSecurityDerivedLinks().length; i++) {
      security.getSecurityDerivedLinks()[i].setIdSecuritycurrency(security.getIdSecuritycurrency());
    }
  }

  @Override
  public boolean checkUserCanChangeDerivedFields(User user, Security security, Security existingSecurity) {
    boolean hasRights = UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, existingSecurity);
    if (!hasRights && existingSecurity != null && existingSecurity.isDerivedInstrument()) {
      setSecurityDerivedLinks(security, existingSecurity);
      if (!UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, existingSecurity)
          && hasDerivedFieldsChanged(security, existingSecurity)) {
        throw new SecurityException(GlobalConstants.FILED_EDIT_SECURITY_BREACH);
      }
    }
    return hasRights;
  }

  @Override
  protected Security afterMainEntitySaved(Security security, Security beforSaveSecuritycurrency) {
    if (security.isDerivedInstrument()) {
      securityDerivedLinkJpaRepository.deleteByIdEmIdSecuritycurrency(security.getIdSecuritycurrency());
      List<SecurityDerivedLink> securityDerivedLinks = Arrays
          .asList(beforSaveSecuritycurrency.getSecurityDerivedLinks());
      securityDerivedLinks
          .forEach(securityDerivedLink -> securityDerivedLink.setIdSecuritycurrency(security.getIdSecuritycurrency()));
      securityDerivedLinkJpaRepository.saveAll(Arrays.asList(beforSaveSecuritycurrency.getSecurityDerivedLinks()));
    } else if (security.getStockexchange().isNoMarketValue()) {
      historyquotePeriodJpaRepository.adjustHistoryquotePeriod(security);
    }

    return security;
  }

  @Override
  public InstrumentStatisticsResult getSecurityStatisticsReturnResult(Integer idSecuritycurrency, LocalDate dateFrom,
      LocalDate dateTo) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    var securityStatisticsSummary = new InstrumentStatisticsSummary(securityJpaRepository, tenantJpaRepository,
        currencypairJpaRepository);
    securityStatisticsSummary.prepareSecurityCurrencypairs(idSecuritycurrency);
    return new InstrumentStatisticsResult(securityStatisticsSummary.getAnnualisedSecurityPerformance(),
        securityStatisticsSummary.getStandardDeviation(jdbcTemplate, dateFrom, dateTo, false));
  }

  @Override
  public List<Historyquote> fillGap(Security security) {
    List<Date> missingDates = historyquoteJpaRepository.getMissingEODForSecurityByUpdCalendarIndex(
        security.getStockexchange().getIdIndexUpdCalendar(), security.getIdSecuritycurrency());
    List<Historyquote> historyquotesFill = new ArrayList<>();
    if (!missingDates.isEmpty()) {
      
      int missingDateCounter = 0;
      int historyIdxFirst = Collections.binarySearch(security.getHistoryquoteList(), new Historyquote(missingDates.get(0)),
          (h1, h2) -> h1.getDate().compareTo(h2.getDate())) * -1 - 1;
      if (historyIdxFirst > 0) {
        fillGapEODAfterFirstHistoryquote(security, historyquotesFill, historyIdxFirst, missingDates, missingDateCounter);
      } else {
        fillGapEODBeforeFirstHistoryquote(security, historyquotesFill, missingDates, missingDateCounter);
      }
    }
    return historyquotesFill;
  }

  /**
   * Fill in the past because first available EOD after oldest date.
   */
  private void fillGapEODBeforeFirstHistoryquote(Security security, List<Historyquote> historyquotesFill,
      List<Date> missingDates, int missingDateCounter) {
    do {
      historyquotesFill
          .add(new Historyquote(security.getIdSecuritycurrency(), HistoryquoteCreateType.FILL_GAP_BY_CONNECTOR,
              missingDates.get(missingDateCounter), security.getHistoryquoteList().get(0).getClose()));
      missingDateCounter++;
    } while (missingDateCounter < missingDates.size()
        && security.getHistoryquoteList().get(0).getDate().after(missingDates.get(missingDateCounter)));
    fillGapEODAfterFirstHistoryquote(security, historyquotesFill, 0, missingDates, missingDateCounter);
  }

  private void fillGapEODAfterFirstHistoryquote(Security security, List<Historyquote> historyquotesFill, int historyIdx,
      List<Date> missingDates, int missingDateCounter) {
    List<Historyquote> hql = security.getHistoryquoteList();
    while(missingDateCounter < missingDates.size()) {
      if (historyIdx == hql.size() || historyIdx < hql.size()
          && missingDates.get(missingDateCounter).before(hql.get(historyIdx).getDate())) {
        historyquotesFill
            .add(new Historyquote(security.getIdSecuritycurrency(), HistoryquoteCreateType.FILL_GAP_BY_CONNECTOR,
                missingDates.get(missingDateCounter), hql.get(historyIdx - 1).getClose()));
        missingDateCounter++;
      } else {
        historyIdx = Collections.binarySearch(hql,
            new Historyquote(missingDates.get(missingDateCounter)), (h1, h2) -> h1.getDate().compareTo(h2.getDate()))
            * -1 - 1; 
      }
    }
  }
  
  @Override
  public HistoryquoteJpaRepository getHistoryquoteJpaRepository() {
    return historyquoteJpaRepository;
  }


}
