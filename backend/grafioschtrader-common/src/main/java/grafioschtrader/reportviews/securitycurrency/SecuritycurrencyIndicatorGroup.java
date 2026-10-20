package grafioschtrader.reportviews.securitycurrency;

import java.time.LocalDate;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.entities.WatchlistIndicatorColumn;
import grafioschtrader.types.FundamentalState;
import grafioschtrader.types.IndicatorCellStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** The instrument rows and unrounded, statically typed indicator cells of one request. */
@Schema(description = "Watchlist indicators with column definitions and the availability of each cell")
public class SecuritycurrencyIndicatorGroup extends SecuritycurrencyGroup {
  public List<WatchlistIndicatorColumn> columns;
  public Map<Integer, Map<Integer, IndicatorCell>> cells = new LinkedHashMap<>();
  public Map<Integer, BenchmarkInfo> benchmarks = new HashMap<>();
  @Schema(description = "Per column the fundamental variables its expression reads; columns without any are absent")
  public Map<Integer, List<String>> columnFundamentalVariables = new HashMap<>();
  @Schema(description = """
      Per instrument and fundamental variable where its bound value comes from, for the cell tooltip of a column that
      reads fundamentals. Only instruments with at least one bound value appear.""")
  public Map<Integer, Map<String, FundamentalInfo>> fundamentalInfo = new HashMap<>();
  public boolean tenantHasColumns;

  public SecuritycurrencyIndicatorGroup(SecuritycurrencyGroup group, List<WatchlistIndicatorColumn> columns) {
    super(group.securityPositionList, group.currencypairPositionList, group.lastTimestamp, group.idWatchlist);
    this.columns = columns;
  }

  @Schema(description = "Unrounded number or boolean when OK, otherwise a null value with its cause")
  public record IndicatorCell(Object value, IndicatorCellStatus status,
      @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate lastCommonDate) {
  }

  @Schema(description = "Readable benchmark identity and quotation currency for the column tooltip")
  public record BenchmarkInfo(String name, String currency) {
  }

  @Schema(description = """
      Origin of a bound fundamental value: the source, the basis (TTM for trailing twelve months, FY for the last fiscal
      year, null for a value without basis such as the share count), the end of its period, its grade, the day it was
      fetched, and the exchange rate with the currency of the stored value when it was converted into the currency of
      the security; rate and currency are null when no conversion took place.""")
  public record FundamentalInfo(String source, String basis,
      @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate periodEnd, FundamentalState state,
      @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate fetchedDate, Double rate, String currency) {
  }
}
