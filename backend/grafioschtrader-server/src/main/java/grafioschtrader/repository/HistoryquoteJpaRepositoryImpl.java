package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;

import com.ezylang.evalex.EvaluationException;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.common.DateHelper;
import grafioschtrader.common.UserAccessHelper;
import grafioschtrader.dto.DeleteHistoryquotesSuccess;
import grafioschtrader.dto.HistoryquoteDateClose;
import grafioschtrader.dto.HistoryquotesWithMissings;
import grafioschtrader.dto.IDateAndClose;
import grafioschtrader.dto.IHistoryquoteQuality;
import grafioschtrader.dto.ISecuritycurrencyIdDateClose;
import grafioschtrader.dto.UserAuditable;
import grafioschtrader.entities.Auditable;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.entities.User;
import grafioschtrader.entities.projection.IFormulaInSecurity;
import grafioschtrader.entities.projection.IFormulaSecurityLoad;
import grafioschtrader.priceupdate.ThruCalculationHelper;
import grafioschtrader.ta.TaIndicators;
import grafioschtrader.ta.TaTraceIndicatorData;
import grafioschtrader.ta.indicator.calc.CalcAccessIndicator;
import grafioschtrader.ta.indicator.calc.ExponentialMovingAverage;
import grafioschtrader.ta.indicator.calc.SimpleMovingAverage;
import grafioschtrader.ta.indicator.model.ShortMediumLongInputPeriod;
import grafioschtrader.types.HistoryquoteCreateType;
import grafioschtrader.types.TaskDataExecPriority;
import grafioschtrader.types.TaskType;

public class HistoryquoteJpaRepositoryImpl extends BaseRepositoryImpl<Historyquote>
    implements HistoryquoteJpaRepositoryCustom {

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private SecurityDerivedLinkJpaRepository securityDerivedLinkJpaRepository;

  @Autowired
  private HistoryquotePeriodJpaRepository historyquotePeriodJpaRepository;

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Override
  public ISecuritycurrencyIdDateClose getCertainOrOlderDayInHistorquoteByIdSecuritycurrency(
      final Integer idSecuritycurrency, final String dateString, final boolean asTraded) throws ParseException {
    final Date date = new SimpleDateFormat(GlobalConstants.SHORT_STANDARD_DATE_FORMAT).parse(dateString);
    return getCertainOrOlderDayInHistorquoteByIdSecuritycurrency(idSecuritycurrency, date, asTraded);
  }

  @Override
  public ISecuritycurrencyIdDateClose getCertainOrOlderDayInHistorquoteByIdSecuritycurrency(
      final Integer idSecuritycurrency, final Date date, final boolean asTraded) {
    final List<Integer> securitycurrencies = new ArrayList<>();
    securitycurrencies.add(idSecuritycurrency);
    final List<ISecuritycurrencyIdDateClose> securitycurrencyIdDateCloseList = historyquoteJpaRepository
        .getIdDateCloseByIdsAndDate(securitycurrencies, date);
    if (securitycurrencyIdDateCloseList.size() == 1) {
      ISecuritycurrencyIdDateClose securitycurrencyIdDateClose = securitycurrencyIdDateCloseList.get(0);
      if (asTraded) {
        final Double factor = securitysplitJpaRepository.getSplitFactorAfterThanEqualDate(idSecuritycurrency,
            securitycurrencyIdDateClose.getDate());
        if (factor != null) {
          return new ISecuritycurrencyIdDateClose() {
            @Override
            public Integer getIdSecuritycurrency() {
              return securitycurrencyIdDateClose.getIdSecuritycurrency();
            }

            @Override
            public Date getDate() {
              return securitycurrencyIdDateClose.getDate();
            }

            @Override
            public double getClose() {
              return DataHelper.round(securitycurrencyIdDateClose.getClose() * factor);
            }
          };
        }
      }
      return securitycurrencyIdDateClose;
    }
    return null;
  }

  @Override
  public UserAuditable getUserAndCheckSecurityAccess(Integer idSecuritycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Auditable autitable = historyquoteJpaRepository.getParentSecurityCurrency(user, idSecuritycurrency);
    if (!UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, autitable)) {
      throw new SecurityException(GlobalConstants.LIMIT_SECURITY_BREACH);
    }
    return new UserAuditable(autitable, user);

  }

  @Override
  public Auditable getParentSecurityCurrency(final User user, final Historyquote historyquote) {
    return getParentSecurityCurrency(user, historyquote.getIdSecuritycurrency());
  }

  @Override
  public Auditable getParentSecurityCurrency(final User user, Integer idSecuritycurrency) {
    Optional<Security> securityOpt = securityJpaRepository.findById(idSecuritycurrency);
    if (securityOpt.isPresent()) {
      // Parent is a security
      return securityOpt.get();
    } else {
      return currencypairJpaRepository.getReferenceById(idSecuritycurrency);
    }
  }

  @Override
  @Transactional
  @Modifying
  public Historyquote saveOnlyAttributes(final Historyquote historyquote, final Historyquote existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    long dayDiff = checkDatePastMinus1Day(historyquote);

    final Historyquote historyquoteFillDate = historyquoteJpaRepository
        .findByIdSecuritycurrencyAndDateAndCreateTypeFilled(historyquote.getIdSecuritycurrency(),
            historyquote.getDate());
    if (historyquoteFillDate != null) {
      historyquoteJpaRepository.delete(historyquoteFillDate);
    }

    if (existingEntity != null && !historyquote.getDate().equals(existingEntity.getDate())) {
      throw new SecurityException(GlobalConstants.FILED_EDIT_SECURITY_BREACH);
    }
    historyquote.setCreateModifyTime(new Date());
    historyquote.setCreateType(HistoryquoteCreateType.ADD_MODIFIED_USER);
    Historyquote historyquoteSaved = historyquoteJpaRepository.save(historyquote);
    updateHolidaysWeekendHistoryquotes(historyquoteSaved);
    updateCalculationByChangedHistoryquote(historyquoteSaved);
    if (dayDiff > 1) {
      Optional<Security> securityOpt = securityJpaRepository.findById(historyquote.getIdSecuritycurrency());
      if (securityOpt.isEmpty()) {
        taskDataChangeJpaRepository
            .save(new TaskDataChange(TaskType.REBUILD_HOLDING_CASHACCOUNT_DEPOSIT_OUT_DATED_CURRENCY_PAIR_PRICE,
                TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now(), historyquote.getIdSecuritycurrency(), null));
      }
    }
    return historyquoteSaved;
  }

  private long checkDatePastMinus1Day(Historyquote historyquote) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Calendar calendarHistoryquote = Calendar.getInstance();
    calendarHistoryquote.setTime(historyquote.getDate());
    if (calendarHistoryquote.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
        || calendarHistoryquote.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
      throw new IllegalArgumentException("The date must be a working day!");
    }

    Calendar calendarNow = DateHelper.getCalendar(new Date());
    calendarNow.add(Calendar.MINUTE, user.getTimezoneOffset() * -1);
    long days = ChronoUnit.DAYS.between(calendarHistoryquote.toInstant(), calendarNow.toInstant());
    if (days < 1) {
      throw new IllegalArgumentException("The date must be yesterday or older!");
    }
    return days;
  }

  @Override
  @Transactional
  @Modifying
  public void afterDelete(Optional<Historyquote> deletedHistoryquoteOpt) {
    if (deletedHistoryquoteOpt.isPresent()) {
      List<IFormulaSecurityLoad> dependingSecurities = securityJpaRepository
          .getBySecurityDerivedLinkByIdSecurityLink(deletedHistoryquoteOpt.get().getIdSecuritycurrency());
      for (IFormulaInSecurity security : dependingSecurities) {
        historyquoteJpaRepository.deleteByIdSecuritycurrencyAndDate(security.getIdSecuritycurrency(),
            deletedHistoryquoteOpt.get().getDate());
      }
    }
  }

  /**
   * Update linked history quotes in derived instrument.
   *
   * @param savedHistoryquote
   * @throws com.ezylang.evalex.parser.ParseException 
   * @throws EvaluationException 
   */
  private void updateCalculationByChangedHistoryquote(Historyquote savedHistoryquote) throws EvaluationException, com.ezylang.evalex.parser.ParseException {
    List<IFormulaSecurityLoad> dependingSecurities = securityJpaRepository
        .getBySecurityDerivedLinkByIdSecurityLink(savedHistoryquote.getIdSecuritycurrency());

    for (IFormulaInSecurity security : dependingSecurities) {
      List<Historyquote> historyquotes = ThruCalculationHelper.loadDataAndCreateHistoryquotes(
          securityDerivedLinkJpaRepository, historyquoteJpaRepository, security, savedHistoryquote.getDate(),
          savedHistoryquote.getDate());
      for (Historyquote historyquote : historyquotes) {
        // It should only be one
        Optional<Historyquote> historyquoteOptional = historyquoteJpaRepository
            .findByIdSecuritycurrencyAndDate(historyquote.getIdSecuritycurrency(), historyquote.getDate());

        if (historyquoteOptional.isPresent()) {
          historyquoteOptional.get().updateThis(historyquote);
        } else {
          historyquoteJpaRepository.save(historyquote);
        }
      }
    }
  }

  /**
   * Some no trading days are filled with history quote, especially currencies
   * quotes. Those days must be adjusted when a history quote is edited manually.
   *
   * @param historyquoteBefore
   */
  private void updateHolidaysWeekendHistoryquotes(final Historyquote historyquoteBefore) {
    final int maxFillDays = globalparametersJpaRepository.getMaxFillDaysCurrency();
    Pageable limit = PageRequest.of(0, maxFillDays);
    List<Historyquote> toSaveHistoryquotes = new ArrayList<>();

    List<Historyquote> historyquotes = historyquoteJpaRepository
        .findByIdSecuritycurrencyAndDateGreaterThanOrderByDateAsc(historyquoteBefore.getIdSecuritycurrency(),
            historyquoteBefore.getDate(), limit);
    for (int i = 0; i < historyquotes.size(); i++) {
      Historyquote historyquote = historyquotes.get(i);
      if (historyquote.getCreateType() != HistoryquoteCreateType.FILLED_NON_TRADE_DAY) {
        break;
      }
      if (DateHelper.getDateDiff(historyquoteBefore.getDate(), historyquote.getDate(), TimeUnit.DAYS) == i) {
        historyquote.updateThis(historyquoteBefore);
        toSaveHistoryquotes.add(historyquote);
      }
    }
    historyquoteJpaRepository.saveAll(toSaveHistoryquotes);
  }

  @Override
  public void fillMissingPeriodWithHistoryquotes(final Historyquote dayBeforHoleHistoryquote,
      final Historyquote dayAfterHoleHistoryquote) {
    Date targetDate = DateHelper.setTimeToZeroAndAddDay(dayBeforHoleHistoryquote.getDate(), 1);
    List<Historyquote> toCreateHistoryquotes = new ArrayList<>();
    while (targetDate.getTime() < dayAfterHoleHistoryquote.getDate().getTime()) {
      final Historyquote historyquote = new Historyquote();
      historyquote.updateThis(dayBeforHoleHistoryquote);
      historyquote.setDate(targetDate);
      historyquote.setCreateType(HistoryquoteCreateType.FILLED_NON_TRADE_DAY);
      historyquote.setIdSecuritycurrency(dayBeforHoleHistoryquote.getIdSecuritycurrency());
      toCreateHistoryquotes.add(historyquote);
      targetDate = DateHelper.setTimeToZeroAndAddDay(targetDate, 1);
    }
    historyquoteJpaRepository.saveAll(toCreateHistoryquotes);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <S extends Securitycurrency<S>> HistoryquotesWithMissings<S> getHistoryqoutesByIdSecuritycurrencyWithMissing(
      int idSecuritycurrency, boolean isCurrencypair) throws InterruptedException, ExecutionException {

    CompletableFuture<IHistoryquoteQuality> historyquoteQualityCF;
    CompletableFuture<Optional<?>> securityOrCurrencypairCF;
    final CompletableFuture<List<Historyquote>> historyquotesCF = CompletableFuture.supplyAsync(
        () -> historyquoteJpaRepository.findByIdSecuritycurrencyAndCreateTypeFalseOrderByDateDesc(idSecuritycurrency));
    if (isCurrencypair) {
      securityOrCurrencypairCF = CompletableFuture
          .supplyAsync(() -> currencypairJpaRepository.findById(idSecuritycurrency));
      historyquoteQualityCF = CompletableFuture
          .supplyAsync(() -> historyquoteJpaRepository.getMissingsDaysCountByIdCurrency(idSecuritycurrency));

    } else {
      securityOrCurrencypairCF = CompletableFuture
          .supplyAsync(() -> securityJpaRepository.findById(idSecuritycurrency));
      historyquoteQualityCF = CompletableFuture
          .supplyAsync(() -> historyquoteJpaRepository.getMissingsDaysCountByIdSecurity(idSecuritycurrency));
    }
    return new HistoryquotesWithMissings<>((S) securityOrCurrencypairCF.get().get(), historyquoteQualityCF.get(),
        historyquotesCF.get());
  }

  @Override
  public List<IDateAndClose> getHistoryquoteDateClose(Integer idSecuritycurrency) {
    UserAccess userAccess = checkUserAccess(idSecuritycurrency);
    if (userAccess.isCurrency) {
      return historyquoteJpaRepository.getByIdSecuritycurrencyAndCreateTypeNotOrderByDate(idSecuritycurrency,
          HistoryquoteCreateType.FILLED_NON_TRADE_DAY.getValue());
    } else {
      if (userAccess.security.getStockexchange().isNoMarketValue()) {
        return historyquotePeriodJpaRepository.getDateAndCloseByIdSecurity(idSecuritycurrency);
      } else {
        return historyquoteJpaRepository.getClosedAndMissingHistoryquoteByIdSecurity(idSecuritycurrency);
      }
    }

  }

  @Override
  public List<TaTraceIndicatorData> getTaWithShortMediumLongInputPeriod(Integer idSecuritycurrency,
      TaIndicators taIndicator, ShortMediumLongInputPeriod shortMediumLongInputPeriod) {
    checkUserAccess(idSecuritycurrency);
    List<TaTraceIndicatorData> taTraceIndicatorData = new ArrayList<>();
    List<HistoryquoteDateClose> historyquoteDateClose = historyquoteJpaRepository
        .findDateCloseByIdSecuritycurrencyAndCreateTypeFalseOrderByDateAsc(idSecuritycurrency);
    Class<? extends CalcAccessIndicator> taClass = null;
    if (taIndicator == TaIndicators.SMA) {
      taClass = SimpleMovingAverage.class;
    } else if (taIndicator == TaIndicators.EMA) {
      taClass = ExponentialMovingAverage.class;
    }

    if (shortMediumLongInputPeriod.taShortPeriod != null
        && historyquoteDateClose.size() > shortMediumLongInputPeriod.taShortPeriod) {
      taTraceIndicatorData
          .add(getTrace(taIndicator, taClass, shortMediumLongInputPeriod.taShortPeriod, historyquoteDateClose));
    }

    if (shortMediumLongInputPeriod.taMediumPeriod != null
        && historyquoteDateClose.size() > shortMediumLongInputPeriod.taMediumPeriod) {
      taTraceIndicatorData
          .add(getTrace(taIndicator, taClass, shortMediumLongInputPeriod.taMediumPeriod, historyquoteDateClose));
    }

    if (shortMediumLongInputPeriod.taLongPeriod != null
        && historyquoteDateClose.size() > shortMediumLongInputPeriod.taLongPeriod) {
      taTraceIndicatorData
          .add(getTrace(taIndicator, taClass, shortMediumLongInputPeriod.taLongPeriod, historyquoteDateClose));
    }
    return taTraceIndicatorData;
  }

  private TaTraceIndicatorData getTrace(TaIndicators taIndicator, Class<? extends CalcAccessIndicator> taClass,
      int period, List<HistoryquoteDateClose> historyquoteDateClose) {
    Constructor<? extends CalcAccessIndicator> constructor = ClassUtils.getConstructorIfAvailable(taClass, int.class,
        int.class);
    CalcAccessIndicator calcAccessIndicator = BeanUtils.instantiateClass(constructor, period,
        historyquoteDateClose.size());
    historyquoteDateClose
        .forEach(historyquote -> calcAccessIndicator.addData(historyquote.getDate(), historyquote.getClose()));

    return new TaTraceIndicatorData(taIndicator, taIndicator.name(), period, calcAccessIndicator.getTaIndicatorData());
  }

  /**
   * Check if user can get this history quotes
   *
   * @param idSecuritycurrency
   */
  private UserAccess checkUserAccess(Integer idSecuritycurrency) {
    if (currencypairJpaRepository.findById(idSecuritycurrency).isPresent()) {
      return new UserAccess(true);
    } else {

      final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();

      // Issue: 53: TODO Check if someone is using GT to get historical data. The
      // following way will certainly not work.
      /*
       * if
       * (historyquoteJpaRepository.countSecuritycurrencyForHistoryquoteAccess(user.
       * getIdTenant(), idSecuritycurrency) == 0) { throw new
       * SecurityException(GlobalConstants.STEAL_DATA_SECURITY_BREACH); }
       */
      return new UserAccess(false, securityJpaRepository
          .findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(idSecuritycurrency, user.getIdTenant()));
    }
  }

  @Override
  @Transactional
  @Modifying
  public DeleteHistoryquotesSuccess deleteHistoryquotesByCreateTypes(Integer idSecuritycurrency,
      List<Byte> historyquoteCreateTypesAsBytes) {
    historyquoteJpaRepository.getUserAndCheckSecurityAccess(idSecuritycurrency);

    DeleteHistoryquotesSuccess dhs = new DeleteHistoryquotesSuccess();
    for (Byte historyquoteCreateTypesAsByte : historyquoteCreateTypesAsBytes) {
      HistoryquoteCreateType hct = HistoryquoteCreateType.getHistoryquoteCreateType(historyquoteCreateTypesAsByte);
      switch (hct) {
      case FILLED_CLOSED_LINEAR_TRADING_DAY:
        dhs.filledLinear = historyquoteJpaRepository.deleteByIdSecuritycurrencyAndCreateType(idSecuritycurrency,
            HistoryquoteCreateType.FILLED_CLOSED_LINEAR_TRADING_DAY.getValue());
        break;
      case MANUAL_IMPORTED:
        dhs.manualImported = historyquoteJpaRepository.deleteByIdSecuritycurrencyAndCreateType(idSecuritycurrency,
            HistoryquoteCreateType.MANUAL_IMPORTED.getValue());
        break;
      default:
        throw new SecurityException(GlobalConstants.LIMIT_SECURITY_BREACH);
      }
    }
    return dhs;
  }

  private static class UserAccess {
    public boolean isCurrency;
    public Security security;

    public UserAccess(boolean isCurrency) {
      this.isCurrency = isCurrency;
    }

    public UserAccess(boolean isCurrency, Security security) {
      this.isCurrency = isCurrency;
      this.security = security;
    }

  }

  /*
   * @Override
   *
   * @Transactional // TODO not yet used. public
   * HistoryQuoteForChartWithEmptyTrace
   * getHistoryQuoteForChartWithEmptyTrace(final Integer idSecuritycurrency) {
   * HistoryQuoteForChartWithEmptyTrace historyQuoteForChartWithEmptyTrace = new
   * HistoryQuoteForChartWithEmptyTrace();
   *
   * List<IDateAndClose> dateAndClose =
   * historyquoteJpaRepository.getClosedAndMissingHistoryquoteByIdSecurity(
   * idSecuritycurrency); dateAndClose.forEach(dac ->
   * historyQuoteForChartWithEmptyTrace.add(dac.getDate(), dac.getClose()));
   * historyQuoteForChartWithEmptyTrace.completeFirstAndLastMisssing();
   *
   * return historyQuoteForChartWithEmptyTrace; }
   *
   *
   * public static class HistoryQuoteForChartWithEmptyTrace { public final
   * List<HistoryquoteDateClose> minimalChartDataWithData = new ArrayList<>();
   * public final List<HistoryquoteDateClose> minimalChartDataMissingData = new
   * ArrayList<>(); private HistoryquoteDateClose lastExistingMinimalChartData =
   * null; private HistoryquoteDateClose lastMissingStartMinimalChartData = null;
   * private boolean lastIsEmpty; private LocalDate lastEmptyDate;
   *
   * public void add(LocalDate date, Double close) { if (close == null) { if
   * (!lastIsEmpty) { // Open missing scope lastMissingStartMinimalChartData = new
   * HistoryquoteDateClose(date, lastExistingMinimalChartData != null ?
   * lastExistingMinimalChartData.close : null);
   * minimalChartDataMissingData.add(lastMissingStartMinimalChartData); }
   * lastEmptyDate = date; lastIsEmpty = true; } else { if (lastIsEmpty &&
   * !minimalChartDataMissingData.isEmpty()) { // Close missing scope if
   * (lastMissingStartMinimalChartData != null &&
   * lastMissingStartMinimalChartData.date.isEqual(lastEmptyDate)) { // Only
   * single Day is missing - > take average price of day before and after
   * lastMissingStartMinimalChartData.close =
   * (lastMissingStartMinimalChartData.close + close) / 2; } else {
   * minimalChartDataMissingData.add(new HistoryquoteDateClose(lastEmptyDate,
   * close)); } } lastIsEmpty = false; lastExistingMinimalChartData = new
   * HistoryquoteDateClose(date, close);
   *
   * } minimalChartDataWithData.add(new HistoryquoteDateClose(date, close)); }
   *
   * public void completeFirstAndLastMisssing() { if
   * (!minimalChartDataMissingData.isEmpty() &&
   * !minimalChartDataWithData.isEmpty()) { if
   * (minimalChartDataMissingData.get(0).date.isBefore(minimalChartDataWithData.
   * get(0).date)) { minimalChartDataMissingData.get(0).close =
   * minimalChartDataWithData.get(0).close; } int mIndex =
   * minimalChartDataMissingData.size() - 1; int dIndex =
   * minimalChartDataWithData.size() - 1; if
   * (minimalChartDataMissingData.get(mIndex).date.isAfter(
   * minimalChartDataWithData.get(dIndex).date)) {
   * minimalChartDataMissingData.get(mIndex).close =
   * minimalChartDataWithData.get(dIndex).close; } } } }
   *
   */

}