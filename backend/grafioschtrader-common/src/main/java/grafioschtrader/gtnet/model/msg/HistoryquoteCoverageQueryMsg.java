package grafioschtrader.gtnet.model.msg;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request payload for querying historical price coverage metadata from GTNet peers.
 *
 * This is a lightweight query that requests only the date coverage (min/max dates) for instruments
 * without fetching the actual historical price data. Used during security import to determine which
 * GTNet peer has the longest historical coverage for newly created securities.
 *
 * @see grafioschtrader.gtnet.GTNetMessageCodeType#GT_NET_HISTORYQUOTE_COVERAGE_SEL_C
 */
@Schema(description = """
    Request payload for querying historical price coverage metadata. Contains lists of instruments
    (identified by ISIN+currency or currency pair) for which coverage information is requested.
    The response will contain min/max date ranges without the actual price data.""")
public class HistoryquoteCoverageQueryMsg {

  @Schema(description = "List of securities to query coverage for (identified by ISIN + currency)")
  public List<InstrumentIdentifier> securities = new ArrayList<>();

  @Schema(description = "List of currency pairs to query coverage for (identified by fromCurrency + toCurrency)")
  public List<InstrumentIdentifier> currencypairs = new ArrayList<>();

  public HistoryquoteCoverageQueryMsg() {
  }

  /**
   * Creates a coverage query for the given instrument identifiers.
   *
   * @param securities    list of security identifiers (ISIN + currency)
   * @param currencypairs list of currency pair identifiers
   * @return the coverage query message
   */
  public static HistoryquoteCoverageQueryMsg forQuery(List<InstrumentIdentifier> securities,
      List<InstrumentIdentifier> currencypairs) {
    HistoryquoteCoverageQueryMsg msg = new HistoryquoteCoverageQueryMsg();
    msg.securities = securities != null ? securities : new ArrayList<>();
    msg.currencypairs = currencypairs != null ? currencypairs : new ArrayList<>();
    return msg;
  }

  /**
   * Returns the total number of instruments in this query.
   */
  @JsonIgnore
  public int getTotalInstrumentCount() {
    return (securities != null ? securities.size() : 0) + (currencypairs != null ? currencypairs.size() : 0);
  }

  /**
   * Checks if this query is empty.
   */
  @JsonIgnore
  public boolean isEmpty() {
    return getTotalInstrumentCount() == 0;
  }

  /**
   * Lightweight identifier for an instrument in coverage queries.
   * Contains only the fields needed to identify the instrument without the full metadata.
   */
  @Schema(description = """
      Lightweight identifier for an instrument in coverage queries.
      For securities: isin and currency are required.
      For currency pairs: fromCurrency and toCurrency are required (stored in currency and toCurrency fields).""")
  public static class InstrumentIdentifier {

    @Schema(description = "ISIN for securities, or fromCurrency for currency pairs")
    public String isin;

    @Schema(description = "Currency code for securities, or fromCurrency for currency pairs")
    public String currency;

    @Schema(description = "toCurrency for currency pairs (null for securities)")
    public String toCurrency;

    public InstrumentIdentifier() {
    }

    /**
     * Creates a security identifier.
     */
    public static InstrumentIdentifier forSecurity(String isin, String currency) {
      InstrumentIdentifier id = new InstrumentIdentifier();
      id.isin = isin;
      id.currency = currency;
      return id;
    }

    /**
     * Creates a currency pair identifier.
     */
    public static InstrumentIdentifier forCurrencypair(String fromCurrency, String toCurrency) {
      InstrumentIdentifier id = new InstrumentIdentifier();
      id.currency = fromCurrency;
      id.toCurrency = toCurrency;
      return id;
    }

    /**
     * Checks if this is a currency pair identifier.
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
  }
}
