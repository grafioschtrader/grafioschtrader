package grafioschtrader.reports;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.descriptive.MultivariateSummaryStatistics;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import grafioschtrader.dto.CorrelationResult;
import grafioschtrader.dto.CorrelationResult.CorrelationInstrument;
import grafioschtrader.entities.CorrelationSet;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Securitycurrency;

/**
 * Report for correlation report
 *
 */
public class CorrelationReport {

  private final JdbcTemplate jdbcTemplate;
  
  public CorrelationReport(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate; 
  }
 
  public CorrelationResult calcCorrelation(CorrelationSet correlationSet) {
    TreeMap<LocalDate, double[]> closeValuesMap = loadCloseData(correlationSet);
    RealMatrix realMatrix = transFormToPercentageChange(closeValuesMap,
        correlationSet.getSecuritycurrencyList().size());

    CorrelationResult cr = new CorrelationResult(closeValuesMap.firstKey(), closeValuesMap.lastKey());
    PearsonsCorrelation corrObj = new PearsonsCorrelation(realMatrix);
    RealMatrix corr = corrObj.getCorrelationMatrix();
    CorrelationInstrument[] ci = new CorrelationInstrument[corr.getRowDimension()];
    cr.correlationInstruments = ci;
    for (int i = 0; i < corr.getRowDimension(); i++) {
      ci[i] = new CorrelationInstrument(correlationSet.getSecuritycurrencyList().get(i).getIdSecuritycurrency(),
          corr.getRow(i));
    }
    calcMultivariateSummary(realMatrix, ci);
    
    return cr;
  }

  private void calcMultivariateSummary(RealMatrix realMatrix, CorrelationInstrument[] ci) {
    var mss = new MultivariateSummaryStatistics(realMatrix.getColumnDimension(), true);
    for (int i = 0; i < realMatrix.getRowDimension(); i++) {
      mss.addValue(realMatrix.getRow(i));
    }

    for (int i = 0; i < realMatrix.getColumnDimension(); i++) {
      ci[i].maxPercentageChange = mss.getMax()[i];
      ci[i].standardDeviation = mss.getStandardDeviation()[i] * Math.sqrt(250);
    }

  }

  private RealMatrix transFormToPercentageChange(Map<LocalDate, double[]> closeValuesMap, int columns) {
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
    return new Array2DRowRealMatrix(data);
  }

  private TreeMap<LocalDate, double[]> loadCloseData(CorrelationSet cs) {
    StringBuilder qSelect = new StringBuilder("SELECT h1.date");
    StringBuilder qFrom = new StringBuilder(" FROM ");
    StringBuilder qWhere = new StringBuilder(" WHERE ");
    for (int i = 0; i < cs.getSecuritycurrencyList().size(); i++) {
      Securitycurrency<?> securitycurrency = cs.getSecuritycurrencyList().get(i);
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

    String query = qSelect.append(qFrom).append(qWhere).append(" ORDER BY h1.date").toString();
  //  System.out.println(query);
    return jdbcTemplate.query(query, new ResultSetExtractor<TreeMap<LocalDate, double[]>>() {
      @Override
      public TreeMap<LocalDate, double[]> extractData(ResultSet rs) throws SQLException, DataAccessException {
        TreeMap<LocalDate, double[]> resultCloseMap = new TreeMap<>();
        while (rs.next()) {
          var closeColumns = new double[cs.getSecuritycurrencyList().size()];
          for (int i = 0; i < cs.getSecuritycurrencyList().size(); i++) {
            closeColumns[i] = rs.getDouble(2 + i);
          }
          resultCloseMap.put(rs.getDate(1).toLocalDate(), closeColumns);
        }
        return resultCloseMap;
      }
    });
  }

}
