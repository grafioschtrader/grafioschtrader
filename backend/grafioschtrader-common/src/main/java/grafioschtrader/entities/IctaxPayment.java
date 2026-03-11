package grafioschtrader.entities;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * A single dividend or interest payment entry from an ICTax Kursliste XML file.
 */
@Entity
@Table(name = IctaxPayment.TABNAME)
public class IctaxPayment {

  public static final String TABNAME = "ictax_payment";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_ictax_payment")
  private Integer idIctaxPayment;

  @JsonIgnore
  @ManyToOne
  @JoinColumn(name = "id_ictax_data", nullable = false)
  private IctaxSecurityTaxData ictaxSecurityTaxData;

  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "payment_date")
  private LocalDate paymentDate;

  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "ex_date")
  private LocalDate exDate;

  @Column(name = "currency", length = 3)
  private String currency;

  @Column(name = "payment_value")
  private Double paymentValue;

  @Column(name = "exchange_rate")
  private Double exchangeRate;

  @Column(name = "payment_value_chf")
  private Double paymentValueChf;

  @Column(name = "capital_gain")
  private Boolean capitalGain;

  @Transient
  @JsonIgnore
  private double computedUnitsAtDate;

  @Transient
  @JsonIgnore
  private double computedTotalPaymentChf;

  public IctaxPayment() {
  }

  public Integer getIdIctaxPayment() {
    return idIctaxPayment;
  }

  public void setIdIctaxPayment(Integer idIctaxPayment) {
    this.idIctaxPayment = idIctaxPayment;
  }

  public IctaxSecurityTaxData getIctaxSecurityTaxData() {
    return ictaxSecurityTaxData;
  }

  public void setIctaxSecurityTaxData(IctaxSecurityTaxData ictaxSecurityTaxData) {
    this.ictaxSecurityTaxData = ictaxSecurityTaxData;
  }

  public LocalDate getPaymentDate() {
    return paymentDate;
  }

  public void setPaymentDate(LocalDate paymentDate) {
    this.paymentDate = paymentDate;
  }

  public LocalDate getExDate() {
    return exDate;
  }

  public void setExDate(LocalDate exDate) {
    this.exDate = exDate;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Double getPaymentValue() {
    return paymentValue;
  }

  public void setPaymentValue(Double paymentValue) {
    this.paymentValue = paymentValue;
  }

  public Double getExchangeRate() {
    return exchangeRate;
  }

  public void setExchangeRate(Double exchangeRate) {
    this.exchangeRate = exchangeRate;
  }

  public Double getPaymentValueChf() {
    return paymentValueChf;
  }

  public void setPaymentValueChf(Double paymentValueChf) {
    this.paymentValueChf = paymentValueChf;
  }

  public Boolean getCapitalGain() {
    return capitalGain;
  }

  public void setCapitalGain(Boolean capitalGain) {
    this.capitalGain = capitalGain;
  }

  public double getComputedUnitsAtDate() {
    return computedUnitsAtDate;
  }

  public void setComputedUnitsAtDate(double computedUnitsAtDate) {
    this.computedUnitsAtDate = computedUnitsAtDate;
  }

  public double getComputedTotalPaymentChf() {
    return computedTotalPaymentChf;
  }

  public void setComputedTotalPaymentChf(double computedTotalPaymentChf) {
    this.computedTotalPaymentChf = computedTotalPaymentChf;
  }
}
