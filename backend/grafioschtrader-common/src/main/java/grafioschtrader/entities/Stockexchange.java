/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalTime;

import javax.persistence.Basic;
import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.common.PropertyAlwaysUpdatable;
import grafioschtrader.common.PropertyOnlyCreation;
import grafioschtrader.common.PropertySelectiveUpdatableOrWhenNull;

/**
 * Stockexchange
 *
 * @author Hugo Graf
 */
@Entity
@Table(name = Stockexchange.TABNAME)
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Stockexchange extends Auditable implements Serializable {

  public static final String TABNAME = "stockexchange";

  private static final long serialVersionUID = 1L;
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_stockexchange")
  private Integer idStockexchange;

  @Basic(optional = false)
  @Column(name = "name")
  @NotBlank
  @Size(min = 2, max = 32)
  @PropertyAlwaysUpdatable
  private String name;

  @NotBlank
  @Column(name = "country_code")
  @PropertyOnlyCreation
  private String countryCode;

  @Basic(optional = false)
  @Column(name = "no_market_value")
  @PropertySelectiveUpdatableOrWhenNull
  private boolean noMarketValue;

  @Basic(optional = false)
  @Column(name = "secondary_market")
  @PropertyAlwaysUpdatable
  private boolean secondaryMarket;

  @Basic(optional = false)
  @Column(name = "time_open")
  @JsonFormat(pattern = "HH:mm")
  @NotNull
  @PropertyAlwaysUpdatable
  private LocalTime timeOpen;

  @Basic(optional = false)
  @Column(name = "time_close")
  @JsonFormat(pattern = "HH:mm")
  @NotNull
  @PropertyAlwaysUpdatable
  private LocalTime timeClose;

  @Basic(optional = false)
  @NotNull
  @Column(name = "symbol")
  @NotBlank
  @Size(min = 3, max = 8)
  @PropertyAlwaysUpdatable
  private String symbol;

  @Basic(optional = false)
  @NotNull
  @Size(min = 1, max = 50)
  @Column(name = "time_zone")
  @PropertyOnlyCreation
  private String timeZone;

  @PropertyAlwaysUpdatable
  @Column(name = "id_index_upd_calendar")
  private Integer idIndexUpdCalendar;

  @Transient
  private String nameIndexUpdCalendar;

  public Stockexchange() {
  }

  public Stockexchange(String name, String symbol, LocalTime timeClose, String timeZone, boolean noMarketValue,
      boolean secondaryMarket) {
    this.name = name;
    this.symbol = symbol;
    this.timeClose = timeClose;
    this.timeZone = timeZone;
    this.noMarketValue = noMarketValue;
    this.secondaryMarket = secondaryMarket;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(String timeZone) {
    this.timeZone = timeZone;
  }

  public Integer getIdStockexchange() {
    return idStockexchange;
  }

  public void setIdStockexchange(Integer idStockexchange) {
    this.idStockexchange = idStockexchange;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCountryCode() {
    return countryCode;
  }

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

  public boolean isSecondaryMarket() {
    return secondaryMarket;
  }

  public void setSecondaryMarket(boolean secondaryMarket) {
    this.secondaryMarket = secondaryMarket;
  }

  public boolean isNoMarketValue() {
    return noMarketValue;
  }

  public void setNoMarketValue(boolean noMarketValue) {
    this.noMarketValue = noMarketValue;
  }

  public LocalTime getTimeClose() {
    return timeClose;
  }

  public void setTimeClose(LocalTime timeClose) {
    this.timeClose = timeClose;
  }

  public LocalTime getTimeOpen() {
    return timeOpen;
  }

  public void setTimeOpen(LocalTime timeOpen) {
    this.timeOpen = timeOpen;
  }

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public Integer getIdIndexUpdCalendar() {
    return idIndexUpdCalendar;
  }

  public void setIdIndexUpdCalendar(Integer idIndexUpdCalendar) {
    this.idIndexUpdCalendar = idIndexUpdCalendar;
  }

  public String getNameIndexUpdCalendar() {
    return nameIndexUpdCalendar;
  }

  public void setNameIndexUpdCalendar(String nameIndexUpdCalendar) {
    this.nameIndexUpdCalendar = nameIndexUpdCalendar;
  }

  @Override
  public Integer getId() {
    return this.idStockexchange;
  }

  @Override
  public String toString() {
    return "Stockexchange [idStockexchange=" + idStockexchange + ", name=" + name + ", noMarketValue=" + noMarketValue
        + ", secondaryMarket=" + secondaryMarket + ", timeClose=" + timeClose + ", symbol=" + symbol + ", timeZone="
        + timeZone + "]";
  }

}
