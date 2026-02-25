package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.BaseConstants;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafioschtrader.GlobalConstants;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Defines which instrument types a security account is allowed to trade, and optionally within which date range.
 * The {@code specInvestInstrument} field is always required and identifies the specific instrument type.
 * A NULL value in {@code categoryType} acts as a wildcard matching all asset class categories.
 * {@code dateFrom} defaults to {@link GlobalConstants#OLDEST_TRADING_DAY} (the earliest trading day GT supports).
 * A NULL {@code dateTo} means "forever".
 * If a security account has no trading period rows at all, all trading is allowed (backward compatibility).
 */
@Entity
@Table(name = SecaccountTradingPeriod.TABNAME)
public class SecaccountTradingPeriod implements Serializable {

  public static final String TABNAME = "securityaccount_trading_period";
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_secaccount_trading_period")
  private Integer idSecaccountTradingPeriod;

  @JsonIgnore
  @Column(name = "id_securitycash_account", nullable = false, insertable = false, updatable = false)
  private Integer idSecuritycashAccount;

  @PropertyAlwaysUpdatable
  @Column(name = "category_type")
  private Byte categoryType;

  @PropertyAlwaysUpdatable
  @Column(name = "spec_invest_instrument", nullable = false)
  private byte specInvestInstrument;

  @PropertyAlwaysUpdatable
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "date_from", nullable = false)
  private LocalDate dateFrom = LocalDate.parse(GlobalConstants.OLDEST_TRADING_DAY);

  @PropertyAlwaysUpdatable
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "date_to")
  private LocalDate dateTo;

  public Integer getIdSecaccountTradingPeriod() {
    return idSecaccountTradingPeriod;
  }

  public void setIdSecaccountTradingPeriod(Integer idSecaccountTradingPeriod) {
    this.idSecaccountTradingPeriod = idSecaccountTradingPeriod;
  }

  public Integer getIdSecuritycashAccount() {
    return idSecuritycashAccount;
  }

  public void setIdSecuritycashAccount(Integer idSecuritycashAccount) {
    this.idSecuritycashAccount = idSecuritycashAccount;
  }

  public AssetclassType getCategoryType() {
    return categoryType == null ? null : AssetclassType.getAssetClassTypeByValue(categoryType);
  }

  public void setCategoryType(AssetclassType assetClassType) {
    this.categoryType = assetClassType == null ? null : assetClassType.getValue();
  }

  public SpecialInvestmentInstruments getSpecInvestInstrument() {
    return SpecialInvestmentInstruments.getSpecialInvestmentInstrumentsByValue(specInvestInstrument);
  }

  public void setSpecInvestInstrument(SpecialInvestmentInstruments specInvestInstrument) {
    this.specInvestInstrument = specInvestInstrument.getValue();
  }

  public LocalDate getDateFrom() {
    return dateFrom;
  }

  public void setDateFrom(LocalDate dateFrom) {
    this.dateFrom = dateFrom;
  }

  public LocalDate getDateTo() {
    return dateTo;
  }

  public void setDateTo(LocalDate dateTo) {
    this.dateTo = dateTo;
  }
}
