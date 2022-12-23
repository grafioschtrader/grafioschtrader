package grafioschtrader.entities;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.platformimport.ImportProperties;
import grafioschtrader.platformimport.ImportTransactionHelper;
import grafioschtrader.types.ImportKnownOtherFlags;
import grafioschtrader.types.TransactionType;
import grafioschtrader.validation.ValidISIN;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Size;

/**
 * Transactions from csv or pdf file are imported in this table. Normally a csv
 * will creates many and a pdf may create only one.
 *
 *
 * @author Hugo Graf
 *
 */
@Entity
@Table(name = ImportTransactionPos.TABNAME)
public class ImportTransactionPos extends TenantBaseID implements Comparable<ImportTransactionPos> {

  public static final String TABNAME = "imp_trans_pos";

  private static final String CSV_FILE = "C";
  private static final String PDF_FILE = "P";
  private static final String PDF_TEXT_FILE = "T";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_trans_pos")
  private Integer idTransactionPos;

  @JsonIgnore
  @Column(name = "id_tenant")
  private Integer idTenant;

  @Column(name = "id_trans_head")
  private Integer idTransactionHead;

  @Column(name = "transaction_type")
  private Byte transactionType;

  @Basic(optional = false)
  @Column(name = "transaction_type_imp")
  private String transactionTypeImp;

  @Basic(optional = false)
  @Column(name = "transaction_time")
  @Temporal(TemporalType.TIMESTAMP)
  private Date transactionTime;

  @Column(name = "ex_date")
  @Temporal(TemporalType.DATE)
  private Date exDate;

  @JoinColumn(name = "id_cash_account", referencedColumnName = "id_securitycash_account")
  @ManyToOne
  private Cashaccount cashaccount;

  @Size(max = 3)
  @Column(name = "currency_account")
  private String currencyAccount;

  @Size(min = 1, max = 32)
  @Column(name = "cash_account_imp")
  private String cashAccountImp;

  @Column(name = "currency_ex_rate")
  private Double currencyExRate;

  @Size(max = 3)
  @Column(name = "currency_security")
  private String currencySecurity;

  @ValidISIN
  @Column(name = "isin")
  private String isin;

  @Column(name = "symbol_imp")
  private String symbolImp;

  @Column(name = "security_name_imp")
  private String securityNameImp;

  @OneToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "id_securitycurrency")
  private Security security;

  @Column(name = "units")
  private Double units;

  @Column(name = "quotation")
  private Double quotation;

  @Column(name = "tax_cost")
  private Double taxCost;

  @Column(name = "transaction_cost")
  private Double transactionCost;

  @Column(name = "currency_cost")
  private String currencyCost;

  @Column(name = "cashaccount_amount")
  private Double cashaccountAmount;

  @Column(name = "accepted_total_diff")
  private Double acceptedTotalDiff;

  @Column(name = "accrued_interest")
  private Double accruedInterest;

  @Column(name = "field1_string_imp")
  private String field1StringImp;

  @Column(name = "ready_for_transaction")
  private boolean readyForTransaction;

  @Schema(description = "The idTransaction of the new create transaction")
  @Column(name = "id_transaction")
  private Integer idTransaction;

  @Schema(description = "The import position is maybe reflectin an existing transaction. It is the id of this transaction")
  @Column(name = "id_transaction_maybe")
  private Integer idTransactionMaybe;

  @Column(name = "id_trans_imp_template")
  private Integer idTransactionImportTemplate;

  @Column(name = "id_file_part")
  private Integer idFilePart;

  @Column(name = "file_name_original")
  private String fileNameOriginal;

  @JoinColumn(name = "id_trans_pos")
  @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
  private List<ImportTransactionPosFailed> importTransactionPosFailedList;

  @Schema(description = "The idTransaction of a conneted transaction")
  @Column(name = "con_id_trans_pos")
  private Integer connectedIdTransactionPos;

  @Column(name = "known_other_flags")
  private Integer knownOtherFlags;

  @Column(name = "transaction_error")
  private String transactionError;

  @Transient
  private double calcCashaccountAmount;

  @Transient
  private double diffCashaccountAmount;

  public ImportTransactionPos() {
  }

  public ImportTransactionPos(Integer idTenant, String fileNameOriginal, Integer idTransactionHead) {
    this.idTenant = idTenant;
    this.fileNameOriginal = fileNameOriginal;
    this.idTransactionHead = idTransactionHead;
  }

  public ImportTransactionPos(Integer idTenant, String fileNameOriginal, Integer idTransactionHead,
      Integer idTransactionImportTemplate) {
    this(idTenant, fileNameOriginal, idTransactionHead);
    this.idTransactionImportTemplate = idTransactionImportTemplate;
  }

  public Integer getIdTransactionPos() {
    return idTransactionPos;
  }

  public void setIdTransactionPos(Integer idTransactionPos) {
    this.idTransactionPos = idTransactionPos;
  }

  public Integer getIdTransactionHead() {
    return idTransactionHead;
  }

  public void setIdTransactionHead(Integer idTransactionHead) {
    this.idTransactionHead = idTransactionHead;
  }

  public TransactionType getTransactionType() {
    return this.transactionType == null ? null : TransactionType.getTransactionTypeByValue(this.transactionType);
  }

  public void setTransactionType(byte transactionType) {
    this.transactionType = transactionType;
  }

  public Date getTransactionTime() {
    return transactionTime;
  }

  public void setTransactionTime(Date transactionTime) {
    this.transactionTime = transactionTime;
  }

  public Date getExDate() {
    return exDate;
  }

  public void setExDate(Date exDate) {
    this.exDate = exDate;
  }

  public String getTransactionTypeImp() {
    return transactionTypeImp;
  }

  public void setTransactionTypeImp(String transactionTypeImp) {
    this.transactionTypeImp = transactionTypeImp;
  }

  public Cashaccount getCashaccount() {
    return cashaccount;
  }

  public void setCashaccount(Cashaccount cashaccount) {
    this.cashaccount = cashaccount;
  }

  public String getCurrencyAccount() {
    return currencyAccount;
  }

  public void setCurrencyAccount(String currencyAccount) {
    this.currencyAccount = currencyAccount;
  }

  public String getCashAccountImp() {
    return cashAccountImp;
  }

  public void setCashAccountImp(String cashAccountImp) {
    this.cashAccountImp = cashAccountImp;
  }

  public String getCurrencySecurity() {
    return currencySecurity;
  }

  public void setCurrencySecurity(String currencySecurity) {
    this.currencySecurity = currencySecurity;
  }

  public String getIsin() {
    return isin;
  }

  public void setIsin(String isin) {
    this.isin = isin;
  }

  public boolean isReadyForTransaction() {
    return readyForTransaction;
  }

  public void setReadyForTransaction(boolean readyForTransaction) {
    this.readyForTransaction = readyForTransaction;
  }

  public String getSymbolImp() {
    return symbolImp;
  }

  public void setSymbolImp(String symbolImp) {
    this.symbolImp = symbolImp;
  }

  public String getSecurityNameImp() {
    return securityNameImp;
  }

  public void setSecurityNameImp(String securityNameImp) {
    this.securityNameImp = securityNameImp;
  }

  public Double getCurrencyExRate() {
    return currencyExRate;
  }

  public void setCurrencyExRate(Double currencyExRate) {
    this.currencyExRate = currencyExRate;
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

  public String getTransactionError() {
    return transactionError;
  }

  public void setTransactionError(String transactionError) {
    this.transactionError = transactionError;
  }

  public void adjustCurrencyExRateOrQuotation() {
    if (currencyExRate != null && currencyExRate != 1.0) {
      currencyExRate = cashaccountAmount / ((cashaccountAmount - diffCashaccountAmount) / currencyExRate);
    } else {
      double distributionFrequency = 1.0;
      if (security != null && getTransactionType() == TransactionType.DIVIDEND) {
        Double df = security.getSecurityTransImportDistributionFrequency();
        distributionFrequency = df == null ? distributionFrequency : df;
      }
      quotation = quotation - diffCashaccountAmount * distributionFrequency / units;
    }
  }

  public Double getTaxCost() {
    return taxCost;
  }

  public List<ImportTransactionPosFailed> getImportTransactionPosFailedList() {
    return importTransactionPosFailedList;
  }

  public void setImportTransactionPosFailedList(List<ImportTransactionPosFailed> importTransactionPosFailedList) {
    this.importTransactionPosFailedList = importTransactionPosFailedList;
  }

  public void setTaxCost(Double taxCost1, Double taxCost2, boolean add) {
    double tt1 = taxCost1 != null ? Math.abs(taxCost1) : 0.0;
    double tt2 = taxCost2 != null ? Math.abs(taxCost2) : 0.0;
    double taxCostC = tt1 + tt2 + (add && taxCost != null ? taxCost : 0.0);
    taxCost = taxCostC == 0.0 ? null : taxCostC;
  }

  public Double getTransactionCost() {
    return transactionCost;
  }

  public void setTransactionCost(Double transactionCost) {
    this.transactionCost = transactionCost;
  }

  public void setTransactionCost(Double transactionCost1, Double transactionCost2, boolean add) {
    double tc1 = transactionCost1 != null ? Math.abs(transactionCost1) : 0.0;
    double tc2 = transactionCost2 != null ? Math.abs(transactionCost2) : 0.0;
    double transactionCostC = tc1 + tc2 + (add && transactionCost != null ? transactionCost : 0.0);
    transactionCost = transactionCostC == 0.0 ? null : transactionCostC;
  }

  public String getCurrencyCost() {
    return currencyCost;
  }

  public void setCurrencyCost(String currencyCost) {
    this.currencyCost = currencyCost;
  }

  public Double getCashaccountAmount() {
    return cashaccountAmount;
  }

  public void setCashaccountAmount(Double cashaccountAmount) {
    this.cashaccountAmount = cashaccountAmount;
  }

  public Double getAcceptedTotalDiff() {
    return acceptedTotalDiff;
  }

  public void setAcceptedTotalDiff(Double acceptedTotalDiff) {
    this.acceptedTotalDiff = acceptedTotalDiff;
  }

  public void adjustAcceptedTotalDiffToDiffTotal() {
    acceptedTotalDiff = diffCashaccountAmount;
  }

  public Double getAccruedInterest() {
    return accruedInterest;
  }

  public void setAccruedInterest(Double accruedInterest, boolean add) {
    if (accruedInterest != null) {
      accruedInterest = Math.abs(accruedInterest);
      this.accruedInterest = add ? this.accruedInterest + accruedInterest : accruedInterest;
    }
  }

  public String getField1StringImp() {
    return field1StringImp;
  }

  public void setField1StringImp(String field1StringImp) {
    this.field1StringImp = field1StringImp;
  }

  public Integer getIdTransaction() {
    return idTransaction;
  }

  public void setIdTransaction(Integer idTransaction) {
    this.idTransaction = idTransaction;
  }

  public Integer getIdTransactionMaybe() {
    return idTransactionMaybe;
  }

  public void setIdTransactionMaybe(Integer idTransactionMaybe) {
    this.idTransactionMaybe = idTransactionMaybe;
  }

  public Integer getIdTransactionImportTemplate() {
    return idTransactionImportTemplate;
  }

  public void setIdTransactionImportTemplate(Integer idTransactionImportTemplate) {
    this.idTransactionImportTemplate = idTransactionImportTemplate;
  }

  public Integer getIdFilePart() {
    return idFilePart;
  }

  public Security getSecurity() {
    return security;
  }

  public void setSecurity(Security security) {
    this.security = security;
  }

  public void setSecurityRemoveFromFlag(Security security) {
    this.security = security;
    removeKnowOtherFlags(ImportKnownOtherFlags.SECURITY_CURRENCY_MISMATCH);
  }

  public void setIdFilePart(Integer idFilePart) {
    this.idFilePart = idFilePart;
  }

  public String getFileNameOriginal() {
    return fileNameOriginal;
  }

  public void setFileNameOriginal(String fileNameOriginal) {
    this.fileNameOriginal = fileNameOriginal;
  }

  public Boolean getTaxableInterest() {
    return getTransactionType() != TransactionType.DIVIDEND ? null
        : getKnownOtherFlags().contains(ImportKnownOtherFlags.CAN_NO_TAX_ON_DIVIDEND_INTEREST) ? false : true;
  }

  @Override
  public Integer getId() {
    return idTransactionPos;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public double getDiffCashaccountAmount() {
    return diffCashaccountAmount;
  }

  public Integer getConnectedIdTransactionPos() {
    return connectedIdTransactionPos;
  }

  public void setConnectedIdTransactionPos(Integer connectedIdTransactionPos) {
    this.connectedIdTransactionPos = connectedIdTransactionPos;
  }

  public void addKnowOtherFlags(ImportKnownOtherFlags importKnownOtherFlags) {
    this.knownOtherFlags |= (1 << importKnownOtherFlags.ordinal());
  }

  public void removeKnowOtherFlags(ImportKnownOtherFlags importKnownOtherFlags) {
    this.knownOtherFlags &= ~(1 << importKnownOtherFlags.ordinal());
  }

  public EnumSet<ImportKnownOtherFlags> getKnownOtherFlags() {
    return knownOtherFlags == null ? EnumSet.noneOf(ImportKnownOtherFlags.class)
        : ImportKnownOtherFlags.decode(knownOtherFlags);
  }

  public void setKnownOtherFlags(EnumSet<ImportKnownOtherFlags> importKnownOtherFlagsSet) {
    this.knownOtherFlags = ImportKnownOtherFlags.encode(importKnownOtherFlagsSet);
  }

  public Boolean isCashaccountCurrencyDifferentFromSecurityAndNoExChangeRate() {
    if (this.cashaccount != null && this.security != null) {
      return !this.cashaccount.getCurrency().equals(security.getCurrency()) && this.currencyExRate == null;
    }
    return null;
  }

  private double getTaxCostEx() {
    return taxCost != null && currencyExRate != null && currencyCost != null && currencyAccount != null
        && currencySecurity != null && !currencyAccount.equals(currencySecurity) ? taxCost * currencyExRate
            : (taxCost != null) ? taxCost : 0;
  }

  private double getTransactionCostEx() {
    return transactionCost != null && currencyExRate != null && currencyCost != null && currencyAccount != null
        && currencySecurity != null && !currencyAccount.equals(currencySecurity) ? transactionCost * currencyExRate
            : (transactionCost != null) ? transactionCost : 0;
  }

  public String getFileType() {
    if (fileNameOriginal == null) {
      return CSV_FILE;
    } else {
      return ImportTransactionHelper.isCsvEnding(fileNameOriginal) ? CSV_FILE
          : ImportTransactionHelper.isPdfEnding(fileNameOriginal) && idFilePart == null ? PDF_FILE : PDF_TEXT_FILE;
    }
  }

  public static ImportTransactionPos createFromImportPropertiesSuccess(Integer idTenant, String fileNameOriginal,
      Integer idTransactionHead, Integer idTransactionImportTemplate, ImportProperties importProperties) {
    ImportTransactionPos importTransactionPos = new ImportTransactionPos(idTenant, fileNameOriginal, idTransactionHead,
        idTransactionImportTemplate);
    return createFromImportPropertiesForEveryKindOfTransaction(importTransactionPos, importProperties);
  }

  public static ImportTransactionPos createFromImportPropertiesSecurity(List<ImportProperties> importPropertiesList) {
    return createFromImportPropertiesSecuritySuccess(null, null, null, null, importPropertiesList);
  }

  public static ImportTransactionPos createFromImportPropertiesSecuritySuccess(Integer idTenant,
      String fileNameOriginal, Integer idTransactionHead, Integer idTransactionImportTemplate,
      List<ImportProperties> importPropertiesList) {
    ImportTransactionPos importTransactionPos = new ImportTransactionPos(idTenant, fileNameOriginal, idTransactionHead,
        idTransactionImportTemplate);
    return createFromImportPropertiesForSecurityTransaction(importTransactionPos, importPropertiesList);
  }

  private static ImportTransactionPos createFromImportPropertiesForSecurityTransaction(
      ImportTransactionPos importTransactionPos, List<ImportProperties> importPropertiesList) {
    double units = 0.0;
    double posTotal = 0.0;
    boolean percentage = false;

    for (int i = 0; i < importPropertiesList.size(); i++) {
      ImportProperties ip = importPropertiesList.get(i);
      if (i == 0) {
        createFromImportPropertiesForEveryKindOfTransaction(importTransactionPos, ip);
        importTransactionPos.setTaxCost(ip.getTt1(), ip.getTt2(), false);
        importTransactionPos.setExDate(ip.getExdiv());
        importTransactionPos.setCurrencySecurity(ip.getCin());
        importTransactionPos.setIsin(ip.getIsin());
        importTransactionPos.setSymbolImp(ip.getSymbol());
        importTransactionPos.setAccruedInterest(ip.getAc(), false);
        importTransactionPos.setCurrencyCost(ip.getCct());
        importTransactionPos.setSecurityNameImp(ip.getSn());
        percentage = ip.isPercentage();

      }
      // Repeatable Properties
      if (ip.getUnits() != null && ip.getQuotation() != null) {
        double transUnits = Math.abs(ip.getUnits()) / (percentage ? 100.0 : 1.0);
        units += transUnits;
        posTotal += transUnits * ip.getQuotation();
        if (i > 0) {
          if (ip.getTa() != null && importTransactionPos.getFileType().equals(CSV_FILE)) {
            importTransactionPos.setCashaccountAmount(importTransactionPos.getCashaccountAmount() + ip.getTa());
          }
          importTransactionPos.setTransactionCost(ip.getTc1(), ip.getTc2(), true);
          importTransactionPos.setTaxCost(ip.getTt1(), ip.getTt2(), true);
          importTransactionPos.setAccruedInterest(ip.getAc(), true);
        }
      }
    }

    if (units != 0.0 && posTotal != 0.0) {
      importTransactionPos.setUnits(units);
      importTransactionPos.setQuotation(posTotal / units);
    }

    return importTransactionPos;
  }

  private static ImportTransactionPos createFromImportPropertiesForEveryKindOfTransaction(
      ImportTransactionPos importTransactionPos, ImportProperties ip) {
    importTransactionPos.setTransactionType(ip.getTransactionType().getValue());
    importTransactionPos.setTransactionTime(ip.getDatetime());
    importTransactionPos.setCurrencyAccount(ip.getCac());
    importTransactionPos.setCashaccountAmount(ip.getTa());
    importTransactionPos.setField1StringImp(ip.getSf1());
    importTransactionPos.setIdFilePart(ip.getFileOrLineNumber());
    importTransactionPos.setKnownOtherFlags(ip.getKnownOtherFlags());
    Double exchangeRate = ip.getCex() != null || ip.getCin() != null && !ip.getCac().equals(ip.getCin()) ? ip.getCex()
        : null;
    importTransactionPos.setCurrencyExRate(exchangeRate);
    importTransactionPos.setTransactionCost(ip.getTc1(), ip.getTc2(), false);
    return importTransactionPos;
  }

  public void calcCashaccountAmount() {
    switch (getTransactionType()) {
    case ACCUMULATE:
    case REDUCE:
    case DIVIDEND:
      // TODO should not happended
      if (units != null && quotation != null) {
        correctQuotationForDividend();
        calcCashaccountAmount = DataHelper.round(units * quotation);

        double taxCostC = getTaxCostEx();
        double transactionCostC = getTransactionCostEx();
        double accruedInterestC = accruedInterest != null ? accruedInterest : 0;
        if (getTransactionType() == TransactionType.ACCUMULATE) {
          calcCashaccountAmount = (calcCashaccountAmount + taxCostC + transactionCostC + accruedInterestC) * -1.0;
          cashaccountAmount = cashaccountAmount != null ? Math.abs(cashaccountAmount) * -1.0 : null;
        } else {
          // For reduce and dividend
          calcCashaccountAmount -= taxCostC + transactionCostC - accruedInterestC;
          cashaccountAmount = cashaccountAmount != null ? Math.abs(cashaccountAmount) : null;
        }
        calcCashaccountAmount *= (currencyExRate != null ? this.currencyExRate : 1.0);
        calcCashaccountAmount = DataHelper.round(calcCashaccountAmount, GlobalConstants.FID_STANDARD_FRACTION_DIGITS);
      }
      break;
    default:
      calcCashaccountAmount = cashaccountAmount;
    }
  }

  private void correctQuotationForDividend() {
    if (security != null && getTransactionType() == TransactionType.DIVIDEND
        && getKnownOtherFlags().contains(ImportKnownOtherFlags.CAN_BOND_QUOTATION_CORRECTION)
        && !getKnownOtherFlags().contains(ImportKnownOtherFlags.USED_BOND_QUOTATION_CORRECTION)) {
      Double df = security.getSecurityTransImportDistributionFrequency();
      if (df != null) {
        quotation /= df;
        addKnowOtherFlags(ImportKnownOtherFlags.USED_BOND_QUOTATION_CORRECTION);
      }
    }
  }

  public void calcDiffCashaccountAmountWhenPossible() {
    if ((importTransactionPosFailedList == null || importTransactionPosFailedList.isEmpty())
        && cashaccountAmount != null) {
      if (calcDiffCashaccountAmount() != 0.0
          && getKnownOtherFlags().contains(ImportKnownOtherFlags.CAN_BASE_CURRENCY_MAYBE_INVERSE)
          && currencyExRate != null) {
        double oldCurrencyExRate = currencyExRate;
        currencyExRate = 1 / currencyExRate;
        if (calcDiffCashaccountAmount() != 0.0) {
          currencyExRate = oldCurrencyExRate;
          calcDiffCashaccountAmount();
        }
      }
    }
  }

  private double calcDiffCashaccountAmount() {
    calcCashaccountAmount();
    diffCashaccountAmount = calcCashaccountAmount
        - DataHelper.round(cashaccountAmount, GlobalConstants.FID_STANDARD_FRACTION_DIGITS);
    return diffCashaccountAmount;
  }

  public double getCalcCashaccountAmount() {
    return calcCashaccountAmount;
  }

  @Override
  public int compareTo(ImportTransactionPos importTransactionPos1) {
    return this.transactionTime.compareTo(importTransactionPos1.getTransactionTime());
  }

  @Override
  public String toString() {
    return "ImportTransactionPos [idTransactionPos=" + idTransactionPos + ", idTenant=" + idTenant
        + ", idTransactionHead=" + idTransactionHead + ", transactionType=" + transactionType + ", transactionTypeImp="
        + transactionTypeImp + ", transactionTime=" + transactionTime + ", cashaccount=" + cashaccount
        + ", currencyAccount=" + currencyAccount + ", cashAccountImp=" + cashAccountImp + ", currencyExRate="
        + currencyExRate + ", currencySecurity=" + currencySecurity + ", isin=" + isin + ", symbolImp=" + symbolImp
        + ", securityNameImp=" + securityNameImp + ", security=" + security + ", units=" + units + ", quotation="
        + quotation + ", taxCost=" + taxCost + ", transactionCost=" + transactionCost + ", cashaccountAmount="
        + cashaccountAmount + ", accruedInterest=" + accruedInterest + ", field1StringImp=" + field1StringImp
        + ", idTransaction=" + idTransaction + ", idTransactionImportTemplate=" + idTransactionImportTemplate
        + ", idFilePart=" + idFilePart + ", fileNameOriginal=" + fileNameOriginal + ", importTransactionPosFailedList="
        + importTransactionPosFailedList + ", calcCashaccountAmount=" + calcCashaccountAmount
        + ", calcCashaccountAmount=" + diffCashaccountAmount + "]";
  }

}
