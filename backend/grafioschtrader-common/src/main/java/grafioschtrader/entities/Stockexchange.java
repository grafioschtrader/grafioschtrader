/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package grafioschtrader.entities;

import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.PropertyAlwaysUpdatable;
import grafioschtrader.common.PropertyOnlyCreation;
import grafioschtrader.common.PropertySelectiveUpdatableOrWhenNull;
import grafioschtrader.validation.WebUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Stock exchange
 */
@Schema(description = "Contains a stock exchange")
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

  @Schema(description = "The market identifier code (MIC) for this stock exchanges. It must be unique")
  @Column(name = "mic")
  @Size(min = 4, max = 4)
  @PropertySelectiveUpdatableOrWhenNull
  private String mic;
  
  @Schema(description = "The name of the stock exchange")
  @Basic(optional = false)
  @Column(name = "name")
  @NotBlank
  @Size(min = 2, max = 32)
  @PropertyAlwaysUpdatable
  private String name;

  @Schema(description = "The country code of this exchange")
  @NotBlank
  @Column(name = "country_code")
  @PropertyAlwaysUpdatable
  private String countryCode;

  @Schema(description = "For an exchange without publicly available quote data, the period quotes entered by the user are used")
  @Basic(optional = false)
  @Column(name = "no_market_value")
  @PropertySelectiveUpdatableOrWhenNull
  private boolean noMarketValue;
  
  @Schema(description = "Defines whether secondary trading is supported by this exchange")
  @Basic(optional = false)
  @Column(name = "secondary_market")
  @PropertyAlwaysUpdatable
  private boolean secondaryMarket;

  @Schema(description = "The official opening time of this stock exchange")
  @Basic(optional = false)
  @Column(name = "time_open")
  @JsonFormat(pattern = "HH:mm")
  @NotNull
  @PropertyAlwaysUpdatable
  private LocalTime timeOpen;

  @Schema(description = "The official time of the close of trading of this stock exchange")  
  @Basic(optional = false)
  @Column(name = "time_close")
  @JsonFormat(pattern = "HH:mm")
  @NotNull
  @PropertyAlwaysUpdatable
  private LocalTime timeClose;
  

  @Schema(description = "The time zone of this stock exchange")  
  @Basic(optional = false)
  @NotNull
  @Size(min = 1, max = 50)
  @Column(name = "time_zone")
  @PropertyOnlyCreation
  private String timeZone;

  @Schema(description = "ID of the index through which the trading calendar receives its update")  
  @PropertyAlwaysUpdatable
  @Column(name = "id_index_upd_calendar")
  private Integer idIndexUpdCalendar;
  
  @Schema(description = "Until this date, the update of the trading calendar was performed via the index. This date is set by the system")  
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  @Column(name = "max_calendar_upd_date")
  private LocalDate maxCalendarUpdDate;

  @Schema(description = "When was the last update performed for this stock exchange ")
  @JsonFormat(pattern = GlobalConstants.STANDARD_LOCAL_DATE_TIME)
  @Column(name = "last_direct_price_update")
  private LocalDateTime lastDirectPriceUpdate;
  
  @Schema(description = "HTML link to the website of the exchange")
  @Column(name = "website")
  @WebUrl
  @PropertyAlwaysUpdatable
  private String website;

  @Transient
  private String nameIndexUpdCalendar;

  public Stockexchange() {
  }

  public Stockexchange(String name, String mic, LocalTime timeClose, String timeZone, boolean noMarketValue,
      boolean secondaryMarket) {
    this.name = name;
    this.mic = mic;
    this.timeClose = timeClose;
    this.timeZone = timeZone;
    this.noMarketValue = noMarketValue;
    this.secondaryMarket = secondaryMarket;
  }

  public String getMic() {
    return mic;
  }

  public void setMic(String mic) {
    this.mic = mic;
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
  
  public LocalDate getMaxCalendarUpdDate() {
    return maxCalendarUpdDate;
  }

  public LocalDateTime getLastDirectPriceUpdate() {
    return lastDirectPriceUpdate;
  }

  public void setLastDirectPriceUpdate(LocalDateTime lastDirectPriceUpdate) {
    this.lastDirectPriceUpdate = lastDirectPriceUpdate;
  }
  
  public String getWebsite() {
    return website;
  }

  public void setWebsite(String website) {
    this.website = website;
  }

  @Override
  public Integer getId() {
    return this.idStockexchange;
  }

  @JsonFormat(pattern = GlobalConstants.STARNDARD_LOCAL_TIME)
  public LocalTime getLocalTime() {
    return LocalTime.now(ZoneId.of(timeZone));
  }

  @JsonIgnore
  public boolean isNowOpenExchange() {
    ZonedDateTime zonedDateTime = ZonedDateTime.now(ZoneId.of(timeZone));
    if (zonedDateTime.getDayOfWeek() != DayOfWeek.SATURDAY && zonedDateTime.getDayOfWeek() != DayOfWeek.SUNDAY) {
      LocalTime lcMpw = zonedDateTime.toLocalTime();
      boolean isBetweenOpenAndCloseStrictlySpeaking = !lcMpw.isBefore(timeOpen) && lcMpw.isBefore(timeClose);
      if (timeOpen.isAfter(timeClose)) {
        return !isBetweenOpenAndCloseStrictlySpeaking;
      } else {
        return isBetweenOpenAndCloseStrictlySpeaking;
      }
    }
    return false;
  }

  /**
   * Return of minutes since the exchange is closed. If stock exchange is open,
   * then negative number of minutes until the next closing.
   * 
   * @return
   */
  @JsonIgnore
  public int getClosedMinuntes() {
    LocalDateTime nowTimeZone = LocalDateTime.now(ZoneId.of(timeZone));
    int closedMinutes = (int) Duration.between(timeClose, LocalTime.now()).toMinutes()
        + ZoneId.of(timeZone).getRules().getOffset(Instant.now()).getTotalSeconds() / 60;
    closedMinutes = isNowOpenExchange() || closedMinutes > 0 ? closedMinutes : 24 * 60 + closedMinutes;
    closedMinutes += nowTimeZone.getDayOfWeek() == DayOfWeek.SUNDAY ? 24 * 60 : 0;
    closedMinutes += nowTimeZone.getDayOfWeek() == DayOfWeek.MONDAY && nowTimeZone.toLocalTime().isBefore(timeOpen)
        ? 24 * 60
        : 0;
    System.out.println("Name:" + name + ", Day:" + nowTimeZone.getDayOfWeek() + ", closed:" + closedMinutes);
    return closedMinutes;
  }

  @JsonIgnore
  public boolean mayHavePriceUpdateSinceLastClose() {
    LocalDateTime nowTimeZone = LocalDateTime.now(ZoneId.of(timeZone));
    ZoneId zoneLocal = ZoneId.of(timeZone);
    LocalDateTime ldpuDateTimeLocal = lastDirectPriceUpdate.atZone(ZoneOffset.UTC).withZoneSameInstant(zoneLocal)
        .toLocalDateTime();
    long minusDays = nowTimeZone.getDayOfWeek() == DayOfWeek.SUNDAY ? 2
        : nowTimeZone.getDayOfWeek() == DayOfWeek.MONDAY ? 3 : 1;
    LocalDateTime lastEspectedUpdateDateTime = nowTimeZone.minusDays(minusDays);
    boolean hasPriceUpdate = lastEspectedUpdateDateTime.toLocalDate().isAfter(ldpuDateTimeLocal.toLocalDate())
        || lastEspectedUpdateDateTime.toLocalDate().isEqual(ldpuDateTimeLocal.toLocalDate())
            && lastEspectedUpdateDateTime.toLocalTime().isBefore(timeClose)
        || nowTimeZone.getDayOfWeek() == DayOfWeek.MONDAY && nowTimeZone.toLocalTime().isAfter(timeClose)
            && lastDirectPriceUpdate.getDayOfWeek() != DayOfWeek.MONDAY;
    System.out.println("May Have Price Update: " + hasPriceUpdate);
    return hasPriceUpdate;
  }

  @Override
  public String toString() {
    return "Stockexchange [idStockexchange=" + idStockexchange + ", mic=" + mic + ", name=" + name + ", countryCode="
        + countryCode + ", noMarketValue=" + noMarketValue + ", secondaryMarket=" + secondaryMarket + ", timeOpen="
        + timeOpen + ", timeClose=" + timeClose + ", MIC=" + mic + ", timeZone=" + timeZone
        + ", idIndexUpdCalendar=" + idIndexUpdCalendar + ", lastDirectPriceUpdate=" + lastDirectPriceUpdate
        + ", website=" + website + ", nameIndexUpdCalendar=" + nameIndexUpdCalendar + "]";
  }

  

}
