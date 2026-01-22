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

import grafiosch.BaseConstants;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertyOnlyCreation;
import grafiosch.common.PropertySelectiveUpdatableOrWhenNull;
import grafiosch.entities.Auditable;
import grafiosch.validation.WebUrl;
import grafioschtrader.GlobalConstants;
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
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "max_calendar_upd_date")
  private LocalDate maxCalendarUpdDate;

  @Schema(description = "When was the last update performed for this stock exchange ")
  @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME)
  @Column(name = "last_direct_price_update")
  private LocalDateTime lastDirectPriceUpdate;

  @Schema(description = "HTML link to the website of the exchange")
  @Size(max = GlobalConstants.FIELD_SIZE_MAX_Stockexchange_Website)
  @Column(name = "website")
  @WebUrl
  @PropertyAlwaysUpdatable
  private String website;

  @Transient
  private String nameIndexUpdCalendar;

  public Stockexchange() {
  }
 

  public Stockexchange(@Size(min = 4, max = 4) String mic,
      @NotBlank @Size(min = 2, max = 32) String name, @NotBlank String countryCode, boolean noMarketValue,
      boolean secondaryMarket, @NotNull LocalTime timeOpen, @NotNull LocalTime timeClose,
      @NotNull @Size(min = 1, max = 50) String timeZone, @Size(max = 128) String website) {
    super();
    this.mic = mic;
    this.name = name;
    this.countryCode = countryCode;
    this.noMarketValue = noMarketValue;
    this.secondaryMarket = secondaryMarket;
    this.timeOpen = timeOpen;
    this.timeClose = timeClose;
    this.timeZone = timeZone;
    this.website = website;
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

//@formatter:off
  /**
   * Returns the number of minutes since the exchange last closed in the configured time zone.
   * If the exchange is currently open, returns a negative value indicating the minutes
   * remaining until the next closing time.
   * <p>
   * The calculation accounts for:
   * <ul>
   *   <li>Overnight closures (wrapping negative intervals into the next trading day).</li>
   *   <li>Sunday closures (adds 24 hours to represent the weekend gap).</li>
   *   <li>Monday pre-market period (adds 24 hours if the current time is before opening).</li>
   * </ul>
   * </p>
   *
   * @return the number of minutes since market close (positive), or negative minutes until next close if market is open
   */
//@formatter:on
  @JsonIgnore
  public int getClosedMinuntes() {
    LocalDateTime nowTimeZone = LocalDateTime.now(ZoneId.of(timeZone));
    int closedMinutes = (int) Duration.between(timeClose, nowTimeZone.toLocalTime()).toMinutes();
    closedMinutes = isNowOpenExchange() || closedMinutes > 0 ? closedMinutes : 24 * 60 + closedMinutes;
    closedMinutes += nowTimeZone.getDayOfWeek() == DayOfWeek.SATURDAY ? 24 * 60 : 0;
    closedMinutes += nowTimeZone.getDayOfWeek() == DayOfWeek.SUNDAY ? 24 * 60 : 0;
    closedMinutes += nowTimeZone.getDayOfWeek() == DayOfWeek.MONDAY && nowTimeZone.toLocalTime().isBefore(timeOpen)
        ? 24 * 60
        : 0;
    return closedMinutes;
  }

  @JsonIgnore
  public boolean mayHavePriceUpdateSinceLastClose() {
    if (lastDirectPriceUpdate == null) {
      return true;
    }
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
    return hasPriceUpdate;
  }

  @JsonIgnore
  public int getTimeDifferenceFromUTCInSeconds() {
    ZoneId zoneId = ZoneId.of(timeZone);
    ZonedDateTime nowInZone = ZonedDateTime.now(zoneId);
    ZoneOffset offset = nowInZone.getOffset();
    return offset.getTotalSeconds();
  }

  @Override
  public String toString() {
    return "Stockexchange [idStockexchange=" + idStockexchange + ", mic=" + mic + ", name=" + name + ", countryCode="
        + countryCode + ", noMarketValue=" + noMarketValue + ", secondaryMarket=" + secondaryMarket + ", timeOpen="
        + timeOpen + ", timeClose=" + timeClose + ", MIC=" + mic + ", timeZone=" + timeZone + ", idIndexUpdCalendar="
        + idIndexUpdCalendar + ", lastDirectPriceUpdate=" + lastDirectPriceUpdate + ", website=" + website
        + ", nameIndexUpdCalendar=" + nameIndexUpdCalendar + "]";
  }

}
