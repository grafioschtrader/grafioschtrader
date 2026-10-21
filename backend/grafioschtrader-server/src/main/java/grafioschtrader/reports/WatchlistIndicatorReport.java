package grafioschtrader.reports;

import static grafioschtrader.types.IndicatorCellStatus.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.dto.IDateCloseHighLow;
import grafioschtrader.entities.*;
import grafioschtrader.evalex.*;
import grafioschtrader.evalex.IndicatorSeries.DividendAmount;
import grafioschtrader.reportviews.securitycurrency.*;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyIndicatorGroup.*;
import grafioschtrader.repository.*;
import grafioschtrader.repository.SecurityFundamentalJpaRepository.SelectedFundamental;
import grafioschtrader.types.FundamentalMetric;
import grafioschtrader.types.FundamentalSource;
import grafioschtrader.types.FundamentalState;
import grafioschtrader.types.IndicatorCellStatus;

/** Batch-loads projected inputs before sequential, isolated evaluation of every indicator cell. */
@Component
public class WatchlistIndicatorReport {
  private static final Logger LOG = LoggerFactory.getLogger(WatchlistIndicatorReport.class);
  @Autowired
  private WatchlistIndicatorColumnJpaRepository columns;
  @Autowired
  private HistoryquoteJpaRepository history;
  @Autowired
  private DividendJpaRepository dividends;
  @Autowired
  private SecurityJpaRepository securities;
  @Autowired
  private CurrencypairJpaRepository currencies;
  @Autowired
  private SecurityFundamentalJpaRepository fundamentals;

  /** Expression variable bound from each metric; SPS takes the trailing value and falls back to the fiscal year. */
  private static final Map<FundamentalMetric, String> VARIABLE_OF_METRIC = Map.of(FundamentalMetric.EPS_TTM, "EPS",
      FundamentalMetric.EPS_FY, "EPS_FY", FundamentalMetric.BVPS, "BVPS", FundamentalMetric.SPS_TTM, "SPS",
      FundamentalMetric.SPS_FY, "SPS", FundamentalMetric.SHARES, "SHARES", FundamentalMetric.NET_DEBT, "NET_DEBT");

  public SecuritycurrencyIndicatorGroup calculate(SecuritycurrencyGroup group, Integer tenant, LocalDate today) {
    var all = columns.findByIdTenantOrderByUiOrder(tenant);
    var selected = all.stream().filter(c -> c.getIdWatchlist() == null || c.getIdWatchlist().equals(group.idWatchlist))
        .toList();
    var result = new SecuritycurrencyIndicatorGroup(group, selected);
    result.tenantHasColumns = !all.isEmpty();
    if (selected.isEmpty())
      return result;
    var inputs = new LinkedHashMap<Integer, Input>();
    group.securityPositionList.forEach(p -> inputs.put(p.securitycurrency.getId(), input(p.securitycurrency)));
    group.currencypairPositionList.forEach(p -> inputs.put(p.securitycurrency.getId(), input(p.securitycurrency)));
    Map<Integer, Set<IndicatorData>> requirements = new HashMap<>();
    selected.forEach(c -> requirements.put(c.getId(), AlertExpressionSupport.requiredData(c.getExpression())));
    var needed = EnumSet.noneOf(IndicatorData.class);
    requirements.values().forEach(needed::addAll);
    var ids = new HashSet<>(inputs.keySet());
    for (var column : selected) {
      if (column.getIdBenchmark() == null)
        continue;
      var security = securities
          .findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(column.getIdBenchmark(), tenant);
      Securitycurrency<?> benchmark = security != null ? security
          : currencies.findById(column.getIdBenchmark()).orElse(null);
      if (benchmark != null) {
        ids.add(benchmark.getId());
        result.benchmarks.put(benchmark.getId(), new BenchmarkInfo(benchmark.getName(), currency(benchmark)));
      }
    }
    var series = needed.contains(IndicatorData.SERIES) || needed.contains(IndicatorData.BENCHMARK)
        ? loadSeries(new ArrayList<>(ids), today)
        : Map.<Integer, List<IDateCloseHighLow>>of();
    Map<Integer, List<DividendAmount>> amounts = new HashMap<>();
    Set<Integer> known = new HashSet<>();
    if (needed.contains(IndicatorData.DIVIDENDS))
      loadDividends(inputs, selected, today, amounts, known);
    Map<Integer, Map<String, Double>> fundamentalValues = Map.of();
    if (needed.contains(IndicatorData.FUNDAMENTALS)) {
      fundamentalValues = loadFundamentals(inputs, today, result.fundamentalInfo);
      for (var column : selected)
        if (requirements.get(column.getId()).contains(IndicatorData.FUNDAMENTALS))
          result.columnFundamentalVariables.put(column.getId(),
              AlertExpressionSupport.fundamentalVariables(column.getExpression()));
    }
    if (!series.isEmpty()) {
      group.securityPositionList.forEach(p -> p.youngestHistoryDate = newest(series.get(p.securitycurrency.getId())));
      group.currencypairPositionList
          .forEach(p -> p.youngestHistoryDate = newest(series.get(p.securitycurrency.getId())));
    }
    var logged = new HashSet<Integer>();
    for (var entry : inputs.entrySet()) {
      var cells = new LinkedHashMap<Integer, IndicatorCell>();
      var sets = new HashMap<Integer, IndicatorFunctionSet>();
      for (var column : selected) {
        int benchmark = column.getIdBenchmark() == null ? 0 : column.getIdBenchmark();
        var functions = sets.computeIfAbsent(benchmark,
            _ -> new IndicatorFunctionSet(new IndicatorSeries(series.getOrDefault(entry.getKey(), List.of()),
                series.getOrDefault(benchmark, List.of()), amounts.getOrDefault(entry.getKey(), List.of()),
                known.contains(entry.getKey()), entry.getValue().security(), today,
                entry.getValue().prices().get("PRICE"))));
        cells.put(column.getId(), evaluate(column, requirements.get(column.getId()), functions, entry.getValue(),
            fundamentalValues.getOrDefault(entry.getKey(), Map.of()), result.benchmarks.containsKey(benchmark), logged));
      }
      result.cells.put(entry.getKey(), cells);
    }
    return result;
  }

  private LocalDate newest(List<IDateCloseHighLow> rows) {
    return rows == null || rows.isEmpty() ? null : rows.getLast().getDate();
  }

  private Map<Integer, List<IDateCloseHighLow>> loadSeries(List<Integer> ids, LocalDate today) {
    if (ids.isEmpty())
      return Map.of();
    var rows = history.findDateCloseHighLowByIdsAndDateRange(ids, today.minusDays(1920), today).stream()
        .map(row -> (IDateCloseHighLow) HistoryquoteJpaRepository.IndicatorQuote.of(row))
        .collect(Collectors.groupingBy(IDateCloseHighLow::getIdSecuritycurrency));
    var shortIds = ids.stream()
        .filter(id -> rows.getOrDefault(id, List.of()).size() < AlertExpressionSupport.SERIES_OBSERVATIONS).toList();
    if (!shortIds.isEmpty()) {
      shortIds.forEach(rows::remove);
      history.findNewestDateCloseHighLowByIds(shortIds, today).stream().map(HistoryquoteJpaRepository.IndicatorQuote::of)
          .forEach(r -> rows.computeIfAbsent(r.getIdSecuritycurrency(), _ -> new ArrayList<>()).add(r));
    }
    rows.replaceAll((_, list) -> List
        .copyOf(list.subList(Math.max(0, list.size() - AlertExpressionSupport.SERIES_OBSERVATIONS), list.size())));
    return rows;
  }

  private void loadDividends(Map<Integer, Input> inputs, List<WatchlistIndicatorColumn> selected, LocalDate today,
      Map<Integer, List<DividendAmount>> amounts, Set<Integer> known) {
    var ids = inputs.entrySet().stream().filter(e -> e.getValue().security()).map(Map.Entry::getKey).toList();
    if (ids.isEmpty())
      return;
    inputs.forEach((id, input) -> {
      if (input.dividendConnector())
        known.add(id);
    });
    known.addAll(dividends.findIdsWithDividendData(ids, today));
    var from = selected.stream().map(c -> AlertExpressionSupport.dividendFrom(c.getExpression(), today))
        .min(LocalDate::compareTo).orElse(today);
    for (var row : dividends.findByIdsAndExDateRangeWithRate(ids, from, today)) {
      Double converted = row.getExchangeRate() == null ? null : row.getAmountAdjusted() * row.getExchangeRate();
      if (row.getAmountAdjusted() < 0 || (row.getExchangeRate() != null && row.getExchangeRate() <= 0))
        converted = -1.0;
      amounts.computeIfAbsent(row.getIdSecuritycurrency(), _ -> new ArrayList<>())
          .add(new DividendAmount(row.getExDate(), converted));
    }
  }

  /**
   * Loads the selected fundamental rows of the securities in one query and converts them into the currency of each
   * security with the newest close of the currency pair. A value without exchange rate stays unbound. The origin of
   * every bound value goes into {@code info} for the cell tooltip.
   *
   * @return per security the bound values by variable name
   */
  private Map<Integer, Map<String, Double>> loadFundamentals(Map<Integer, Input> inputs, LocalDate today,
      Map<Integer, Map<String, FundamentalInfo>> info) {
    var ids = inputs.entrySet().stream().filter(e -> e.getValue().security()).map(Map.Entry::getKey).toList();
    Map<Integer, Map<String, Double>> values = new HashMap<>();
    if (ids.isEmpty())
      return values;
    var rows = new ArrayList<>(fundamentals.findSelectedByIds(ids, today));
    // The trailing revenue per share comes first, so that SPS falls back to the fiscal year only without it.
    rows.sort(Comparator.comparingInt(SelectedFundamental::getMetric));
    for (var row : rows) {
      var metric = FundamentalMetric.getByValue(row.getMetric().byteValue());
      String variable = VARIABLE_OF_METRIC.get(metric);
      var bound = values.computeIfAbsent(row.getIdSecuritycurrency(), _ -> new HashMap<>());
      if (variable == null || row.getExchangeRate() == null || row.getValue() == null || bound.containsKey(variable))
        continue;
      double value = row.getValue() * row.getExchangeRate();
      if (!Double.isFinite(value))
        continue;
      bound.put(variable, value);
      boolean converted = row.getExchangeRate() != 1.0;
      info.computeIfAbsent(row.getIdSecuritycurrency(), _ -> new HashMap<>()).put(variable,
          new FundamentalInfo(sourceName(row.getSource()), basis(metric), row.getPeriodEnd(),
              FundamentalState.getByValue(row.getState().byteValue()),
              row.getFetchedTime() == null ? null : row.getFetchedTime().toLocalDate(),
              converted ? row.getExchangeRate() : null, converted ? row.getCurrency() : null));
    }
    return values;
  }

  private static String sourceName(Integer source) {
    String name = FundamentalSource.getByValue(source.byteValue()).name();
    return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
  }

  private static String basis(FundamentalMetric metric) {
    return switch (metric) {
    case EPS_TTM, SPS_TTM -> "TTM";
    case EPS_FY, SPS_FY -> "FY";
    default -> null;
    };
  }

  private IndicatorCell evaluate(WatchlistIndicatorColumn column, Set<IndicatorData> needed,
      IndicatorFunctionSet functions, Input input, Map<String, Double> fundamentalValues, boolean benchmarkPresent,
      Set<Integer> logged) {
    LocalDate common = null;
    try {
      if (needed.contains(IndicatorData.BENCHMARK)) {
        if (!benchmarkPresent)
          throw new IndicatorUnavailableException(NO_BENCHMARK);
        common = functions.lastCommonDate();
      }
      if (input.noMarketValue() && (needed.contains(IndicatorData.SERIES) || needed.contains(IndicatorData.BENCHMARK)))
        throw new IndicatorUnavailableException(NO_HISTORY);
      var expression = AlertExpressionSupport.create(column.getExpression(), functions);
      for (String variable : expression.getUsedVariables()) {
        String name = variable.toUpperCase(Locale.ROOT);
        boolean fundamental = AlertExpressionSupport.FUNDAMENTAL_VARIABLES.contains(name);
        Double value = fundamental ? fundamentalValues.get(name) : input.prices().get(name);
        if (value == null)
          throw new IndicatorUnavailableException(fundamental ? NO_FUNDAMENTAL : NO_PRICE);
        if (!Double.isFinite(value))
          throw new IndicatorUnavailableException(INVALID_DATA);
        expression.with(variable, BigDecimal.valueOf(value));
      }
      var value = expression.evaluate();
      if (value.isBooleanValue())
        return new IndicatorCell(value.getBooleanValue(), OK, common);
      double number = value.getNumberValue().doubleValue();
      return new IndicatorCell(Double.isFinite(number) ? number : null, Double.isFinite(number) ? OK : ARITHMETIC,
          common);
    } catch (Exception e) {
      IndicatorCellStatus status = ERROR;
      for (Throwable cause = e; cause != null; cause = cause.getCause()) {
        if (cause instanceof IndicatorUnavailableException unavailable) {
          status = unavailable.getStatus();
          break;
        }
        if (cause instanceof ArithmeticException
            || cause instanceof com.ezylang.evalex.EvaluationException && "Division by zero".equals(cause.getMessage()))
          status = ARITHMETIC;
      }
      if (status == ERROR && logged.add(column.getId()))
        LOG.warn("Indicator column {}: {}", column.getId(), column.getExpression(), e);
      return new IndicatorCell(null, status, common);
    }
  }

  private record Input(boolean security, boolean noMarketValue, boolean dividendConnector, Map<String, Double> prices) {
  }

  private Input input(Securitycurrency<?> instrument) {
    var prices = new HashMap<String, Double>();
    prices.put("PRICE", instrument.getSLast());
    prices.put("PREVCLOSE", instrument.getSPrevClose());
    prices.put("OPEN", instrument.getSOpen());
    prices.put("HIGH", instrument.getSHigh());
    prices.put("LOW", instrument.getSLow());
    if (instrument instanceof Security security) {
      prices.put("VOLUME", security.getSVolume() == null ? null : security.getSVolume().doubleValue());
      return new Input(true, security.getStockexchange().isNoMarketValue(), security.getIdConnectorDividend() != null,
          prices);
    }
    return new Input(false, false, false, prices);
  }

  private String currency(Securitycurrency<?> instrument) {
    return instrument instanceof Security security ? security.getCurrency()
        : ((Currencypair) instrument).getToCurrency();
  }
}
