package grafioschtrader.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import grafiosch.BaseConstants;
import grafiosch.entities.BaseID;
import grafioschtrader.types.SecurityActionType;
import grafioschtrader.types.SpinOffHistoryBasis;
import grafioschtrader.types.SpinOffHistoryBasisOrigin;
import grafioschtrader.types.SpinOffTaxTreatmentDe;
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
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * System-wide corporate action recorded by an administrator. The instrument references identify the predecessor and
 * successor of an ISIN change or the parent and child of a spin-off.
 */
@Schema(description = "Represents an admin-created ISIN change or spin-off.")
@Entity
@Table(name = SecurityAction.TABNAME)
public class SecurityAction extends BaseID<Integer> {

  public static final String TABNAME = "security_action";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_security_action")
  private Integer idSecurityAction;

  @Column(name = "action_type", nullable = false)
  private byte actionType;

  @Schema(description = "Predecessor of the ISIN change or parent security of the spin-off")
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "id_security_old", referencedColumnName = "id_securitycurrency")
  @NotNull
  private Security securityOld;

  @Schema(description = "Successor security of the ISIN change or child security of the spin-off")
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "id_security_new", referencedColumnName = "id_securitycurrency")
  private Security securityNew;

  @Schema(description = "Denormalized old ISIN for display")
  @Column(name = "isin_old")
  @NotNull
  @Size(max = 12)
  private String isinOld;

  @Schema(description = "ISIN of the successor or spin-off child")
  @Column(name = "isin_new")
  @NotNull
  @Size(max = 12)
  private String isinNew;

  @Schema(description = "Effective date of the ISIN change or ex-date of the spin-off")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "action_date")
  @NotNull
  private LocalDate actionDate;

  @Schema(description = "Optional admin note about the security action")
  @Column(name = "note")
  @Size(max = 1024)
  private String note;

  @Schema(description = "Number of tenants holding the old security at the time of creation")
  @Column(name = "affected_count")
  private int affectedCount;

  @Schema(description = "Number of tenants who have applied the action")
  @Column(name = "applied_count")
  private int appliedCount;

  @Schema(description = """
      Old units of an ISIN change with a split, or parent units in the share basis on the spin-off date.
      A pure ISIN change leaves both factors empty.""")
  @Column(name = "from_factor")
  private Integer fromFactor;

  @Schema(description = """
      New units of an ISIN change with a split, or child units allotted per fromFactor parent units on the spin-off
      date. A pure ISIN change leaves both factors empty.""")
  @Column(name = "to_factor")
  private Integer toFactor;

  @Schema(description = "Fraction of the parent's cost basis allocated to the child")
  @Column(name = "cost_allocation")
  @DecimalMin(value = "0", inclusive = false)
  @DecimalMax(value = "1", inclusive = false)
  private Double costAllocation;

  @Column(name = "cost_allocation_source")
  @Size(max = 255)
  private String costAllocationSource;

  @Column(name = "tax_treatment_de")
  private Byte taxTreatmentDe;

  @Schema(description = "Optional allocation for German tax calculations")
  @Column(name = "cost_allocation_de")
  @DecimalMin(value = "0", inclusive = false)
  @DecimalMax(value = "1", inclusive = false)
  private Double costAllocationDe;

  @Schema(description = """
      Factor of the split the split connector reports for the parent on the spin-off date, which GT drops. Written only
      by the split import; null when no split falls on that date.""")
  @Column(name = "provider_split_factor")
  private Double providerSplitFactor;

  @Schema(description = """
      Whether the series of the parent's history source is adjusted for this spin-off. Null for an ISIN change.""")
  @Column(name = "history_basis")
  private Byte historyBasis;

  @Schema(description = """
      Factor by which the history source divided the parent's prices before the spin-off date. Set only when the
      history basis is ADJUSTED; GT multiplies the delivered prices by it.""")
  @Column(name = "history_factor")
  private Double historyFactor;

  @Schema(description = "How the history basis was decided; null while it is undetermined and not set by the admin")
  @Column(name = "history_basis_origin")
  private Byte historyBasisOrigin;

  @Schema(description = "History connector of the parent for which the history basis was decided")
  @Column(name = "history_connector")
  @Size(max = 35)
  private String historyConnector;

  @Schema(description = "History URL extension of the parent for which the history basis was decided")
  @Column(name = "history_url_extend")
  @Size(max = 254)
  private String historyUrlExtend;

  @Schema(description = """
      True when the stored quotes of the parent before the spin-off date were last loaded under a determined history
      basis, so they are unadjusted for this spin-off and can serve as reference for the next full reload.""")
  @Column(name = "history_basis_applied")
  private boolean historyBasisApplied;

  @Schema(description = "Admin user ID who created this event")
  @Column(name = "created_by")
  @NotNull
  private Integer createdBy;

  @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_SECOND)
  @Column(name = "creation_time")
  private LocalDateTime creationTime;

  @Override
  public Integer getId() {
    return idSecurityAction;
  }

  public Integer getIdSecurityAction() {
    return idSecurityAction;
  }

  @NotNull
  public SecurityActionType getActionType() {
    return SecurityActionType.getByValue(actionType);
  }

  @JsonSetter(nulls = Nulls.FAIL)
  public void setActionType(SecurityActionType actionType) {
    this.actionType = actionType.getValue();
  }

  public Double getCostAllocation() {
    return costAllocation;
  }

  public void setCostAllocation(Double costAllocation) {
    this.costAllocation = costAllocation;
  }

  public String getCostAllocationSource() {
    return costAllocationSource;
  }

  public void setCostAllocationSource(String costAllocationSource) {
    this.costAllocationSource = costAllocationSource;
  }

  public SpinOffTaxTreatmentDe getTaxTreatmentDe() {
    return taxTreatmentDe == null ? null : SpinOffTaxTreatmentDe.getByValue(taxTreatmentDe);
  }

  public void setTaxTreatmentDe(SpinOffTaxTreatmentDe taxTreatmentDe) {
    this.taxTreatmentDe = taxTreatmentDe == null ? null : taxTreatmentDe.getValue();
  }

  public Double getCostAllocationDe() {
    return costAllocationDe;
  }

  public void setCostAllocationDe(Double costAllocationDe) {
    this.costAllocationDe = costAllocationDe;
  }

  public Double getProviderSplitFactor() {
    return providerSplitFactor;
  }

  public void setProviderSplitFactor(Double providerSplitFactor) {
    this.providerSplitFactor = providerSplitFactor;
  }

  public SpinOffHistoryBasis getHistoryBasis() {
    return historyBasis == null ? null : SpinOffHistoryBasis.getByValue(historyBasis);
  }

  public void setHistoryBasis(SpinOffHistoryBasis historyBasis) {
    this.historyBasis = historyBasis == null ? null : historyBasis.getValue();
  }

  public Double getHistoryFactor() {
    return historyFactor;
  }

  public void setHistoryFactor(Double historyFactor) {
    this.historyFactor = historyFactor;
  }

  public SpinOffHistoryBasisOrigin getHistoryBasisOrigin() {
    return historyBasisOrigin == null ? null : SpinOffHistoryBasisOrigin.getByValue(historyBasisOrigin);
  }

  public void setHistoryBasisOrigin(SpinOffHistoryBasisOrigin historyBasisOrigin) {
    this.historyBasisOrigin = historyBasisOrigin == null ? null : historyBasisOrigin.getValue();
  }

  public String getHistoryConnector() {
    return historyConnector;
  }

  public void setHistoryConnector(String historyConnector) {
    this.historyConnector = historyConnector;
  }

  public String getHistoryUrlExtend() {
    return historyUrlExtend;
  }

  public void setHistoryUrlExtend(String historyUrlExtend) {
    this.historyUrlExtend = historyUrlExtend;
  }

  public boolean isHistoryBasisApplied() {
    return historyBasisApplied;
  }

  public void setHistoryBasisApplied(boolean historyBasisApplied) {
    this.historyBasisApplied = historyBasisApplied;
  }

  /**
   * Factor by which GT multiplies the delivered prices of the parent before the action date, 1 when the history basis
   * is not {@link SpinOffHistoryBasis#ADJUSTED}.
   */
  @JsonIgnore
  public double getAppliedHistoryFactor() {
    return getHistoryBasis() == SpinOffHistoryBasis.ADJUSTED && historyFactor != null ? historyFactor : 1.0;
  }

  public void setIdSecurityAction(Integer idSecurityAction) {
    this.idSecurityAction = idSecurityAction;
  }

  public Security getSecurityOld() {
    return securityOld;
  }

  public void setSecurityOld(Security securityOld) {
    this.securityOld = securityOld;
  }

  public Security getSecurityNew() {
    return securityNew;
  }

  public void setSecurityNew(Security securityNew) {
    this.securityNew = securityNew;
  }

  public String getIsinOld() {
    return isinOld;
  }

  public void setIsinOld(String isinOld) {
    this.isinOld = isinOld;
  }

  public String getIsinNew() {
    return isinNew;
  }

  public void setIsinNew(String isinNew) {
    this.isinNew = isinNew;
  }

  public LocalDate getActionDate() {
    return actionDate;
  }

  public void setActionDate(LocalDate actionDate) {
    this.actionDate = actionDate;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public int getAffectedCount() {
    return affectedCount;
  }

  public void setAffectedCount(int affectedCount) {
    this.affectedCount = affectedCount;
  }

  public int getAppliedCount() {
    return appliedCount;
  }

  public void setAppliedCount(int appliedCount) {
    this.appliedCount = appliedCount;
  }

  public Integer getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Integer createdBy) {
    this.createdBy = createdBy;
  }

  public LocalDateTime getCreationTime() {
    return creationTime;
  }

  public void setCreationTime(LocalDateTime creationTime) {
    this.creationTime = creationTime;
  }

  public Integer getFromFactor() {
    return fromFactor;
  }

  public void setFromFactor(Integer fromFactor) {
    this.fromFactor = fromFactor;
  }

  public Integer getToFactor() {
    return toFactor;
  }

  public void setToFactor(Integer toFactor) {
    this.toFactor = toFactor;
  }
}
