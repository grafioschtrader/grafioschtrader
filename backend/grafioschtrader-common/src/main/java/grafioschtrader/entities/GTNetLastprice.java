package grafioschtrader.entities;

import java.util.Date;

import grafiosch.entities.BaseID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

/**
 * Intraday price data for instruments in the GT-Network pool.
 *
 * This entity stores normalized OHLCV (Open, High, Low, Last, Volume) data for instruments that are shared
 * between GTNet peers. Each record references a {@link GTNetInstrument} which identifies the security or
 * currency pair.
 *
 * <h3>Data Flow</h3>
 * <ol>
 *   <li>Provider instances update this table with current market data from their connectors</li>
 *   <li>Consumer instances query providers and receive price updates</li>
 *   <li>Updates are applied based on timestamp comparison (newer wins)</li>
 * </ol>
 *
 * <h3>Relationship to Instrument Pool</h3>
 * Each GTNetLastprice record has a 1:1 relationship with a GTNetInstrument. The instrument provides
 * the identification (ISIN+currency for securities, from/to currency for pairs), while this entity
 * stores the actual price data.
 *
 * @see GTNetInstrument for instrument identification
 * @see GTNetInstrumentSecurity for security instruments
 * @see GTNetInstrumentCurrencypair for currency pair instruments
 */
@Entity
@Table(name = GTNetLastprice.TABNAME)
@Schema(description = """
    Intraday price data for instruments in the GT-Network pool. Stores OHLCV (Open, High, Low, Last, Volume)
    data with a timestamp, linked to a GTNetInstrument which provides the identification. Provider instances
    populate this from connectors; consumer instances query providers and merge newer data.""")
public class GTNetLastprice extends BaseID<Integer> {
  public static final String TABNAME = "gt_net_lastprice";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_gt_net_lastprice")
  private Integer idGtNetLastprice;

  @Schema(description = "Reference to the instrument in the GTNet pool")
  @ManyToOne
  @JoinColumn(name = "id_gt_net_instrument", nullable = false)
  private GTNetInstrument gtNetInstrument;

  @Schema(description = "Time of the last intraday price update")
  @Column(name = "timestamp")
  @Temporal(TemporalType.TIMESTAMP)
  private Date timestamp;

  @Schema(description = "Opening price for the last or current trading day")
  @Column(name = "open")
  private Double open;

  @Schema(description = "Highest price for the last or current trading day")
  @Column(name = "high")
  private Double high;

  @Schema(description = "Lowest price for the last or current trading day")
  @Column(name = "low")
  private Double low;

  @Schema(description = "The most current price - possibly with after-hour trades")
  @Column(name = "last")
  private Double last;

  @Schema(description = "The traded volume for this trading day. Cryptocurrencies can also have a volume.")
  @Column(name = "volume")
  private Long volume;

  public GTNetLastprice() {
    super();
  }

  public Integer getIdGtNetLastprice() {
    return idGtNetLastprice;
  }

  public GTNetInstrument getGtNetInstrument() {
    return gtNetInstrument;
  }

  public void setGtNetInstrument(GTNetInstrument gtNetInstrument) {
    this.gtNetInstrument = gtNetInstrument;
  }

  public Date getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Date timestamp) {
    this.timestamp = timestamp;
  }

  public Double getOpen() {
    return open;
  }

  public void setOpen(Double open) {
    this.open = open;
  }

  public Double getHigh() {
    return high;
  }

  public void setHigh(Double high) {
    this.high = high;
  }

  public Double getLow() {
    return low;
  }

  public void setLow(Double low) {
    this.low = low;
  }

  public Double getLast() {
    return last;
  }

  public void setLast(Double last) {
    this.last = last;
  }

  public Long getVolume() {
    return volume;
  }

  public void setVolume(Long volume) {
    this.volume = volume;
  }

  @Override
  public Integer getId() {
    return idGtNetLastprice;
  }

}
