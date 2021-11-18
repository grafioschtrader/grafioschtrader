package grafioschtrader.reports;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.User;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
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

  public static TreeMap<LocalDate, double[]> loadCloseData(JdbcTemplate jdbcTemplate,
      List<Securitycurrency<?>> securitycurrencyList, SamplingPeriodType samplingPeriod, LocalDate dateFrom,
      LocalDate dateTo) {
    StringBuilder qSelect = new StringBuilder("SELECT h0.date");
    StringBuilder qFrom = new StringBuilder(" FROM ");
    StringBuilder qWhere = new StringBuilder(WHERE_WORD);

    for (int i = 0; i < securitycurrencyList.size(); i++) {
      Securitycurrency<?> securitycurrency = securitycurrencyList.get(i);
      qSelect.append(", h" + i + ".close AS c" + i);
      if (i == 0) {
        qFrom.append(Historyquote.TABNAME + " h" + i);
      } else {
        qFrom.append(" JOIN " + Historyquote.TABNAME + " h" + i + " ON " + "h0.date=h" + i + ".date");
      }
      if (i > 0) {
        qWhere.append(" AND ");
      }
      qWhere.append("h" + i + ".id_securitycurrency=" + securitycurrency.getIdSecuritycurrency());
    }
    addDateBoundry(dateFrom, qWhere, ">");
    addDateBoundry(dateTo, qWhere, "<");
    StringBuilder qGroup = addMonthYearGroupBy(samplingPeriod);

    String query = qSelect.append(qFrom).append(WHERE_WORD.endsWith(qWhere.toString()) ? "" : qWhere).append(qGroup)
        .append(" ORDER BY h0.date").toString();
    // System.out.println(query);
    return jdbcTemplate.query(query, new ResultSetExtractor<TreeMap<LocalDate, double[]>>() {
      @Override
      public TreeMap<LocalDate, double[]> extractData(ResultSet rs) throws SQLException, DataAccessException {
        TreeMap<LocalDate, double[]> resultCloseMap = new TreeMap<>();
        while (rs.next()) {
          var closeColumns = new double[securitycurrencyList.size()];
          for (int i = 0; i < securitycurrencyList.size(); i++) {
            closeColumns[i] = rs.getDouble(2 + i);
          }
          resultCloseMap.put(rs.getDate(1).toLocalDate(), closeColumns);
        }
        return resultCloseMap;
      }
    });
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

  public static double[][] transformToPercentageChange(Map<LocalDate, double[]> closeValuesMap, int columns) {
    double[][] data = new double[closeValuesMap.size() - 1][columns];
    double[] prevClose = null;
    int l = 0;
    for (double[] close : closeValuesMap.values()) {
      if (prevClose == null) {
        prevClose = close;
      } else {
        for (int i = 0; i < close.length; i++) {
          data[l][i] = (close[i] / prevClose[i] - 1) * 100.0;
        }
        prevClose = close;
        l++;
      }
    }
    return data;
  }

}
