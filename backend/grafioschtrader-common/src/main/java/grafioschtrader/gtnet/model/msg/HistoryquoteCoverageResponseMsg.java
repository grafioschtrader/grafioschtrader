package grafioschtrader.gtnet.model.msg;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response payload for historical price coverage queries from GTNet peers.
 *
 * Contains coverage metadata (min/max dates, record counts) for each queried instrument without
 * the actual price data. This lightweight response enables efficient peer selection based on
 * which peer has the longest historical coverage.
 *
 * @see grafioschtrader.gtnet.GTNetMessageCodeType#GT_NET_HISTORYQUOTE_COVERAGE_RESPONSE_S
 */
@Schema(description = """
    Response payload for historical price coverage queries. Contains coverage metadata
    (min/max dates, record counts) for each queried instrument without the actual price data.""")
public class HistoryquoteCoverageResponseMsg {

  @Schema(description = "Coverage information for securities (keyed by ISIN:currency)")
  public List<InstrumentCoverageDTO> securities = new ArrayList<>();

  @Schema(description = "Coverage information for currency pairs (keyed by fromCurrency:toCurrency)")
  public List<InstrumentCoverageDTO> currencypairs = new ArrayList<>();

  public HistoryquoteCoverageResponseMsg() {
  }

  /**
   * Returns the total number of instruments with coverage data in this response.
   */
  @JsonIgnore
  public int getTotalCount() {
    return (securities != null ? securities.size() : 0) + (currencypairs != null ? currencypairs.size() : 0);
  }

  /**
   * Returns the number of instruments that have coverage data available.
   */
  @JsonIgnore
  public int getAvailableCount() {
    int count = 0;
    if (securities != null) {
      count += securities.stream().filter(InstrumentCoverageDTO::isAvailable).count();
    }
    if (currencypairs != null) {
      count += currencypairs.stream().filter(InstrumentCoverageDTO::isAvailable).count();
    }
    return count;
  }

  /**
   * Checks if this response is empty.
   */
  @JsonIgnore
  public boolean isEmpty() {
    return getTotalCount() == 0;
  }

  /**
   * Builds a map from instrument key to coverage data for efficient lookup.
   */
  @JsonIgnore
  public Map<String, InstrumentCoverageDTO> buildCoverageMap() {
    Map<String, InstrumentCoverageDTO> map = new HashMap<>();
    if (securities != null) {
      for (InstrumentCoverageDTO coverage : securities) {
        map.put(coverage.getKey(), coverage);
      }
    }
    if (currencypairs != null) {
      for (InstrumentCoverageDTO coverage : currencypairs) {
        map.put(coverage.getKey(), coverage);
      }
    }
    return map;
  }

  /**
   * Coverage metadata for a single instrument.
   */
  @Schema(description = """
      Coverage metadata for a single instrument. Contains the date range and record count
      for historical price data available from this GTNet peer.""")
  public static class InstrumentCoverageDTO {

    @Schema(description = "ISIN for securities (null for currency pairs)")
    private String isin;

    @Schema(description = "Currency code for securities, or fromCurrency for currency pairs")
    private String currency;

    @Schema(description = "toCurrency for currency pairs (null for securities)")
    private String toCurrency;

    @Schema(description = "Earliest date with historical data")
    private Date minDate;

    @Schema(description = "Most recent date with historical data")
    private Date maxDate;

    @Schema(description = "Total number of historical price records available")
    private int recordCount;

    @Schema(description = "Whether this instrument has any historical data available")
    private boolean available;

    public InstrumentCoverageDTO() {
    }

    /**
     * Creates coverage data for a security.
     */
    public static InstrumentCoverageDTO forSecurity(String isin, String currency, Date minDate, Date maxDate,
        int recordCount) {
      InstrumentCoverageDTO dto = new InstrumentCoverageDTO();
      dto.isin = isin;
      dto.currency = currency;
      dto.minDate = minDate;
      dto.maxDate = maxDate;
      dto.recordCount = recordCount;
      dto.available = minDate != null && maxDate != null;
      return dto;
    }

    /**
     * Creates coverage data for a currency pair.
     */
    public static InstrumentCoverageDTO forCurrencypair(String fromCurrency, String toCurrency, Date minDate,
        Date maxDate, int recordCount) {
      InstrumentCoverageDTO dto = new InstrumentCoverageDTO();
      dto.currency = fromCurrency;
      dto.toCurrency = toCurrency;
      dto.minDate = minDate;
      dto.maxDate = maxDate;
      dto.recordCount = recordCount;
      dto.available = minDate != null && maxDate != null;
      return dto;
    }

    /**
     * Creates coverage data indicating the instrument is not available.
     */
    public static InstrumentCoverageDTO notAvailable(String isin, String currency, String toCurrency) {
      InstrumentCoverageDTO dto = new InstrumentCoverageDTO();
      dto.isin = isin;
      dto.currency = currency;
      dto.toCurrency = toCurrency;
      dto.available = false;
      dto.recordCount = 0;
      return dto;
    }

    // Getters and setters

    public String getIsin() {
      return isin;
    }

    public void setIsin(String isin) {
      this.isin = isin;
    }

    public String getCurrency() {
      return currency;
    }

    public void setCurrency(String currency) {
      this.currency = currency;
    }

    public String getToCurrency() {
      return toCurrency;
    }

    public void setToCurrency(String toCurrency) {
      this.toCurrency = toCurrency;
    }

    public Date getMinDate() {
      return minDate;
    }

    public void setMinDate(Date minDate) {
      this.minDate = minDate;
    }

    public Date getMaxDate() {
      return maxDate;
    }

    public void setMaxDate(Date maxDate) {
      this.maxDate = maxDate;
    }

    public int getRecordCount() {
      return recordCount;
    }

    public void setRecordCount(int recordCount) {
      this.recordCount = recordCount;
    }

    public boolean isAvailable() {
      return available;
    }

    public void setAvailable(boolean available) {
      this.available = available;
    }

    /**
     * Checks if this is a currency pair.
     */
    @JsonIgnore
    public boolean isCurrencypair() {
      return toCurrency != null && !toCurrency.isBlank();
    }

    /**
     * Returns a unique key for this instrument.
     */
    @JsonIgnore
    public String getKey() {
      if (isCurrencypair()) {
        return currency + ":" + toCurrency;
      } else {
        return isin + ":" + currency;
      }
    }

    /**
     * Calculates the coverage period in days (from minDate to maxDate).
     *
     * @return number of days of coverage, or 0 if not available
     */
    @JsonIgnore
    public long getCoverageDays() {
      if (!available || minDate == null || maxDate == null) {
        return 0;
      }
      return (maxDate.getTime() - minDate.getTime()) / (1000 * 60 * 60 * 24);
    }
  }
}
