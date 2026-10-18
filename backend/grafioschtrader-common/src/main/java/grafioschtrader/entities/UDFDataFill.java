package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import grafioschtrader.types.UDFFillSource;
import grafioschtrader.types.UDFFillState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Fill state of one cell: the user-defined security field {@code idUDFMetadata} for the security {@code idEntity}. The
 * provenance cannot live in {@code udf_data.json_values} itself, because {@code UDFData.checkDataAgainstMetadata}
 * removes every key that is not the key of a field, so a marker key would be dropped at the next save of the user.
 *
 * A value is automatic only while the cell holds exactly {@code filledValue}; once the user changed it, the next fill
 * run treats it as manual and deletes the row. The table has no {@code id_user}: the owner is the owner of the field,
 * whose {@code udf_data} row holds the value. Rows are written exclusively by {@code UDFFillWriter}.
 */
@Schema(description = "Fill state of one cell of a user-defined security field whose value a fill source computes.")
@Entity
@Table(name = UDFDataFill.TABNAME)
public class UDFDataFill {

  public static final String TABNAME = "udf_data_fill";

  @EmbeddedId
  private UDFDataFillKey id;

  @Column(name = "fill_source")
  private byte fillSource;

  @Column(name = "fill_state")
  private byte fillState;

  @Schema(description = "The fill rule version of the field when this row was written.")
  @Column(name = "rule_version")
  private int ruleVersion;

  @Schema(description = "The value the fill wrote or adopted, in the same canonical form as in udf_data.")
  @Column(name = "filled_value")
  private String filledValue;

  @Schema(description = "Optional page from which the value was taken.")
  @Column(name = "source_url")
  private String sourceUrl;

  @Schema(description = "UTC time of the last definitive outcome for this cell.")
  @Column(name = "checked_time")
  private LocalDateTime checkedTime;

  @Schema(description = "Excerpt of the source that names a value proposed by a language model.")
  @Column(name = "evidence")
  private String evidence;

  @Schema(description = "Date the source gives for a proposed value, when it occurs in the source.")
  @Column(name = "as_of_date")
  private LocalDate asOfDate;

  @Schema(description = "Why a proposal was discarded or why the source has no value.")
  @Column(name = "verify_note")
  private String verifyNote;

  public UDFDataFill() {
  }

  public UDFDataFill(UDFDataFillKey id) {
    this.id = id;
  }

  public UDFDataFillKey getId() {
    return id;
  }

  public UDFFillSource getFillSource() {
    return UDFFillSource.getByValue(fillSource);
  }

  public void setFillSource(UDFFillSource fillSource) {
    this.fillSource = fillSource.getValue();
  }

  public UDFFillState getFillState() {
    return UDFFillState.getByValue(fillState);
  }

  public void setFillState(UDFFillState fillState) {
    this.fillState = fillState.getValue();
  }

  public int getRuleVersion() {
    return ruleVersion;
  }

  public void setRuleVersion(int ruleVersion) {
    this.ruleVersion = ruleVersion;
  }

  public String getFilledValue() {
    return filledValue;
  }

  public void setFilledValue(String filledValue) {
    this.filledValue = filledValue;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public void setSourceUrl(String sourceUrl) {
    this.sourceUrl = sourceUrl;
  }

  public LocalDateTime getCheckedTime() {
    return checkedTime;
  }

  public void setCheckedTime(LocalDateTime checkedTime) {
    this.checkedTime = checkedTime;
  }

  public String getEvidence() {
    return evidence;
  }

  public void setEvidence(String evidence) {
    this.evidence = evidence;
  }

  public LocalDate getAsOfDate() {
    return asOfDate;
  }

  public void setAsOfDate(LocalDate asOfDate) {
    this.asOfDate = asOfDate;
  }

  public String getVerifyNote() {
    return verifyNote;
  }

  public void setVerifyNote(String verifyNote) {
    this.verifyNote = verifyNote;
  }

  /**
   * Natural key of a fill row: the field and the security.
   */
  @Embeddable
  public static class UDFDataFillKey implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "id_udf_metadata")
    private Integer idUDFMetadata;

    @Column(name = "id_entity")
    private Integer idEntity;

    public UDFDataFillKey() {
    }

    public UDFDataFillKey(Integer idUDFMetadata, Integer idEntity) {
      this.idUDFMetadata = idUDFMetadata;
      this.idEntity = idEntity;
    }

    public Integer getIdUDFMetadata() {
      return idUDFMetadata;
    }

    public Integer getIdEntity() {
      return idEntity;
    }

    @Override
    public int hashCode() {
      return Objects.hash(idUDFMetadata, idEntity);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      UDFDataFillKey other = (UDFDataFillKey) obj;
      return Objects.equals(idUDFMetadata, other.idUDFMetadata) && Objects.equals(idEntity, other.idEntity);
    }
  }
}
