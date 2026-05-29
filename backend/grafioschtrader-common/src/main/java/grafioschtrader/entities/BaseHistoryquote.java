package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.common.ImportDataRequired;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertyOnlyCreation;
import grafiosch.entities.ProposeTransientTransfer;
import grafiosch.validation.AfterEqual;
import grafioschtrader.GlobalConstants;
import grafioschtrader.types.HistoryquoteCreateType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * Shared base for end-of-day quote records. Holds the price (OHLCV), the trading date, the creation provenance and the
 * owning security/currency-pair reference common to both the live {@link Historyquote} table and the
 * {@link HistoryquoteLegacy} shadow archive.
 *
 * <p>
 * Extending {@link ProposeTransientTransfer} lets both subclasses participate in the propose-change approval flow: a
 * user without direct edit rights on the parent security produces a {@code ProposeChangeEntity} instead of a direct
 * write. The {@code @PropertyAlwaysUpdatable} / {@code @PropertyOnlyCreation} annotations on the fields below drive the
 * field-level diff used by that flow ({@code DataHelper.getDiffPropertiesOfEntity} walks the whole class hierarchy via
 * {@code FieldUtils.getAllFieldsList}, so the annotations are honoured even though the fields live in this superclass).
 * </p>
 */
@MappedSuperclass
public abstract class BaseHistoryquote extends ProposeTransientTransfer implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "Trading date to which these data belong")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @AfterEqual(value = GlobalConstants.OLDEST_TRADING_DAY, format = BaseConstants.STANDARD_DATE_FORMAT)
  @Basic(optional = false)
  @Column(name = "date")
  @PropertyOnlyCreation
  @ImportDataRequired
  protected LocalDate date;

  @Schema(description = "Close price of the day")
  @Basic(optional = false)
  @Column(name = "close")
  @PropertyAlwaysUpdatable
  @ImportDataRequired
  protected double close;

  @Schema(description = "Volume changed on one day")
  @Column(name = "volume")
  @PropertyAlwaysUpdatable
  protected Long volume;

  @Schema(description = "Open price of the day")
  @Column(name = "open")
  @PropertyAlwaysUpdatable
  protected Double open;

  @Schema(description = "High price of the day")
  @Column(name = "high")
  @PropertyAlwaysUpdatable
  protected Double high;

  @Schema(description = "Low price of the day")
  @Column(name = "low")
  @PropertyAlwaysUpdatable
  protected Double low;

  @Schema(description = "Who has crated this EOD record")
  @Column(name = "create_type")
  protected byte createType;

  @Column(name = "id_securitycurrency")
  protected Integer idSecuritycurrency;

  public LocalDate getDate() {
    return date;
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }

  public double getClose() {
    return close;
  }

  public void setClose(double close) {
    this.close = close;
  }

  public Long getVolume() {
    return volume;
  }

  public void setVolume(Long volume) {
    this.volume = volume;
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

  public Integer getIdSecuritycurrency() {
    return idSecuritycurrency;
  }

  public void setIdSecuritycurrency(Integer idSecuritycurrency) {
    this.idSecuritycurrency = idSecuritycurrency;
  }

  public HistoryquoteCreateType getCreateType() {
    return HistoryquoteCreateType.getHistoryquoteCreateType(createType);
  }

  public void setCreateType(HistoryquoteCreateType historyquoteCreateType) {
    this.createType = historyquoteCreateType.getValue();
  }

}
