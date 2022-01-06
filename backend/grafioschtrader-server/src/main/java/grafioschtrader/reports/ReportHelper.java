package grafioschtrader.reports;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.User;
import grafioschtrader.entities.projection.CurrencyCount;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.types.SamplingPeriodType;

public abstract class ReportHelper {
  private final static String WHERE_WORD = " WHERE ";

  public static void loadUntilDateHistoryquotes(final HistoryquoteJpaRepository historyquoteJpaRepository,
      DateTransactionCurrencypairMap dateCurrencyMap) {
    if (!dateCurrencyMap.isUntilDateEqualNowOrAfter() && !dateCurrencyMap.isUntilDateDataLoaded()) {
      final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
      loadUntilDateHistoryquotesWithoutCheck(user.getIdTenant(), historyquoteJpaRepository, dateCurrencyMap);
    }
  }

  public static void loadUntilDateHistoryquotes(final Integer idTenant,
      final HistoryquoteJpaRepository historyquoteJpaRepository, DateTransactionCurrencypairMap dateCurrencyMap) {
    if (!dateCurrencyMap.isUntilDateEqualNowOrAfter() && !dateCurrencyMap.isUntilDateDataLoaded()) {
      loadUntilDateHistoryquotesWithoutCheck(idTenant, historyquoteJpaRepository, dateCurrencyMap);
    }
  }

  public static void loadUntilDateHistoryquotesWithoutCheck(final Integer idTenant,
      final HistoryquoteJpaRepository historyquoteJpaRepository, DateTransactionCurrencypairMap dateCurrencyMap) {
    List<Object[]> currencyList = historyquoteJpaRepository.getUsedCurrencyHistoryquotesByIdTenantAndDate(idTenant,
        dateCurrencyMap.getUntilDate());
    dateCurrencyMap.putToDateFromCurrencyMap(currencyList);
    dateCurrencyMap.untilDateDataIsLoaded();
  }

  public static ClosePricesCurrencyClose loadCloseData(JdbcTemplate jdbcTemplate,
      CurrencypairJpaRepository currencypairJpaRepository, List<Securitycurrency<?>> securitycurrencyList,
      SamplingPeriodType samplingPeriod, LocalDate dateFrom, LocalDate dateTo, boolean adjustToSingleCurrency) {
    CurrencyRequired cr = null;
    StringBuilder qSelect = new StringBuilder("SELECT h0.date");
    StringBuilder qFrom = new StringBuilder(" FROM ");
    StringBuilder qWhere = new StringBuilder(WHERE_WORD);
    List<Integer> securityCurrencyIds = securitycurrencyList.stream().map(sc -> sc.getIdSecuritycurrency())
        .collect(Collectors.toList());

    if (adjustToSingleCurrency) {
      cr = adjustToSingleCurrency(currencypairJpaRepository, securitycurrencyList);
      cr.carList.forEach(cfa -> {
        securityCurrencyIds.add(cfa.idSecuritycurrency);
        cfa.column = securityCurrencyIds.size() - 1;
      });
    }

    for (int i = 0; i < securityCurrencyIds.size(); i++) {
      qSelect.append(", h" + i + ".close AS c" + i);
      if (i == 0) {
        qFrom.append(Historyquote.TABNAME + " h" + i);
      } else {
        qFrom.append(" JOIN " + Historyquote.TABNAME + " h" + i + " ON " + "h0.date=h" + i + ".date");
      }
      if (i > 0) {
        qWhere.append(" AND ");
      }
      qWhere.append("h" + i + ".id_securitycurrency=" + securityCurrencyIds.get(i));
    }
    addDateBoundry(dateFrom, qWhere, ">");
    addDateBoundry(dateTo, qWhere, "<");
    StringBuilder qGroup = addMonthYearGroupBy(samplingPeriod);

    String query = qSelect.append(qFrom).append(WHERE_WORD.endsWith(qWhere.toString()) ? "" : qWhere).append(qGroup)
        .append(" ORDER BY h0.date").toString();
   // System.out.println(query);
    return new ClosePricesCurrencyClose(getQueryDateCloseAsTreeMap(jdbcTemplate, query, securityCurrencyIds.size()),
        cr);
  }

  public static void adjustCloseToSameCurrency(List<Securitycurrency<?>> securitycurrencyList,
      ClosePricesCurrencyClose cpcc) {
    if (cpcc.currencyRequired != null && cpcc.currencyRequired.needCurrencyAdjustment()) {
      adjustCloseToSameCurrency(securitycurrencyList, cpcc, cpcc.currencyRequired);
    }
  }

  private static void adjustCloseToSameCurrency(List<Securitycurrency<?>> securitycurrencyList,
      ClosePricesCurrencyClose cpcc, CurrencyRequired cr) {
    for (int col = 0; col < securitycurrencyList.size(); col++) {
      if (securitycurrencyList.get(col) instanceof Security
          && !((Security) securitycurrencyList.get(col)).getCurrency().equals(cpcc.currencyRequired.adjustCurrency)) {
        Security s = (Security) securitycurrencyList.get(col);
        CurrencyAvailableRequired car = cr.get2ndCurrency(s.getCurrency());
        for (double[] closeRow : cpcc.dateCloseTree.values()) {
          closeRow[col] *= cr.isAdjustCurrencyEqualsFromCurrency(car) ? 1.0 / closeRow[car.column]
              : closeRow[car.column];
        }
      }
    }
  }

  public static double[][] transformToPercentageChange(Map<LocalDate, double[]> closeValuesMap, int columns) {
    double[][] data = new double[closeValuesMap.size() - 1][columns];
    double[] prevCloseRow = null;
    int l = 0;
    for (double[] closeRow : closeValuesMap.values()) {
      if (prevCloseRow == null) {
        prevCloseRow = closeRow;
      } else {
        for (int colCounter = 0; colCounter < columns; colCounter++) {
          data[l][colCounter] = (closeRow[colCounter] / prevCloseRow[colCounter] - 1) * 100.0;
        }
        prevCloseRow = closeRow;
        l++;
      }
    }
    return data;
  }

  private static TreeMap<LocalDate, double[]> getQueryDateCloseAsTreeMap(JdbcTemplate jdbcTemplate, String query,
      int columns) {
    return jdbcTemplate.query(query, new ResultSetExtractor<TreeMap<LocalDate, double[]>>() {
      @Override
      public TreeMap<LocalDate, double[]> extractData(ResultSet rs) throws SQLException, DataAccessException {
        TreeMap<LocalDate, double[]> resultCloseMap = new TreeMap<>();
        while (rs.next()) {
          var closeColumns = new double[columns];
          for (int i = 0; i < columns; i++) {
            closeColumns[i] = rs.getDouble(2 + i);
          }
          resultCloseMap.put(rs.getDate(1).toLocalDate(), closeColumns);
        }
        return resultCloseMap;
      }
    });
  }

  private static CurrencyRequired adjustToSingleCurrency(CurrencypairJpaRepository currencypairJpaRepository,
      List<Securitycurrency<?>> securitycurrencyList) {
    Map<String, Integer> requiredCurrenciesSet = new HashMap<>();
    CurrencyRequired cr = new CurrencyRequired();
    for (int i = 0; i < securitycurrencyList.size(); i++) {
      Securitycurrency<?> sc = securitycurrencyList.get(i);
      if (sc instanceof Security) {
        requiredCurrenciesSet.merge(((Security) sc).getCurrency(), 1, Integer::sum);
      } else {
        Currencypair cp = (Currencypair) sc;
        cr.carList.add(
            new CurrencyAvailableRequired(i, cp.getIdSecuritycurrency(), cp.getFromCurrency(), cp.getToCurrency()));
      }
    }
    determineMissingCurrencyPairs(currencypairJpaRepository, requiredCurrenciesSet, cr);
    return cr;
  }

  private static void determineMissingCurrencyPairs(CurrencypairJpaRepository currencypairJpaRepository,
      Map<String, Integer> requiredCurrenciesSet, CurrencyRequired cr) {

    if (requiredCurrenciesSet.size() > 1) {
      List<CurrencyCount> cc = currencypairJpaRepository.countCurrencyGroupByCurrency(requiredCurrenciesSet.keySet());
      cr.adjustCurrency = cc.get(0).getCurrency();
      List<String> cpairList = new ArrayList<>();
      for (Map.Entry<String, Integer> entry : requiredCurrenciesSet.entrySet()) {
        if (cr.adjustCurrency.equals(entry.getKey())) {
          continue;
        } else {
          Optional<CurrencyAvailableRequired> loadedCurrencyOpt = cr.containsCurrencypairIgnoreFromTo(cr.adjustCurrency,
              entry.getKey());
          if (loadedCurrencyOpt.isEmpty()) {
            cr.carList.add(new CurrencyAvailableRequired(-1, -1, cr.adjustCurrency, entry.getKey()));
            cpairList.add(cr.adjustCurrency + entry.getKey());
          } else {
            cr.carList.add(loadedCurrencyOpt.get());
          }
        }
      }
      completeCurrencyAvailableRequired(cr, currencypairJpaRepository.getPairsByFromAndToCurrency(cpairList));
      createMissingCurrencypair(currencypairJpaRepository, cr);
    }
  }

  private static void completeCurrencyAvailableRequired(CurrencyRequired cr, List<Currencypair> possibleCpList) {
    for (Currencypair currencypair : possibleCpList) {
      Optional<CurrencyAvailableRequired> carOpt = cr.containsCurrencypairIgnoreFromTo(currencypair.getFromCurrency(),
          currencypair.getToCurrency());
      if (carOpt.isPresent() && carOpt.get().idSecuritycurrency.equals(-1)) {
        CurrencyAvailableRequired car = carOpt.get();
        car.adjust(currencypair.getIdSecuritycurrency(), currencypair.getFromCurrency(), currencypair.getToCurrency());
      }
    }
  }

  
  private static void createMissingCurrencypair(CurrencypairJpaRepository currencypairJpaRepository, CurrencyRequired cr) {
    for(CurrencyAvailableRequired car: cr.getMissingCurrencypair()) {
      Currencypair cp = currencypairJpaRepository.createNonExistingCurrencypair(car.formCurrency, car.toCurrency, false);
      car.adjust(cp.getIdSecuritycurrency(), cp.getFromCurrency(), cp.getToCurrency());
    }
  }
  
  private static void addDateBoundry(LocalDate date, StringBuilder qWhere, String lessMore) {
    if (date != null) {
      qWhere.append(" AND h0.date " + lessMore + "= \"" + date + "\" ");
    }
  }

  private static StringBuilder addMonthYearGroupBy(SamplingPeriodType samplingPeriodType) {
    StringBuilder qGroup = new StringBuilder("");
    if (samplingPeriodType != SamplingPeriodType.DAILY_RETURNS) {
      qGroup.append(" GROUP BY YEAR(h0.date)");
      if (samplingPeriodType == SamplingPeriodType.MONTHLY_RETURNS) {
        qGroup.append(", MONTH(h0.date)");
      }
    }
    return qGroup;
  }

  public static class CurrencyAvailableRequired {
    public int column;
    public Integer idSecuritycurrency;
    public String formCurrency;
    public String toCurrency;

    public CurrencyAvailableRequired(int column, Integer idSecuritycurrency, String formCurrency, String toCurrency) {
      this.column = column;
      this.idSecuritycurrency = idSecuritycurrency;
      this.formCurrency = formCurrency;
      this.toCurrency = toCurrency;
    }

    public void adjust(Integer idSecuritycurrency, String formCurrency, String toCurrency) {
      this.idSecuritycurrency = idSecuritycurrency;
      this.formCurrency = formCurrency;
      this.toCurrency = toCurrency;
    }

    @Override
    public String toString() {
      return "CurrencyAvailableRequired [column=" + column + ", idSecuritycurrency=" + idSecuritycurrency
          + ", formCurrency=" + formCurrency + ", toCurrency=" + toCurrency + "]";
    }
  }

  public static class CurrencyRequired {
    public String adjustCurrency;
    public final List<CurrencyAvailableRequired> carList = new ArrayList<>();

    public CurrencyRequired() {
    }

    public CurrencyRequired(String adjustCurrency) {
      this.adjustCurrency = adjustCurrency;
    }

    public Optional<CurrencyAvailableRequired> containsCurrencypairIgnoreFromTo(String c1, String c2) {
      return carList.stream().filter(cp -> cp.formCurrency.equals(c1) && cp.toCurrency.equals(c2)
          || cp.formCurrency.equals(c2) && cp.toCurrency.equals(c1)).findFirst();
    }

    public boolean isAdjustCurrencyEqualsFromCurrency(CurrencyAvailableRequired car) {
      return car.formCurrency.equals(adjustCurrency);
    }

    public CurrencyAvailableRequired get2ndCurrency(String currency) {
      return carList.stream().filter(cp -> cp.formCurrency.equals(currency) || cp.toCurrency.equals(currency)).findAny()
          .orElse(null);
    }

    public boolean needCurrencyAdjustment() {
      return adjustCurrency != null;
    }
    
    public List<CurrencyAvailableRequired> getMissingCurrencypair() {
      return carList.stream().filter(car -> car.idSecuritycurrency.equals(-1)).collect(Collectors.toList());
    }
    
  }

  public static class ClosePricesCurrencyClose {
    public final TreeMap<LocalDate, double[]> dateCloseTree;
    public CurrencyRequired currencyRequired;

    public ClosePricesCurrencyClose(TreeMap<LocalDate, double[]> dateCloseTree, CurrencyRequired currencyRequired) {
      this.dateCloseTree = dateCloseTree;
      this.currencyRequired = currencyRequired;
    }

  }

}
