package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.common.DateHelper;
import grafioschtrader.types.CreateType;
import grafioschtrader.validation.ValidCurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Schema(description = """
    Contains the dividends as they are paid for the individual shares. These are
    mainly used for evaluations or messages to the user. They are filled by
    providers and are not edited manually. They also do not lead to transactions
    for tenants, as tax deductions vary greatly from country to country.""")
@Entity
@Table(name = Dividend.TABNAME)
public class Dividend extends DividendSplit implements Serializable {

  public static final String TABNAME = "dividend";

  private static final long serialVersionUID = 1L;
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_dividend")
  private Integer idDividend;

  @Schema(description = """
      Investors who buy the stock on or after this date will not get the upcoming
      dividend; instead, it goes to the seller. To receive the dividend, you must
      own shares before the ex-dividend date.""")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "ex_date")
  private LocalDate exDate;

  @Schema(description = """
      The payment date is the day when the dividend is actually paid to shareholders who owned the stock before
      the ex-dividend date. It typically occurs a few days or weeks after the ex-dividend date.""")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "pay_date")
  private LocalDate payDate;

  @Schema(description = """
      A data source may only provide real dividends. In such a case, the split-adjusted amount is calculated from this"
      Even if the dividend were automatically transferred into transactions, this amount would be relevant.""")
  @Column(name = "amount")
  private Double amount;

  @Schema(description = """
       Since GT always works with split-adjusted data, the dividends must also be split-adjusted,
       otherwise an addition with price data would lead to incorrect results.
      """)
  @Column(name = "amount_adjusted")
  private Double amountAdjusted;

  @Schema(description = "Currency of security, ISO 4217")
  @ValidCurrencyCode
  @Column(name = "currency")
  private String currency;

  public Dividend() {
  }

  public Dividend(Integer idSecuritycurrency, LocalDate exDate, LocalDate payDate, Double amount, Double amountAdjusted,
      String currency, CreateType createType) {
    super(idSecuritycurrency, createType);
    this.exDate = exDate;
    this.payDate = payDate;
    this.amount = amount;
    this.amountAdjusted = amountAdjusted;
    this.currency = currency;
  }

  public Integer getIdDividend() {
    return idDividend;
  }

  public void setIdDividend(Integer idDividend) {
    this.idDividend = idDividend;
  }

  public LocalDate getExDate() {
    return exDate;
  }

  public void setExDate(LocalDate exDate) {
    this.exDate = exDate;
  }

  public LocalDate getPayDate() {
    return payDate;
  }

  public void setPayDate(LocalDate payDate) {
    this.payDate = payDate;
  }

  public Double getAmount() {
    return amount;
  }

  public void setAmount(Double amount) {
    this.amount = amount;
  }

  public Double getAmountAdjusted() {
    return amountAdjusted;
  }

  public void setAmountAdjusted(Double amountAdjusted) {
    this.amountAdjusted = amountAdjusted;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  @Override
  public Integer getId() {
    return this.idDividend;
  }

  @Override
  public String toString() {
    return "Dividend [idDividend=" + idDividend + ", idSecuritycurrency=" + idSecuritycurrency + ", exDate=" + exDate
        + ", payDate=" + payDate + ", amount=" + amount + ", currency=" + currency + "]";
  }

  @Override
  public Date getEventDate() {
    return DateHelper.getDateFromLocalDate(exDate);
  }

}
