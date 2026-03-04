package grafioschtrader.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.BaseConstants;
import grafiosch.entities.BaseID;
import grafioschtrader.types.CreateType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;

@MappedSuperclass
public abstract class DividendSplit extends BaseID<Integer> {

  @Schema(description = "Reference to the security")
  @Basic(optional = false)
  @Column(name = "id_securitycurrency")
  protected Integer idSecuritycurrency;

  @Schema(description = "Who has crated this EOD record")
  @Column(name = "create_type")
  @NotNull
  protected byte createType;

  @Schema(description = "When was this recored added or last time modified")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT)
  @Column(name = "create_modify_time")
  @NotNull
  private LocalDateTime createModifyTime;

  @JsonIgnore
  public abstract LocalDate getEventDate();

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

  public LocalDateTime getCreateModifyTime() {
    return createModifyTime;
  }

  public void setCreateModifyTime(LocalDateTime createModifyTime) {
    this.createModifyTime = createModifyTime;
  }

}
