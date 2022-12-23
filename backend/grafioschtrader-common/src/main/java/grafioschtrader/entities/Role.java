package grafioschtrader.entities;

import java.io.Serializable;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Entity
@Table(name = Role.TABNAME)
public class Role extends BaseID implements Serializable {

  public static final String TABNAME = "role";

  public static final String ADMIN = "ADMIN";
  public static final String ALL_EDIT = "ALLEDIT";
  public static final String USER = "USER";
  public static final String LIMIT_EDIT = "LIMITEDIT";

  public static final String ROLE = "ROLE_";
  public static final String ROLE_ADMIN = ROLE + ADMIN;
  public static final String ROLE_ALL_EDIT = ROLE + ALL_EDIT;
  public static final String ROLE_USER = ROLE + USER;
  public static final String ROLE_LIMIT_EDIT = ROLE + LIMIT_EDIT;

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_role")
  private Integer idRole;

  @NotNull
  @Size(max = 50)
  @Column(name = "rolename", length = 50)
  private String rolename;

  public Role() {
  }

  public Role(String rolename) {
    this.rolename = rolename;
  }

  public Integer getIdRole() {
    return idRole;
  }

  public void setIdRole(int idRole) {
    this.idRole = idRole;
  }

  public String getRolename() {
    return rolename;
  }

  public void setRolename(String rolename) {
    this.rolename = rolename;
  }

  @Override
  public Integer getId() {
    return idRole;
  }

  /*
   * public Set<User> getUserRoles() { return userRoles; }
   *
   * public void setUserRoles(Set<User> userRoles) { this.userRoles = userRoles; }
   */
  @Override
  public String toString() {
    return String.format("%s(id=%d, rolename='%s')", this.getClass().getSimpleName(), this.getIdRole(),
        this.getRolename());
  }

}
