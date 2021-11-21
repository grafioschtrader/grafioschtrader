package grafioschtrader.platformimport;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.EnumSet;
import java.util.Map;

import grafioschtrader.common.DateHelper;
import grafioschtrader.types.ImportKnownOtherFlags;
import grafioschtrader.types.TransactionType;
import grafioschtrader.validation.ISINValidator;

/**
 * Is filled with the data of a import form.
 *
 * @author Hugo Graf
 *
 */
public class ImportProperties {

  public static final String ORDER = "order";

  /**
   * Date of Transaction
   */
  private Date datetime;

  /**
   * Date part of Transaction when it comes separated as date and time
   */
  private LocalDate date;

  /**
   * Time part of Transaction when it comes separated as date and time
   */
  private LocalTime time;

  /**
   * Date of ex dividend
   */
  private Date exdiv;

  /**
   * Transaction type
   */
  private String transType;

  /**
   * Name of the security
   */
  private String sn;

  /**
   * Is used to for finding the right security paper
   */
  private String isin;

  /**
   * Symbol is used when there is no ISIN
   */
  private String symbol;
  /**
   * The number of units
   */
  private Double units;
  /**
   * The price per unit
   */
  private Double quotation;

  /**
   * Accrued interest
   */
  private Double ac;

  /**
   * Currency of the investment product
   */
  private String cin;

  /**
   * Currency of the cash account
   */
  private String cac;

  /**
   * Currency exchange rate
   */
  private Double cex;

  /**
   * Transaction cost 1
   */
  private Double tc1;

  /**
   * Transaction cost 2
   */
  private Double tc2;

  /**
   * Transaction Tax 1
   */
  private Double tt1;

  /**
   * Transaction Tax 1
   */
  private Double tt2;

  /**
   * Currency for tax and transaction cost
   */
  private String cct;

  /**
   * Total amount
   */
  private Double ta;

  /**
   * May be used in csv do connect to rows together
   */
  private String order;

  /**
   * User defined field
   */
  private String sf1;

  /**
   * Some prices are given by percentage, for example bonds
   */
  private String per;

  /**
   * A template may handle different transaction types.
   */
  private final Map<String, TransactionType> transactionTypesMap;
  private final EnumSet<ImportKnownOtherFlags> knownOtherFlags;
  private Integer fileOrLineNumber;

  private String ignoreTaxOnDivInt;

  public ImportProperties(Map<String, TransactionType> transactionTypesMap,
      EnumSet<ImportKnownOtherFlags> knownOtherFlags, String ignoreTaxOnDivInt) {
    this.transactionTypesMap = transactionTypesMap;
    this.knownOtherFlags = knownOtherFlags;
    this.ignoreTaxOnDivInt = ignoreTaxOnDivInt;
  }

  public ImportProperties(Map<String, TransactionType> transactionTypesMap,
      EnumSet<ImportKnownOtherFlags> knownOtherFlags, Integer fileOrLineNumber, String ignoreTaxOnDivInt) {
    this(transactionTypesMap, knownOtherFlags, ignoreTaxOnDivInt);
    this.fileOrLineNumber = fileOrLineNumber;
  }

  public Date getDatetime() {
    if (datetime == null && date != null && time != null) {
      datetime = DateHelper.convertToDateViaInstant(LocalDateTime.of(date, time));
    }
    return datetime;
  }

  public void setDatetime(Date date) {
    this.datetime = date;
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }

  public void setTime(LocalTime time) {
    this.time = time;
  }

  public Date getExdiv() {
    return exdiv;
  }

  public void setExdiv(Date exdiv) {
    this.exdiv = exdiv;
  }

  public String getTransType() {
    return transType;
  }

  public void setTransType(String transType) {
    this.transType = transType;
    if (transactionTypesMap.get(this.transType) == null) {
      throw new IllegalArgumentException(transType + " not accepted");
    }
    if (ignoreTaxOnDivInt != null && this.transType.equals(ignoreTaxOnDivInt)) {
      knownOtherFlags.add(ImportKnownOtherFlags.CAN_NO_TAX_ON_DIVIDEND_INTEREST);
    }
  }

  public TransactionType getTransactionType() {
    return this.transType != null ? transactionTypesMap.get(this.transType) : null;
  }

  public String getIsin() {
    return isin;
  }

  public void setIsin(String isin) {
    ISINValidator iSINValidator = new ISINValidator();
    if (iSINValidator.isValid(isin, null)) {
      this.isin = isin;
    } else {
      throw new IllegalArgumentException(isin + " not accepted");
    }
  }

  public String getSn() {
    return sn;
  }

  public void setSn(String sn) {
    this.sn = sn;
  }

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public Double getUnits() {
    return units;
  }

  public void setUnits(Double units) {
    this.units = units;
  }

  public Double getQuotation() {
    return quotation;
  }

  public void setQuotation(Double quotation) {
    this.quotation = quotation;
  }

  public Double getAc() {
    return ac;
  }

  public void setAc(Double ac) {
    this.ac = ac;
  }

  public String getCin() {
    return cin;
  }

  public void setCin(String cin) {
    this.cin = cin;
  }

  public String getCac() {
    return cac;
  }

  public void setCac(String cac) {
    this.cac = cac;
  }

  public Double getCex() {
    return cex;
  }

  public void setCex(Double cex) {
    this.cex = cex;
  }

  public Double getTc1() {
    return tc1;
  }

  public void setTc1(Double tc1) {
    this.tc1 = tc1;
  }

  public Double getTc2() {
    return tc2;
  }

  public void setTc2(Double tc2) {
    this.tc2 = tc2;
  }

  public Double getTt1() {
    return tt1;
  }

  public void setTt1(Double tt1) {
    this.tt1 = tt1;
  }

  public Double getTt2() {
    return tt2;
  }

  public void setTt2(Double tt2) {
    this.tt2 = tt2;
  }

  public String getCct() {
    return cct;
  }

  public void setCct(String cct) {
    this.cct = cct;
  }

  public Double getTa() {
    return ta;
  }

  public void setTa(Double ta) {
    this.ta = ta;
  }

  public String getOrder() {
    return order;
  }

  public void setOrder(String order) {
    this.order = order;
  }

  public String getSf1() {
    return sf1;
  }

  public void setSf1(String sf1) {
    this.sf1 = sf1;
  }

  public String getPer() {
    return per;
  }

  public void setPer(String per) {
    this.per = per;
  }

  public boolean isPercentage() {
    return per == null ? false : true;
  }

  public boolean maybeEmpty() {
    return datetime == null && ta == null;
  }

  public Integer getFileOrLineNumber() {
    return fileOrLineNumber;
  }

  public EnumSet<ImportKnownOtherFlags> getKnownOtherFlags() {
    return knownOtherFlags;
  }

  @Override
  public String toString() {
    return "ImportProperties [date=" + datetime + ", transType=" + transType + ", isin=" + isin + ", units=" + units
        + ", quotation=" + quotation + ", ac=" + ac + ", cin=" + cin + ", cac=" + cac + ", cex=" + cex + ", tc1=" + tc1
        + ", tc2=" + tc2 + ", tt1=" + tt1 + ", tt2=" + tt2 + ", ta=" + ta + ", sf1=" + sf1 + ", per=" + per
        + ", transactionTypesMap=" + transactionTypesMap + "]";
  }

}
