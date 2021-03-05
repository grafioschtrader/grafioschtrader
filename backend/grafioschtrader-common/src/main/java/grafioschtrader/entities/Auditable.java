package grafioschtrader.entities;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.EntityListeners;
import javax.persistence.MappedSuperclass;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Version;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Auditable extends ProposeTransientTransfer implements Serializable {

  private static final long serialVersionUID = 1L;

  @CreatedBy
  @Column(name = "created_by")
  protected Integer createdBy;

  @CreatedDate
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "creation_time")
  protected Date creationTime;

  @LastModifiedBy
  @Column(name = "last_modified_by")
  protected Integer lastModifiedBy;

  @LastModifiedDate
  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "last_modified_time")
  protected Date lastModifiedTime;

  @Version
  @Column(name = "version")
  protected Integer version;

  public Integer getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Integer createdBy) {
    this.createdBy = createdBy;
  }

  public Date getCreationTime() {
    return creationTime;
  }

  public void setCreationTime(Date creationTime) {
    this.creationTime = creationTime;
  }

  public Integer getLastModifiedBy() {
    return lastModifiedBy;
  }

  public void setLastModifiedBy(Integer lastModifiedBy) {
    this.lastModifiedBy = lastModifiedBy;
  }

  public Date getLastModifiedTime() {
    return lastModifiedTime;
  }

  public void setLastModifiedTime(Date lastModifiedTime) {
    this.lastModifiedTime = lastModifiedTime;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
   this.version = version;
  }

}
