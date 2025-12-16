package grafioschtrader.entities;

import static jakarta.persistence.InheritanceType.JOINED;

import java.util.Date;

import grafiosch.entities.BaseID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

/**
 * Abstract base class for intraday price data shared via the GT-Network.
 *
 * This entity stores normalized OHLCV (Open, High, Low, Close, Volume) data for instruments that can be
 * shared between GTNet peers. Concrete implementations exist for:
 * <ul>
 *   <li>{@link GTNetLastpriceSecurity} - Securities identified by ISIN</li>
 *   <li>{@link GTNetLastpriceCurrencypair} - Currency pairs identified by from/to currency codes</li>
 * </ul>
 *
 * The data flow works as follows:
 * <ol>
 *   <li>Provider instances update this table with current market data from their connectors</li>
 *   <li>Consumer instances query providers and merge newer data into their local Security/Currencypair tables</li>
 *   <li>All operations are logged via {@link GTNetLastpriceLog} and optionally {@link GTNetLastpriceDetailLog}</li>
 * </ol>
 *
 * Uses JPA JOINED inheritance strategy, with discriminator values 'S' for Security and 'C' for Currencypair.
 */
@Entity
@Table(name = GTNetLastprice.TABNAME)
@Inheritance(strategy = JOINED)
@Schema(description = """
    Abstract base class for intraday price data shared via the GT-Network. Stores normalized OHLCV data that can be
    exchanged between GTNet peers. Extended by GTNetLastpriceSecurity (for securities with ISIN) and
    GTNetLastpriceCurrencypair (for currency pairs). Provider instances populate this data from their connectors;
    consumer instances query providers and merge newer data into local tables.""")
public abstract class GTNetLastprice extends BaseID<Integer> {
  public static final String TABNAME = "gt_net_lastprice";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_net_lastprice")
  protected Integer idNetLastprice;

  @Schema(description = "Time of the last instraday price update")
  @Column(name = "timestamp")
  @Temporal(TemporalType.TIMESTAMP)
  protected Date timestamp;

  @Schema(description = "Opening price for the last or current trading day")
  @Column(name = "open")
  protected Double open;

  @Schema(description = "Lowest price for the last or current trading day.")
  @Column(name = "low")
  protected Double low;

  @Schema(description = "Higest price for the last or current trading day.")
  @Column(name = "high")
  protected Double high;

  @Schema(description = "The most current price - possibly with after hour trade.")
  @Column(name = "last")
  protected Double last;

  @Schema(description = "The traded volume for this trading day. Cryptocurrencies can also have a volume.")
  @Column(name = "volume")
  protected Long volume;

  public GTNetLastprice() {
    super();
  }

  public Integer getIdNetLastprice() {
    return idNetLastprice;
  }

  public void setIdNetLastprice(Integer idNetLastprice) {
    this.idNetLastprice = idNetLastprice;
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

  public Double getLow() {
    return low;
  }

  public void setLow(Double low) {
    this.low = low;
  }

  public Double getHigh() {
    return high;
  }

  public void setHigh(Double high) {
    this.high = high;
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
    return idNetLastprice;
  }

}
