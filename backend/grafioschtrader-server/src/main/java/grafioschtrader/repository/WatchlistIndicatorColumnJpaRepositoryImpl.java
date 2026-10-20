package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.service.DailyLimitService;
import grafiosch.service.EntityLimitService;
import grafiosch.types.OperationType;
import grafioschtrader.config.LimitKeyConfig;
import grafioschtrader.entities.WatchlistIndicatorColumn;
import grafioschtrader.evalex.*;
import grafioschtrader.types.*;

/** Validates ownership, input availability and presentation before persisting any column. */
public class WatchlistIndicatorColumnJpaRepositoryImpl extends BaseRepositoryImpl<WatchlistIndicatorColumn>
    implements WatchlistIndicatorColumnJpaRepositoryCustom {
  @Autowired
  private WatchlistIndicatorColumnJpaRepository repository;
  @Autowired
  private TenantJpaRepository tenants;
  @Autowired
  private WatchlistJpaRepository watchlists;
  @Autowired
  private SecurityJpaRepository securities;
  @Autowired
  private CurrencypairJpaRepository currencies;
  @Autowired
  private EntityLimitService limits;
  @Autowired
  private DailyLimitService dailyLimits;
  private static final String ENTITY = WatchlistIndicatorColumn.class.getSimpleName();
  @Autowired
  private MessageSource messages;

  private User user() {
    return (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
  }

  private void lock(User user) {
    tenants.lockMonitoringTenant(user.getIdTenant())
        .orElseThrow(() -> new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH));
  }

  @Transactional
  public WatchlistIndicatorColumn saveOnlyAttributes(WatchlistIndicatorColumn input, WatchlistIndicatorColumn ignored,
      Set<Class<? extends Annotation>> levels) {
    var user = user();
    lock(user);
    if (input.getIdTenant() != null && !input.getIdTenant().equals(user.getIdTenant()))
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    var stored = input.getId() == null ? null : own(input.getId(), user.getIdTenant());
    validate(input, user.getIdTenant());
    if (stored == null) {
      checkLimit(user, 1);
      input.setUiOrder((short) (repository.findByIdTenantOrderByUiOrder(user.getIdTenant()).stream()
          .mapToInt(WatchlistIndicatorColumn::getUiOrder).max().orElse(0) + 1));
    } else
      input.setUiOrder(stored.getUiOrder());
    dailyLimits.check(user, ENTITY, 1);
    input.setIdTenant(user.getIdTenant());
    return repository.saveAndFlush(input);
  }

  private void validate(WatchlistIndicatorColumn input, Integer tenant) {
    try {
      AlertExpressionSupport.validate(input.getExpression(), EnumSet.allOf(IndicatorData.class));
      input.setResultType(AlertExpressionSupport.resultType(input.getExpression()));
    } catch (AlertExpressionException e) {
      throw new DataViolationException("expression", e.getKey(), e.getArguments());
    }
    if (AlertExpressionSupport.requiredData(input.getExpression()).contains(IndicatorData.BENCHMARK)
        && input.getIdBenchmark() == null)
      fail("id.benchmark", "WIC_BENCHMARK_REQUIRED");
    if (input.getIdBenchmark() != null && !currencies.existsById(input.getIdBenchmark()) && securities
        .findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(input.getIdBenchmark(), tenant) == null)
      fail("id.benchmark", "WIC_BENCHMARK_UNREADABLE");
    if (input.getIdWatchlist() != null
        && watchlists.findById(input.getIdWatchlist()).filter(w -> tenant.equals(w.getIdTenant())).isEmpty())
      fail("id.watchlist", "WIC_WATCHLIST_INVALID");
    if (input.getLabel() == null || input.getLabel().isBlank() || input.getLabel().length() > 40)
      fail("wic.label", "WIC_LABEL_INVALID");
    if (input.getDecimals() < 0 || input.getDecimals() > 6)
      fail("decimals", "WIC_DECIMALS_INVALID");
    if (input.getResultType() == IndicatorResultType.BOOLEAN && (input.getLowerMark() != null
        || input.getUpperMark() != null || input.getMarkColors() != IndicatorMarkColors.LOW_NEGATIVE_HIGH_POSITIVE))
      fail("lower.mark", "WIC_BOOLEAN_MARKS");
    if ((input.getLowerMark() != null && !Double.isFinite(input.getLowerMark()))
        || (input.getUpperMark() != null && !Double.isFinite(input.getUpperMark())) || (input.getLowerMark() != null
            && input.getUpperMark() != null && input.getLowerMark() >= input.getUpperMark()))
      fail("lower.mark", "WIC_MARK_ORDER");
  }

  private void checkLimit(User user, int count) {
    if (!limits.fitsWithinLimit(user, LimitKeyConfig.KEY_WATCHLIST_INDICATOR_COLUMN, null, count))
      fail("wic.label", "MAX_WATCHLIST_INDICATOR_COLUMN");
  }

  private void fail(String field, String key) {
    throw new DataViolationException(field, key, null);
  }

  private WatchlistIndicatorColumn own(Integer id, Integer tenant) {
    return repository.findByIdWatchlistIndicatorColumnAndIdTenant(id, tenant)
        .orElseThrow(() -> new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH));
  }

  @Transactional
  public int delEntityWithTenant(Integer id, Integer tenant) {
    var user = user();
    lock(user);
    if (!user.getIdTenant().equals(tenant))
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    var row = own(id, tenant);
    dailyLimits.check(user, ENTITY, 1);
    repository.delete(row);
    return 1;
  }

  @Override
  @Transactional
  public List<WatchlistIndicatorColumn> reorder(List<Integer> ids) {
    var user = user();
    lock(user);
    var rows = repository.findByIdTenantOrderByUiOrder(user.getIdTenant());
    if (ids == null || ids.size() != rows.size() || new HashSet<>(ids).size() != ids.size()
        || !new HashSet<>(ids).equals(new HashSet<>(rows.stream().map(WatchlistIndicatorColumn::getId).toList())))
      fail("wic.label", "WIC_ORDER_INVALID");
    dailyLimits.check(user, ENTITY, rows.size());
    for (var row : rows)
      row.setUiOrder((short) (ids.indexOf(row.getId()) + 1));
    repository.saveAllAndFlush(rows);
    dailyLimits.log(user.getIdUser(), ENTITY, OperationType.UPDATE, rows.size());
    return repository.findByIdTenantOrderByUiOrder(user.getIdTenant());
  }

  @Override
  @Transactional
  public List<WatchlistIndicatorColumn> createDefaults() {
    var user = user();
    lock(user);
    if (repository.countByIdTenant(user.getIdTenant()) != 0)
      fail("wic.label", "WIC_DEFAULTS_EXIST");
    checkLimit(user, 10);
    dailyLimits.check(user, ENTITY, 10);
    String[] expressions = { "RSI(14)", "PRICE / SMA(50) * 100 - 100", "PRICE / SMA(200) * 100 - 100",
        "SMA(50) > SMA(200)", "PRICE / CLOSE(126) * 100 - 100", "PRICE / HIGHEST(250) * 100 - 100", "VOLA(250)",
        "ATR(14) / PRICE * 100", "ZSCORE(20)", "DIVYIELD()" };
    var rows = new ArrayList<WatchlistIndicatorColumn>();
    for (int i = 0; i < expressions.length; i++) {
      var row = new WatchlistIndicatorColumn();
      row.setIdTenant(user.getIdTenant());
      row.setLabel(messages.getMessage("WIC_DEFAULT_" + (i + 1), null, LocaleContextHolder.getLocale()));
      row.setExpression(expressions[i]);
      row.setUiOrder((short) (i + 1));
      row.setDecimals((byte) (i == 3 ? 0 : i >= 7 ? 2 : 1));
      if (i == 0 || i == 8) {
        row.setLowerMark(i == 0 ? 30.0 : -2.0);
        row.setUpperMark(i == 0 ? 70.0 : 2.0);
        row.setMarkColors(IndicatorMarkColors.LOW_POSITIVE_HIGH_NEGATIVE);
      }
      validate(row, user.getIdTenant());
      rows.add(row);
    }
    var saved = repository.saveAllAndFlush(rows);
    dailyLimits.log(user.getIdUser(), ENTITY, OperationType.ADD, rows.size());
    return saved;
  }
}
