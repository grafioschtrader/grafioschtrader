/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.types.HistoryquoteCreateType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Schema(description = "Contains a single qoute for end of day data")
@Entity
@Table(name = Historyquote.TABNAME)
public class Historyquote extends BaseHistoryquote implements Serializable {

  public static final String TABNAME = "historyquote";

  private static final long serialVersionUID = 1L;
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_history_quote")
  private Integer idHistoryQuote;

  @Schema(description = "When was this recored added or last time modified")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT)
  @Column(name = "create_modify_time")
  private LocalDateTime createModifyTime;

  public Historyquote() {
    this.createType = HistoryquoteCreateType.CONNECTOR_CREATED.getValue();
  }

  public Historyquote(Integer idSecuritycurrency, HistoryquoteCreateType historyquoteCreateType, LocalDate date,
      double close) {
    this.idSecuritycurrency = idSecuritycurrency;
    this.createType = historyquoteCreateType.getValue();
    this.date = date;
    this.close = close;
  }

  public Historyquote(Integer idSecuritycurrency, HistoryquoteCreateType historyquoteCreateType, LocalDate date) {
    this.idSecuritycurrency = idSecuritycurrency;
    this.createType = historyquoteCreateType.getValue();
    this.date = date;
  }

  public Historyquote(Integer idSecuritycurrency, HistoryquoteCreateType historyquoteCreateType) {
    this(idSecuritycurrency, historyquoteCreateType, null);
  }

  public Historyquote(LocalDate date) {
    this.date = date;
  }

  @Override
  public Integer getId() {
    return this.idHistoryQuote;
  }

  public Historyquote(Integer idhistoryQuote, LocalDate date, double close) {
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

  public LocalDateTime getCreateModifyTime() {
    return createModifyTime;
  }

  public void setCreateModifyTime(LocalDateTime createModifyTime) {
    this.createModifyTime = createModifyTime;
  }

  public void updateThis(Historyquote sourceHistoryquote) {
    this.setClose(sourceHistoryquote.getClose());
    this.setHigh(sourceHistoryquote.getHigh());
    this.setLow(sourceHistoryquote.getLow());
    this.setOpen(sourceHistoryquote.getOpen());
    this.setVolume(sourceHistoryquote.getVolume());
    this.setCreateModifyTime(LocalDateTime.now());
  }

  @Override
  public String toString() {
    return "Historyquote [idHistoryQuote=" + idHistoryQuote + ", date=" + date + ", close=" + close + ", volume="
        + volume + ", open=" + open + ", high=" + high + ", low=" + low + ", idSecuritycurrency=" + idSecuritycurrency
        + "]";
  }

}
