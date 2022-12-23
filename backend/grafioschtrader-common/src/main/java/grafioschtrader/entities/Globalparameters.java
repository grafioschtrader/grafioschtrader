/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.MaxDefaultDBValue;
import grafioschtrader.dto.MaxDefaultDBValueWithKey;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.xml.bind.annotation.XmlRootElement;

@Schema(description = "Contains a global setting configuration")
@Entity
@Table(name = Globalparameters.TABNAME)
@XmlRootElement
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Globalparameters implements Serializable {

  public static final String TABNAME = "globalparameters";

  private static final String GT_PREFIX = "gt.";

  public static final String GLOB_KEY_CURRENCY_PRECISION = GT_PREFIX + "currency.precision";
  public static final String GLOB_KEY_TASK_DATA_DAYS_PRESERVE = GT_PREFIX + "task.data.days.preserve";

  // Connector settings
  public static final String GLOB_KEY_CRYPTOCURRENCY_HISTORY_CONNECTOR = GT_PREFIX + "cryptocurrency.history.connector";
  public static final String GLOB_KEY_CRYPTOCURRENCY_INTRA_CONNECTOR = GT_PREFIX + "cryptocurrency.intra.connector";

  public static final String GLOB_KEY_CURRENCY_HISTORY_CONNECTOR = GT_PREFIX + "currency.history.connector";
  public static final String GLOB_KEY_CURRENCY_INTRA_CONNECTOR = GT_PREFIX + "currency.intra.connector";
  public static final String GLOB_KEY_INTRA_RETRY = GT_PREFIX + "intra.retry";
  public static final String GLOB_KEY_HISTORY_RETRY = GT_PREFIX + "history.retry";
  public static final String GLOB_KEY_DIVIDEND_RETRY = GT_PREFIX + "dividend.retry";
  public static final String GLOB_KEY_SPLIT_RETRY = GT_PREFIX + "split.retry";
  public static final String GLOB_KEY_START_FEED_DATE = GT_PREFIX + "core.data.feed.start.date";
  public static final String GLOB_KEY_SC_INTRA_UPDATE_TIMEOUT_SECONDS = GT_PREFIX + "sc.intra.update.timeout.seconds";
  public static final String GLOB_KEY_W_INTRA_UPDATE_TIMEOUT_SECONDS = GT_PREFIX + "w.intra.update.timeout.seconds";
  public static final String GLOB_KEY_HISTORY_MAX_FILLDAYS_CURRENCY = GT_PREFIX + "history.max.filldays.currency";

  // History quote quality. Date which last time when a history quality update was
  // happened
  public static final String GLOB_KEY_HISTORYQUOTE_QUALITY_UPDATE_DATE = GT_PREFIX + "historyquote.quality.update.date";
  public static final String GLOB_KEY_YOUNGES_SPLIT_APPEND_DATE = GT_PREFIX + "securitysplit.append.date";

  // The idGTNet for this Server in GTNet  
  public static final String GLOB_KEY_GTNET_MY_ENTRY_ID = GT_PREFIX + "gtnet.my.entry.id";
  
  // Alert bitmap for sending emai
  public static final String GLOB_KEY_ALERT_MAIL = GT_PREFIX + "alert.mail.bitmap";
  
  // Tenant data entity limits
  private static final String MAX = "max.";
  public static final String GLOB_KEY_MAX_CASH_ACCOUNT = GT_PREFIX + MAX + "cash.account";
  public static final String GLOB_KEY_MAX_PORTFOLIO = GT_PREFIX + MAX + "portfolio";
  public static final String GLOB_KEY_MAX_SECURITY_ACCOUNT = GT_PREFIX + MAX + "security.account";
  public static final String GLOB_KEY_MAX_SECURITIES_CURRENCIES = GT_PREFIX + MAX + "securities.currencies";
  public static final String GLOB_KEY_MAX_WATCHTLIST = GT_PREFIX + MAX + "watchlist";
  public static final String GLOB_KEY_MAX_WATCHLIST_LENGTH = GT_PREFIX + MAX + "watchlist.length";
  public static final String GLOB_KEY_MAX_CORRELATION_SET = GT_PREFIX + MAX + "correlation.set";
  public static final String GLOB_KEY_MAX_CORRELATION_INSTRUMENTS = GT_PREFIX + MAX + "correlation.instruments";

  
  public static final String GLOB_KEY_UPDATE_PRICE_BY_EXCHANGE = GT_PREFIX + "update.price.by.exchange";
  
  // User day entity limits
  public static final String GT_LIMIT_DAY = GT_PREFIX + "limit.day.";

  public static final String GLOB_KEY_LIMIT_DAY_ASSETCLASS = GT_LIMIT_DAY + Assetclass.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_STOCKEXCHANGE = GT_LIMIT_DAY + Stockexchange.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_PROPOSEUSERTASK = GT_LIMIT_DAY + ProposeUserTask.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_SECURITY = GT_LIMIT_DAY + Security.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_CURRENCYPAIR = GT_LIMIT_DAY + Currencypair.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_MAIL_SENDBOX = GT_LIMIT_DAY + MailSendbox.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_IMPORTTRANSACTIONTEMPLATE = GT_LIMIT_DAY
      + ImportTransactionTemplate.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_IMPORTTRANSACTIONPLATFORM = GT_LIMIT_DAY
      + ImportTransactionPlatform.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_TRADINGPLATFORMPLAN = GT_LIMIT_DAY
      + TradingPlatformPlan.class.getSimpleName();

  // Tenant regulatory violations
  public static final String GLOB_KEY_MAX_LIMIT_EXCEEDED_COUNT = GT_PREFIX + "max.limit.request.exceeded.count";
  public static final String GLOB_KEY_MAX_SECURITY_BREACH_COUNT = GT_PREFIX + "max.security.breach.count";

  public static final String GLOB_KEY_SOURCE_DEMO_ID_TENANT_DE = GT_PREFIX + "source.demo.idtenant.de";
  public static final String GLOB_KEY_SOURCE_DEMO_ID_TENANT_EN = GT_PREFIX + "source.demo.idtenant.en";

  private static final Map<String, MaxDefaultDBValue> defaultLimitMap = new HashMap<>();

  static {
    // Set tenant data entity limits in total on not shared entries.
    defaultLimitMap.put(GLOB_KEY_MAX_CASH_ACCOUNT, new MaxDefaultDBValue(30));
    defaultLimitMap.put(GLOB_KEY_MAX_PORTFOLIO, new MaxDefaultDBValue(20));
    defaultLimitMap.put(GLOB_KEY_MAX_SECURITY_ACCOUNT, new MaxDefaultDBValue(20));
    defaultLimitMap.put(GLOB_KEY_MAX_SECURITIES_CURRENCIES, new MaxDefaultDBValue(2000));
    defaultLimitMap.put(GLOB_KEY_MAX_WATCHTLIST, new MaxDefaultDBValue(30));
    defaultLimitMap.put(GLOB_KEY_MAX_WATCHLIST_LENGTH, new MaxDefaultDBValue(200));
    defaultLimitMap.put(GLOB_KEY_MAX_CORRELATION_SET, new MaxDefaultDBValue(10));
    defaultLimitMap.put(GLOB_KEY_MAX_CORRELATION_INSTRUMENTS, new MaxDefaultDBValue(20));

    // Set tenant regulations violations, with daily limits on shared entries
    defaultLimitMap.put(GLOB_KEY_LIMIT_DAY_ASSETCLASS, new MaxDefaultDBValue(10));
    defaultLimitMap.put(GLOB_KEY_LIMIT_DAY_STOCKEXCHANGE, new MaxDefaultDBValue(10));
    defaultLimitMap.put(GLOB_KEY_LIMIT_DAY_PROPOSEUSERTASK, new MaxDefaultDBValue(10));
    defaultLimitMap.put(GLOB_KEY_LIMIT_DAY_SECURITY, new MaxDefaultDBValue(50));
    defaultLimitMap.put(GLOB_KEY_LIMIT_DAY_MAIL_SENDBOX, new MaxDefaultDBValue(200));
    defaultLimitMap.put(GLOB_KEY_LIMIT_DAY_CURRENCYPAIR, new MaxDefaultDBValue(15));
    defaultLimitMap.put(GLOB_KEY_LIMIT_DAY_IMPORTTRANSACTIONTEMPLATE, new MaxDefaultDBValue(10));
    defaultLimitMap.put(GLOB_KEY_LIMIT_DAY_IMPORTTRANSACTIONPLATFORM, new MaxDefaultDBValue(3));
    defaultLimitMap.put(GLOB_KEY_LIMIT_DAY_TRADINGPLATFORMPLAN, new MaxDefaultDBValue(3));
    // TODO Other entities -> otherwise null pointer exception

  }

  public static final String DEFAULT_CURRENCY_PRECISION = "BTC=8,ETH=7,JPY=0,ZAR=0";
  public static final int DEFAULT_TASK_DATA_DAYS_PRESERVE = 10;
  public static final short DEFAULT_INTRA_RETRY = 4;
  public static final short DEFAULT_HISTORY_RETRY = 4;
  public static final short DEFAULT_DIVIDEND_RETRY = 2;
  public static final short DEFAULT_SPLIT_RETRY = 2;
  public static final int DEFAULT_SC_INTRA_UPDATE_TIMEOUT_SECONDS = 300;
  public static final int DEFAULT_W_INTRA_UPDATE_TIMEOUT_SECONDS = 1200;

  public static final int DEFUALT_MAX_WATCHLIST = 30;

  public static final int DEFAULT_MAX_LIMIT_EXCEEDED_COUNT = 20;
  public static final int DEFAULT_MAX_SECURITY_BREACH_COUNT = 5;
  public static final Date DEFAULT_START_FEED_DATE = new GregorianCalendar(2000, Calendar.JANUARY, 1).getTime();

  public static final int DEFAULT_ALERT_MAIL = Integer.MAX_VALUE;
  
  public static final int DEFAULT_HISTORY_MAX_FILLDAYS_CURRENCY = 5;
  
  public static final int DEFAULT_UPDATE_PRICE_BY_EXCHANGE = 0;

  private static final long serialVersionUID = 1L;

  @Id
  @Basic(optional = false)
  @NotNull
  @Size(min = 1, max = 45)
  @Column(name = "property_name")
  private String propertyName;

  @Column(name = "property_int")
  private Integer propertyInt;

  @Size(max = 25)
  @Column(name = "property_string")
  private String propertyString;

  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  @Column(name = "property_date")
  private LocalDate propertyDate;

  @Lob
  @Column(name = "property_blob")
  private byte[] propertyBlob;

  @Schema(description = "This property will be changed by the system")
  @Column(name = "changed_by_system")
  private boolean changedBySystem = false;

  public Globalparameters() {
  }

  public Globalparameters(String propertyName) {
    this.propertyName = propertyName;
  }

  public Globalparameters(String propertyName, LocalDate propertyDate, boolean changedBySystem) {
    this.propertyName = propertyName;
    this.propertyDate = propertyDate;
    this.changedBySystem = changedBySystem;
  }

  public String getPropertyName() {
    return propertyName;
  }

  public Globalparameters setPropertyName(String propertyName) {
    this.propertyName = propertyName;
    return this;
  }

  public Integer getPropertyInt() {
    return propertyInt;
  }

  public Globalparameters setPropertyInt(Integer propertyInt) {
    this.propertyInt = propertyInt;
    return this;
  }

  public String getPropertyString() {
    return propertyString;
  }

  public Globalparameters setPropertyString(String propertyString) {
    this.propertyString = propertyString;
    return this;
  }

  public LocalDate getPropertyDate() {
    return propertyDate;
  }

  public Globalparameters setPropertyDate(LocalDate propertyDate) {
    this.propertyDate = propertyDate;
    return this;
  }

  public byte[] getPropertyBlob() {
    return propertyBlob;
  }

  public Globalparameters setPropertyBlob(byte[] propertyBlob) {
    this.propertyBlob = propertyBlob;
    return this;
  }

  public boolean isChangedBySystem() {
    return changedBySystem;
  }

  public void setChangedBySystem(boolean changedBySystem) {
    this.changedBySystem = changedBySystem;
  }

  public static String getKeyFromMsgKey(String msgKey) {
    return GT_PREFIX + msgKey.toLowerCase().replaceAll("_", ".");
  }

  public static MaxDefaultDBValueWithKey getMaxDefaultDBValueByMsgKey(final String msgKey) {
    return getMaxDefaultDBValueByKey(getKeyFromMsgKey(msgKey));
  }

  public static MaxDefaultDBValueWithKey getMaxDefaultDBValueByKey(final String key) {
    return new MaxDefaultDBValueWithKey(key, defaultLimitMap.get(key));
  }

  public static void resetDBValueOfKey(final String key) {
    MaxDefaultDBValue mddv = defaultLimitMap.get(key);
    if(mddv != null) {
      mddv.setDbValue(null);
    }
  }

  public void replaceExistingPropertyValue(Globalparameters gpNew) {
    if (gpNew.getPropertyDate() != null && propertyDate != null) {
      this.propertyDate = gpNew.getPropertyDate();
    } else if (gpNew.getPropertyInt() != null && propertyInt != null) {
      this.propertyInt = gpNew.getPropertyInt();
    } else if (gpNew.getPropertyString() != null && propertyString != null) {
      this.propertyString = gpNew.getPropertyString();
    } else if (gpNew.getPropertyBlob() != null && propertyBlob != null) {
      setPropertyBlob(gpNew.getPropertyBlob());
    } else {
      throw new IllegalArgumentException();
    }
  }

  @Override
  public int hashCode() {
    int hash = 0;
    hash += (propertyName != null ? propertyName.hashCode() : 0);
    return hash;
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof Globalparameters)) {
      return false;
    }
    Globalparameters other = (Globalparameters) object;
    if ((this.propertyName == null && other.propertyName != null)
        || (this.propertyName != null && !this.propertyName.equals(other.propertyName))) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "grafioschtrader.entities.Globalparameters[ propertyName=" + propertyName + " ]";
  }

}
