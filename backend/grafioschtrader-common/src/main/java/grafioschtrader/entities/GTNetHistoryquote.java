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
 * Historical (EOD) price data for FOREIGN instruments in the GT-Network pool.
 *
 * This entity stores end-of-day OHLCV (Open, High, Low, Close, Volume) data for instruments that do NOT
 * exist in the local database. For instruments that DO exist locally (where {@link GTNetInstrument#isLocalInstrument()}
 * returns true), historical data is stored in the standard {@link Historyquote} table instead.
 *
 * <h3>When This Table Is Used</h3>
 * <ul>
 *   <li>When receiving historical quotes for an instrument not in our local database</li>
 *   <li>When serving as a PUSH_OPEN provider for instruments received from other GTNet peers</li>
 *   <li>The {@link GTNetInstrument#getIdSecuritycurrency()} will be null for these instruments</li>
 * </ul>
 *
 * <h3>When Standard Historyquote Is Used Instead</h3>
 * <ul>
 *   <li>When the instrument exists locally ({@link GTNetInstrument#isLocalInstrument()} returns true)</li>
 *   <li>Historical quotes go directly to {@link Historyquote} linked by idSecuritycurrency</li>
 *   <li>This avoids data duplication and keeps the local historyquote table as the single source of truth</li>
 * </ul>
 *
 * @see GTNetInstrument for instrument identification and local/foreign distinction
 * @see GTNetLastprice for intraday price data
 * @see Historyquote for local instrument historical data
 */
@Entity
@Table(name = GTNetHistoryquote.TABNAME)
@Schema(description = """
    Historical (EOD) price data for FOREIGN instruments in the GT-Network pool. Stores OHLCV data with a date,
    linked to a GTNetInstrument. Only used when the instrument does NOT exist locally (idSecuritycurrency is null).
    For local instruments, historical data goes to the standard historyquote table instead.""")
public class GTNetHistoryquote extends BaseID<Integer> {
  public static final String TABNAME = "gt_net_historyquote";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_gt_net_historyquote")
  private Integer idGtNetHistoryquote;

  @Schema(description = "Reference to the instrument in the GTNet pool (must be a foreign instrument)")
  @ManyToOne
  @JoinColumn(name = "id_gt_net_instrument", nullable = false)
  private GTNetInstrument gtNetInstrument;

  @Schema(description = "Trading date for this historical quote")
  @Column(name = "date", nullable = false)
  @Temporal(TemporalType.DATE)
  private Date date;

  @Schema(description = "Opening price for this trading day")
  @Column(name = "open")
  private Double open;

  @Schema(description = "Highest price for this trading day")
  @Column(name = "high")
  private Double high;

  @Schema(description = "Lowest price for this trading day")
  @Column(name = "low")
  private Double low;

  @Schema(description = "Closing price for this trading day (required)")
  @Column(name = "close", nullable = false)
  private Double close;

  @Schema(description = "Traded volume for this trading day")
  @Column(name = "volume")
  private Long volume;

  public GTNetHistoryquote() {
    super();
  }

  public Integer getIdGtNetHistoryquote() {
    return idGtNetHistoryquote;
  }

  public GTNetInstrument getGtNetInstrument() {
    return gtNetInstrument;
  }

  public void setGtNetInstrument(GTNetInstrument gtNetInstrument) {
    this.gtNetInstrument = gtNetInstrument;
  }

  public Date getDate() {
    return date;
  }

  public void setDate(Date date) {
    this.date = date;
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

  public Double getClose() {
    return close;
  }

  public void setClose(Double close) {
    this.close = close;
  }

  public Long getVolume() {
    return volume;
  }

  public void setVolume(Long volume) {
    this.volume = volume;
  }

  @Override
  public Integer getId() {
    return idGtNetHistoryquote;
  }

}
