package grafiosch.entities;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

/**
 * TenantBase is the shared base for Tenant entities. Contains common attributes for Tenant entities. This class must be
 * expanded. A special implementation for a specific application will always extend Tenant with application-specific
 * attributes.
 *
 * TODO The user can currently only have one tenant. This should possibly be extended to a many to many relationship.
 */
@MappedSuperclass
public abstract class TenantBase extends TenantBaseID implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_tenant")
  private Integer idTenant;

  @Schema(description = "Name of the tenant.")
  @NotBlank
  @Basic(optional = false)
  @Size(min = 1, max = 25)
  @Column(name = "tenant_name")
  private String tenantName;

  @Schema(description = "User ID which created this tenant, can not be set from outside")
  @Column(name = "create_id_user")
  private Integer createIdUser;

 
  @JoinColumn(name = "id_tenant")
  @OneToMany()
  private List<User> userList;

  public static final String TABNAME = "tenant";

  public TenantBase() {
  }

  public TenantBase(String tenantName, Integer createIdUser) {
    super();
    this.tenantName = tenantName;
    this.createIdUser = createIdUser;
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

 

  @JsonIgnore
  public List<User> getUsergroupList() {
    return userList;
  }

  public void setUsergroupList(List<User> usergroupList) {
    this.userList = usergroupList;
  }

}
