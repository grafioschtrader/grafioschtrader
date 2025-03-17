package grafiosch.entities;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.types.TenantKindType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@MappedSuperclass
@Schema(title = "TenantBase is the shared base for Tenant entities", description = "Contains common attributes for Tenant entities.")
public abstract class TenantBase extends TenantBaseID implements Serializable {
 
  private static final long serialVersionUID = 1L;
  
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_tenant")
  private Integer idTenant;

  @NotBlank
  @Basic(optional = false)
  @Size(min = 1, max = 25)
  @Column(name = "tenant_name")
  private String tenantName;

  @Column(name = "create_id_user")
  @Schema(description = "User ID which created this tenant, can not be set from outside")
  private Integer createIdUser;

  @Column(name = "tenant_kind_type")
  @Schema(description = "Type of tenant, can not be set from outside")
  private byte tenantKindType;

  @JoinColumn(name = "id_tenant")
  @OneToMany()
  private List<User> userList;

  public TenantBase() {
  }

  public TenantBase(String tenantName, Integer createIdUser, TenantKindType tenantKindType) {
    this.tenantName = tenantName;
    this.createIdUser = createIdUser;
    this.tenantKindType = tenantKindType.getValue();
  }

  @JsonIgnore
  @Override
  public Integer getId() {
    return idTenant;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public String getTenantName() {
    return tenantName;
  }

  public void setTenantName(String tenantName) {
    this.tenantName = tenantName;
  }

  public Integer getCreateIdUser() {
    return createIdUser;
  }

  public void setCreateIdUser(Integer createIdUser) {
    this.createIdUser = createIdUser;
  }

  public TenantKindType getTenantKindType() {
    return TenantKindType.getTenantKindTypeByValue(tenantKindType);
  }

  public void setTenantKindType(TenantKindType tenantKindType) {
    this.tenantKindType = tenantKindType.getValue();
  }

  @JsonIgnore
  public List<User> getUsergroupList() {
    return userList;
  }

  public void setUsergroupList(List<User> usergroupList) {
    this.userList = usergroupList;
  }

  @Override
  public String toString() {
    return "grafioschtrader.entities.Tenant[ idTenant=" + idTenant + " ]";
  }
}
