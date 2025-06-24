package grafioschtrader.platformimport;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.EnumSet;
import java.util.Map;

import grafiosch.common.DateHelper;
import grafioschtrader.types.ImportKnownOtherFlags;
import grafioschtrader.types.TransactionType;
import grafioschtrader.validation.ISINValidator;

/**
 * Container for financial transaction data extracted from trading platform documents (PDF or CSV).
 * 
 * <p>
 * This class holds all the parsed transaction information including dates, security identification, quantities, prices,
 * costs, taxes, and currencies. It provides validation for critical fields like ISIN codes and transaction types, and
 * handles special cases like percentage-based bond pricing.
 * </p>
 */
public class ImportProperties {

  /** Field name constant for order linking in CSV imports. */
  public static final String ORDER = "order";

  /** Date of Transaction */
  private Date datetime;

  /** Date component when date and time are provided separately. */
  private LocalDate date;

  /** Time part of Transaction when it comes separated as date and time */
  private LocalTime time;

  /** Date of ex dividend */
  private Date exdiv;

  /** Transaction type as text from the document (validated against transactionTypesMap). */
  private String transType;

  /** Security name for display purposes. */
  private String sn;

  /** Is used to for finding the right security paper */
  private String isin;

  /** Symbol is used when there is no ISIN */
  private String symbol;

  /** The number of units */
  private Double units;

  /** Price per unit of the security. */
  private Double quotation;

  /** Accrued interest amount for bond transactions. */
  private Double ac;

  /** Currency of the investment security. */
  private String cin;

  /** Currency of the cash account (mandatory field). */
  private String cac;

  /** Exchange rate when transaction involves currency conversion. */
  private Double cex;

  /** Primary transaction cost (broker fees, commissions). */
  private Double tc1;

  /** Secondary transaction cost if multiple fees apply. */
  private Double tc2;

  /** Discount amount deducted from transaction costs. */
  private Double reduce;

  /** Primary tax amount. */
  private Double tt1;

  /** Secondary tax amount if multiple taxes apply. */
  private Double tt2;

  /** Currency for transaction costs and taxes. */
  private String cct;

  /** Total transaction amount including all costs and taxes. */
  private Double ta;

  /** Order identifier for linking related CSV transaction rows. */
  private String order;

  /** User-defined field for custom data extraction. */
  private String sf1;

  /** Indicator for percentage-based pricing (typically for bonds). */
  private String per;

  /** Maps transaction type text to internal TransactionType enums. */
  private final Map<String, TransactionType> transactionTypesMap;

  /** Set of processing flags that modify transaction behavior. */
  private final EnumSet<ImportKnownOtherFlags> knownOtherFlags;

  /** File or line number for tracking source location. */
  private Integer fileOrLineNumber;

  /** Transaction type text that should be marked as tax-exempt. */
  private String ignoreTaxOnDivInt;

  /**
   * Creates transaction properties with the specified configuration.
   * 
   * @param transactionTypesMap Mapping from document text to transaction types
   * @param knownOtherFlags     Processing flags for special transaction handling
   * @param ignoreTaxOnDivInt   Transaction type text for tax exemption
   */
  public ImportProperties(Map<String, TransactionType> transactionTypesMap,
      EnumSet<ImportKnownOtherFlags> knownOtherFlags, String ignoreTaxOnDivInt) {
    this.transactionTypesMap = transactionTypesMap;
    this.knownOtherFlags = knownOtherFlags;
    this.ignoreTaxOnDivInt = ignoreTaxOnDivInt;
  }

  /**
   * Creates transaction properties with source tracking information.
   * 
   * @param transactionTypesMap Mapping from document text to transaction types
   * @param knownOtherFlags     Processing flags for special transaction handling
   * @param fileOrLineNumber    Source file or line number for tracking
   * @param ignoreTaxOnDivInt   Transaction type text for tax exemption
   */
  public ImportProperties(Map<String, TransactionType> transactionTypesMap,
      EnumSet<ImportKnownOtherFlags> knownOtherFlags, Integer fileOrLineNumber, String ignoreTaxOnDivInt) {
    this(transactionTypesMap, knownOtherFlags, ignoreTaxOnDivInt);
    this.fileOrLineNumber = fileOrLineNumber;
  }

  /**
   * Returns the transaction date and time, combining separate date/time components if needed.
   * 
   * @return Complete transaction timestamp
   */
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

  public Double getReduce() {
    return reduce;
  }

  public void setReduce(Double reduce) {
    this.reduce = reduce;
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

  /**
   * Sets and validates the transaction type text against the configured mapping. Automatically applies tax exemption
   * flags if applicable.
   * 
   * @param transType Transaction type text from document
   * @throws IllegalArgumentException if transaction type is not in the mapping
   */
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

  /**
   * Sets and validates the ISIN code.
   * 
   * @param isin ISIN code to validate and set
   * @throws IllegalArgumentException if ISIN format is invalid
   */
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
