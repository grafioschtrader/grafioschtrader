package grafioschtrader.entities;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.types.CreateType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.NotNull;

@MappedSuperclass
public abstract class DividendSplit extends BaseID {

  @Basic(optional = false)
  @Column(name = "id_securitycurrency")
  protected Integer idSecuritycurrency;

  @Schema(description = "Who has crated this EOD record")
  @Column(name = "create_type")
  @NotNull
  protected byte createType;

  @Schema(description = "When was this recored added or last time modified")
  @Column(name = "create_modify_time")
  @NotNull
  @Temporal(TemporalType.TIMESTAMP)
  private Date createModifyTime;

  @JsonIgnore
  public abstract Date getEventDate();

  public DividendSplit() {
  }

  public DividendSplit(Integer idSecuritycurrency, CreateType createType) {
    super();
    this.idSecuritycurrency = idSecuritycurrency;
    this.createType = createType.getValue();
  }

  public Integer getIdSecuritycurrency() {
    return idSecuritycurrency;
  }

  public void setIdSecuritycurrency(Integer idSecuritycurrency) {
    this.idSecuritycurrency = idSecuritycurrency;
  }

  public CreateType getCreateType() {
    return CreateType.getCreateType(createType);
  }

  public void setCreateType(CreateType createType) {
    this.createType = createType.getValue();
  }

  public Date getCreateModifyTime() {
    return createModifyTime;
  }

  public void setCreateModifyTime(Date createModifyTime) {
    this.createModifyTime = createModifyTime;
  }

}
