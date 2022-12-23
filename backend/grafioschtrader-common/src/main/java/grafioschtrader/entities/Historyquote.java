/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.ImportDataRequired;
import grafioschtrader.common.PropertyAlwaysUpdatable;
import grafioschtrader.common.PropertyOnlyCreation;
import grafioschtrader.types.HistoryquoteCreateType;
import grafioschtrader.validation.AfterEqual;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name = Historyquote.TABNAME)
@Schema(description = "Contains a single qoute for end of day data")
public class Historyquote extends ProposeTransientTransfer implements Serializable {

  public static final String TABNAME = "historyquote";

  private static final long serialVersionUID = 1L;
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_history_quote")
  private Integer idHistoryQuote;

  @Schema(description = "Trading date to which these data belong")
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  @AfterEqual(value = GlobalConstants.OLDEST_TRADING_DAY, format = GlobalConstants.STANDARD_DATE_FORMAT)
  @Basic(optional = false)
  @Column(name = "date")
  @Temporal(TemporalType.DATE)
  @PropertyOnlyCreation
  @ImportDataRequired
  private Date date;

  @Schema(description = "Close price of the day")
  @Basic(optional = false)
  @Column(name = "close")
  @PropertyAlwaysUpdatable
  @ImportDataRequired
  private double close;

  @Schema(description = "Volume changed on one day")
  @Column(name = "volume")
  @PropertyAlwaysUpdatable
  private Long volume;

  @Schema(description = "Open price of the day")
  @Column(name = "open")
  @PropertyAlwaysUpdatable
  private Double open;

  @Schema(description = "High price of the day")
  @Column(name = "high")
  @PropertyAlwaysUpdatable
  private Double high;

  @Schema(description = "Low price of the day")
  @Column(name = "low")
  @PropertyAlwaysUpdatable
  private Double low;

  @Schema(description = "Who has crated this EOD record")
  @Column(name = "create_type")
  private byte createType;

  @Schema(description = "When was this recored added or last time modified")
  @Column(name = "create_modify_time")
  @Temporal(TemporalType.TIMESTAMP)
  private Date createModifyTime;

  @Column(name = "id_securitycurrency")
  private Integer idSecuritycurrency;

  public Historyquote() {
    this.createType = HistoryquoteCreateType.CONNECTOR_CREATED.getValue();
  }

  public Historyquote(Integer idSecuritycurrency, HistoryquoteCreateType historyquoteCreateType, Date date,
      double close) {
    this.idSecuritycurrency = idSecuritycurrency;
    this.createType = historyquoteCreateType.getValue();
    this.date = date;
    this.close = close;
  }

  public Historyquote(Integer idSecuritycurrency, HistoryquoteCreateType historyquoteCreateType, Date date) {
    this.idSecuritycurrency = idSecuritycurrency;
    this.createType = historyquoteCreateType.getValue();
    this.date = date;
  }

  public Historyquote(Integer idSecuritycurrency, HistoryquoteCreateType historyquoteCreateType) {
    this(idSecuritycurrency, historyquoteCreateType, null);
  }

  public Historyquote(Date date) {
    this.date = date;
  }

  @Override
  public Integer getId() {
    return this.idHistoryQuote;
  }

  public Historyquote(Integer idhistoryQuote, Date date, double close) {
    this.idHistoryQuote = idhistoryQuote;
    this.date = date;
    this.close = close;
  }

  public Integer getIdHistoryQuote() {
    return idHistoryQuote;
  }

  public void setIdHistoryQuote(Integer idhistoryQuote) {
    this.idHistoryQuote = idhistoryQuote;
  }

  public Date getDate() {
    return date;
  }

  public void setDate(Date date) {
    this.date = date;
  }

  public void setDateLD(LocalDate localDate) {
    this.date = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
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

  public Date getCreateModifyTime() {
    return createModifyTime;
  }

  public void setCreateModifyTime(Date createModifyTime) {
    this.createModifyTime = createModifyTime;
  }

  public void updateThis(Historyquote sourceHistoryquote) {
    this.setClose(sourceHistoryquote.getClose());
    this.setHigh(sourceHistoryquote.getHigh());
    this.setLow(sourceHistoryquote.getLow());
    this.setOpen(sourceHistoryquote.getOpen());
    this.setVolume(sourceHistoryquote.getVolume());
    this.setCreateModifyTime(new Date());
  }

  @Override
  public String toString() {
    return "Historyquote [idHistoryQuote=" + idHistoryQuote + ", date=" + date + ", close=" + close + ", volume="
        + volume + ", open=" + open + ", high=" + high + ", low=" + low + ", idSecuritycurrency=" + idSecuritycurrency
        + "]";
  }

}
