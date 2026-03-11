package grafioschtrader.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.entities.BaseID;
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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * System-wide ISIN change event created by an admin. When a security's ISIN changes, this entity records the old and
 * new ISIN plus references to the old and newly created security. Affected tenants can then apply or reverse the change.
 */
@Schema(description = "Represents an admin-created ISIN change event for a security.")
@Entity
@Table(name = SecurityAction.TABNAME)
public class SecurityAction extends BaseID<Integer> {

  public static final String TABNAME = "security_action";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_security_action")
  private Integer idSecurityAction;

  @Schema(description = "Security with the old ISIN before the change")
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "id_security_old", referencedColumnName = "id_securitycurrency")
  @NotNull
  private Security securityOld;

  @Schema(description = "Auto-created security with the new ISIN")
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "id_security_new", referencedColumnName = "id_securitycurrency")
  private Security securityNew;

  @Schema(description = "Denormalized old ISIN for display")
  @Column(name = "isin_old")
  @NotNull
  @Size(max = 12)
  private String isinOld;

  @Schema(description = "New ISIN to replace the old one")
  @Column(name = "isin_new")
  @NotNull
  @Size(max = 12)
  private String isinNew;

  @Schema(description = "Date the ISIN change took effect")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "action_date")
  @NotNull
  private LocalDate actionDate;

  @Schema(description = "Optional admin note about this ISIN change")
  @Column(name = "note")
  @Size(max = 1024)
  private String note;

  @Schema(description = "Number of tenants holding the old security at the time of creation")
  @Column(name = "affected_count")
  private int affectedCount;

  @Schema(description = "Number of tenants who have applied this ISIN change")
  @Column(name = "applied_count")
  private int appliedCount;

  @Schema(description = """
      Optional split ratio numerator. For a reverse split 1:5, fromFactor=5. When both fromFactor and toFactor are \
      null, the action is a pure ISIN change without unit adjustment.""")
  @Column(name = "from_factor")
  private Integer fromFactor;

  @Schema(description = """
      Optional split ratio denominator. For a reverse split 1:5, toFactor=1. When both fromFactor and toFactor are \
      null, the action is a pure ISIN change without unit adjustment.""")
  @Column(name = "to_factor")
  private Integer toFactor;

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
