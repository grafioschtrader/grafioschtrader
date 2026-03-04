package grafiosch.entities;

import java.io.Serializable;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * The entities of most shared information classes should additionally contain the creator the last editor and the
 * associated timestamps. Supported by the Spring Framework via auditing.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Auditable extends ProposeTransientTransfer implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "The ID of the user who created the entity", example = "123", nullable = true)
  @CreatedBy
  @Column(name = "created_by")
  protected Integer createdBy;

  @Schema(description = "The timestamp when the entity was created", example = "2025-03-29T10:15:30", nullable = true)
  @CreatedDate
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT)
  @Column(name = "creation_time")
  protected LocalDateTime creationTime;

  @Schema(description = "The timestamp when the entity was last modified.", example = "2025-03-29T14:20:45", nullable = true)
  @LastModifiedBy
  @Column(name = "last_modified_by")
  protected Integer lastModifiedBy;

  @LastModifiedDate
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT)
  @Column(name = "last_modified_time")
  protected LocalDateTime lastModifiedTime;

  @Schema(description = "The version number of the entity for optimistic locking.", example = "1", nullable = true)
  @Version
  @Column(name = "version")
  protected Integer version;

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

  public Integer getLastModifiedBy() {
    return lastModifiedBy;
  }

  public void setLastModifiedBy(Integer lastModifiedBy) {
    this.lastModifiedBy = lastModifiedBy;
  }

  public LocalDateTime getLastModifiedTime() {
    return lastModifiedTime;
  }

  public void setLastModifiedTime(LocalDateTime lastModifiedTime) {
    this.lastModifiedTime = lastModifiedTime;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

}
