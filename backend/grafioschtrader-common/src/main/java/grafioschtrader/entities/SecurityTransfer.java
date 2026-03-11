package grafioschtrader.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.BaseConstants;
import grafiosch.entities.TenantBaseID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Represents a security transfer between two securities accounts belonging to the same tenant. Creates a matching sell
 * transaction in the source account and a buy transaction in the target account at the closing price on the transfer
 * date.
 */
@Schema(description = "User-initiated transfer of a security between securities accounts within the same tenant.")
@Entity
@Table(name = SecurityTransfer.TABNAME)
public class SecurityTransfer extends TenantBaseID {

  public static final String TABNAME = "security_transfer";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_security_transfer")
  private Integer idSecurityTransfer;

  @JsonIgnore
  @Column(name = "id_tenant")
  @NotNull
  private Integer idTenant;

  @Schema(description = "The security being transferred")
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "id_security", referencedColumnName = "id_securitycurrency")
  @NotNull
  private Security security;

  @Schema(description = "Source securities account ID")
  @Column(name = "id_securityaccount_source")
  @NotNull
  private Integer idSecurityaccountSource;

  @Schema(description = "Target securities account ID")
  @Column(name = "id_securityaccount_target")
  @NotNull
  private Integer idSecurityaccountTarget;

  @Schema(description = "Date of the transfer")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "transfer_date")
  @NotNull
  private LocalDate transferDate;

  @Schema(description = "Number of units to transfer")
  @Column(name = "units")
  @NotNull
  @Positive
  private Double units;

  @Schema(description = "Closing price at transfer date")
  @Column(name = "quotation")
  @NotNull
  @Positive
  private Double quotation;

  @Schema(description = "SELL transaction ID in source account")
  @Column(name = "id_transaction_sell")
  private Integer idTransactionSell;

  @Schema(description = "BUY transaction ID in target account")
  @Column(name = "id_transaction_buy")
  private Integer idTransactionBuy;

  @Schema(description = "Security ID for creation requests (avoids sending full Security object)")
  @Transient
  private Integer idSecurity;

  @Schema(description = "Optional note about this transfer")
  @Column(name = "note")
  @Size(max = 1024)
  private String note;

  @Schema(description = "Whether this transfer can be reversed (no subsequent transactions in target account)")
  @Transient
  private boolean reversible = true;

  @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_SECOND)
  @Column(name = "creation_time")
  private LocalDateTime creationTime;

  @Override
  public Integer getId() {
    return idSecurityTransfer;
  }

  public Integer getIdSecurityTransfer() {
    return idSecurityTransfer;
  }

  public void setIdSecurityTransfer(Integer idSecurityTransfer) {
    this.idSecurityTransfer = idSecurityTransfer;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public Security getSecurity() {
    return security;
  }

  public void setSecurity(Security security) {
    this.security = security;
  }

  public Integer getIdSecurityaccountSource() {
    return idSecurityaccountSource;
  }

  public void setIdSecurityaccountSource(Integer idSecurityaccountSource) {
    this.idSecurityaccountSource = idSecurityaccountSource;
  }

  public Integer getIdSecurityaccountTarget() {
    return idSecurityaccountTarget;
  }

  public void setIdSecurityaccountTarget(Integer idSecurityaccountTarget) {
    this.idSecurityaccountTarget = idSecurityaccountTarget;
  }

  public LocalDate getTransferDate() {
    return transferDate;
  }

  public void setTransferDate(LocalDate transferDate) {
    this.transferDate = transferDate;
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

  public Integer getIdTransactionSell() {
    return idTransactionSell;
  }

  public void setIdTransactionSell(Integer idTransactionSell) {
    this.idTransactionSell = idTransactionSell;
  }

  public Integer getIdTransactionBuy() {
    return idTransactionBuy;
  }

  public void setIdTransactionBuy(Integer idTransactionBuy) {
    this.idTransactionBuy = idTransactionBuy;
  }

  public Integer getIdSecurity() {
    return idSecurity;
  }

  public void setIdSecurity(Integer idSecurity) {
    this.idSecurity = idSecurity;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public boolean isReversible() {
    return reversible;
  }

  public void setReversible(boolean reversible) {
    this.reversible = reversible;
  }

  public LocalDateTime getCreationTime() {
    return creationTime;
  }

  public void setCreationTime(LocalDateTime creationTime) {
    this.creationTime = creationTime;
  }
}
