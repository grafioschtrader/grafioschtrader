package grafiosch.entities;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.LocaleUtils;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import grafiosch.common.DynamicFormPropertySupport;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertyChangePassword;
import grafiosch.common.PropertyOnlyCreation;
import grafiosch.dynamic.model.DynamicFormPropertyHelps;
import grafiosch.exceptions.DataViolationException;
import grafiosch.types.Language;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

@Schema(description = "Contains a user of this framework. The user can have different roles.")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Entity
@Table(name = User.TABNAME, uniqueConstraints = @UniqueConstraint(columnNames = { "email" }))
public class User extends Auditable implements Serializable, UserDetails, AdminEntity {
  public static final String TABNAME = "user";
  public static final String TABNAME_USER_ROLE = "user_role";

  public final static String LIMIT_REQUEST_EXCEED_COUNT = "limitRequestExceedCount";
  public final static String SECURITY_BREACH_COUNT = "securityBreachCount";

  private static final long serialVersionUID = 1L;

  @Schema(description = "Unique identifier of the user")
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_user")
  private Integer idUser;

  @Schema(description = "Contains the user's e-mail address. Is also used as a login name")
  @NotNull
  @Size(min = 4, max = 255)
  @PropertyOnlyCreation
  @DynamicFormPropertySupport(value = { DynamicFormPropertyHelps.EMAIL })
  @Pattern(regexp = "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*$")
  private String email;

  @Schema(description = "Contains the encrypted password")
  @NotNull(groups = { PropertyChangePassword.class })
  @PropertyChangePassword
  @Column(name = "password")
  @DynamicFormPropertySupport(value = { DynamicFormPropertyHelps.PASSWORD })
  @Size(min = 1, max = 70)
  @NotNull(groups = { PropertyAlwaysUpdatable.class })
  @Null(groups = { AdminModify.class })
  private String password;

  @Schema(description = "Contains the nickname. Could possibly be used as a name for other users of this instance. This must be unambiguous.")
  @NotNull
  @Size(min = 2, max = 30)
  @PropertyAlwaysUpdatable
  private String nickname;

  @Schema(description = "Contains the user's roles. The function of these roles must be consistent with Spring Security.")
  @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(name = TABNAME_USER_ROLE, joinColumns = @JoinColumn(name = "id_user", referencedColumnName = "id_user"), inverseJoinColumns = @JoinColumn(name = "id_role", referencedColumnName = "id_role"))
  private Collection<Role> roles;

  @Schema(description = "The time-limited exception limits for CUD operations of certain information classes.")
  @JoinColumn(name = "id_user")
  @OneToMany(fetch = FetchType.LAZY)
  private List<UserEntityChangeLimit> userEntityChangeLimitList;

  @Schema(description = "Tenant reference")
  @Column(name = "id_tenant")
  private Integer idTenant;

  @Schema(description = "Can a user log on to this application?")
  @Column(name = "enabled")
  @PropertyAlwaysUpdatable
  private boolean enabled;

  @Schema(description = """
      Basically, the locale of the user interface is determined by this specification. This should override the language
      settings of the web browser or the language settings of other UIs.""")
  @Column(name = "locale")
  @NotNull
  @Size(min = 2, max = 5)
  @DynamicFormPropertySupport(value = { DynamicFormPropertyHelps.SELECT_OPTIONS })
  @PropertyAlwaysUpdatable
  private String localeStr;

  @Schema(description = "This framework uses the UTC time zone. Contains the deviation in minutes compared to UTC, can therefore also be minus.")
  @Column(name = "timezone_offset")
  @PropertyAlwaysUpdatable
  private Integer timezoneOffset;

  @Schema(description = "Counts violations of the data limit or access to third-party data.")
  @Column(name = "security_breach_count")
  @PropertyAlwaysUpdatable
  private short securityBreachCount;

  @Schema(description = "Counts the number of violations that exceeds the number of requests in one or more time units.")
  @Column(name = "limit_request_exceed_count")
  @PropertyAlwaysUpdatable
  private short limitRequestExceedCount;

  @Schema(description = "When was the user's role last changed? Is used for the message or mail system of this framework.")
  @JsonIgnore
  @Column(name = "last_role_modified_time")
  private Date lastRoleModifiedTime;

  @Schema(description = "Show if an entity was created by my in the user interface")
  @Column(name = "ui_show_my_property")
  @PropertyAlwaysUpdatable
  private boolean uiShowMyProperty;

  @Schema(description = "Contains the user role with the most extensive rights.")
  @Transient
  @PropertyAlwaysUpdatable
  private String mostPrivilegedRole;

  @Transient
  private Map<String, Role> roleMap;

  @Schema(description = """
      Not used yet. Designed for the future:
      - A portfolio is simulated and would have to be linked to another tenant ID.
      Only then could the result of this simulation be viewed in full.
      - An additional table would be created containing the permission to view a tenant.""")
  @Transient
  private Integer actualIdTenant;

  @Override
  @JsonProperty("email")
  public String getUsername() {
    return email;
  }

  @Schema(description = """
      The admin can remove the user lock if the user has violated third-party
      data and the request limit. In such a case, an entry will be made here.""")
  @Transient
  public ProposeUserTask userChangePropose;

  @Schema(description = """
      If the user of the “User with limits” role exceeds the limit with CUD operations on an information class.
      He can simultaneously request an increase in this limit for this information class.
      The desired request is added here and should be displayed accordingly in the user interface so
      that this proposal is accepted or rejected. This would create or change a 'UserEntityChangeLimit'.""")
  @Transient
  private List<ProposeUserTask> userChangeLimitProposeList = new ArrayList<>();

  public User() {
  }

  public User(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public User(final String email, final String password, final String nickname, final String localeStr,
      final Integer timezoneOffset) {
    this.email = email;
    this.password = password;
    this.nickname = nickname;
    this.localeStr = localeStr;
    this.timezoneOffset = timezoneOffset;
  }

  public Integer getIdUser() {
    return idUser;
  }

  public void setIdUser(Integer idUser) {
    this.idUser = idUser;
  }

  public void setUsername(String username) {
    this.email = username;
  }

  public String getNickname() {
    return nickname;
  }

  public void setNickname(String nickname) {
    this.nickname = nickname;
  }

  @Override
  @JsonIgnore
  public String getPassword() {
    return password;
  }

  @JsonProperty
  public void setPassword(String password) {
    this.password = password;
  }

  public Integer getIdTenant() {
    return idTenant;
  }

  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public Integer getTimezoneOffset() {
    return timezoneOffset;
  }

  public void setTimezoneOffset(Integer timezoneOffset) {
    this.timezoneOffset = timezoneOffset;
  }

  public short getSecurityBreachCount() {
    return securityBreachCount;
  }

  public void setSecurityBreachCount(short securityBreachCount) {
    this.securityBreachCount = securityBreachCount;
  }

  public short getLimitRequestExceedCount() {
    return limitRequestExceedCount;
  }

  public void setLimitRequestExceedCount(short limitRequestExceedCount) {
    this.limitRequestExceedCount = limitRequestExceedCount;
  }

  public Collection<Role> getRoles() {
    return roles;
  }

  public void setRoles(Collection<Role> roles) {
    this.roles = roles;
  }

  public Integer getActualIdTenant() {
    return actualIdTenant != null ? actualIdTenant : idTenant;
  }

  public void setActualIdTenant(Integer actualIdTenant) {
    this.actualIdTenant = actualIdTenant;
  }

  @Override
  @JsonIgnore
  public Collection<? extends GrantedAuthority> getAuthorities() {
    Collection<GrantedAuthority> authorities = new ArrayList<>();
    if (getRoles() != null && !getRoles().isEmpty()) {
      for (Role role : roles) {
        GrantedAuthority authority = new SimpleGrantedAuthority(role.getRolename());
        authorities.add(authority);
      }
    }
    return authorities;
  }

  public void addRole(Role role) {
    if (roles == null) {
      roles = new ArrayList<>();
    }
    roles.add(role);
  }

  public String getMostPrivilegedRole() {
    if (mostPrivilegedRole != null) {
      return mostPrivilegedRole;
    } else {
      if (roles != null) {
        List<String> rolesStr = roles.stream().map(role -> role.getRolename()).collect(Collectors.toList());

        return rolesStr.contains(Role.ROLE_ADMIN) ? Role.ROLE_ADMIN
            : rolesStr.contains(Role.ROLE_ALL_EDIT) ? Role.ROLE_ALL_EDIT
                : rolesStr.contains(Role.ROLE_USER) ? Role.ROLE_USER : Role.ROLE_LIMIT_EDIT;
      } else {
        return null;
      }
    }
  }

  public void setRoleMap(Map<String, Role> roleMap) {
    this.roleMap = roleMap;
  }

  public void setMostPrivilegedRole(String mostPrivilegedRole) {
    this.mostPrivilegedRole = mostPrivilegedRole;
    if (roleMap != null) {
      roles = new ArrayList<>();
      switch (mostPrivilegedRole) {
      case Role.ROLE_ADMIN:
        roles.add(roleMap.get(Role.ROLE_ADMIN));
      case Role.ROLE_ALL_EDIT:
        roles.add(roleMap.get(Role.ROLE_ALL_EDIT));
      case Role.ROLE_USER:
        roles.add(roleMap.get(Role.ROLE_USER));
        break;
      default:
        roles.add(roleMap.get(Role.ROLE_LIMIT_EDIT));
      }
    }
  }

  public boolean hasIdRole(Integer idRole) {
    return roles.stream().filter(r -> r.getIdRole().equals(idRole)).findFirst().isPresent();
  }

  public Date getLastRoleModifiedTime() {
    return lastRoleModifiedTime;
  }

  public void setLastRoleModifiedTime(Date lastRoleModifiedTime) {
    this.lastRoleModifiedTime = lastRoleModifiedTime;
  }

  public List<UserEntityChangeLimit> getUserEntityChangeLimitList() {
    return userEntityChangeLimitList;
  }

  public void setUserEntityChangeLimitList(List<UserEntityChangeLimit> userEntityChangeLimitList) {
    this.userEntityChangeLimitList = userEntityChangeLimitList;
  }

  @Override
  @JsonIgnore
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  @JsonIgnore
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  @JsonIgnore
  public boolean isCredentialsNonExpired() {
    return true;
  }

  public Locale createAndGetJavaLocale() {
    return Locale.forLanguageTag(localeStr);
  }

  public String getLocaleStr() {
    return localeStr;
  }

  public void addUserChangeLimitPropose(ProposeUserTask proposeUserTask) {
    userChangeLimitProposeList.add(proposeUserTask);
  }

  public List<ProposeUserTask> getUserChangeLimitProposeList() {
    return userChangeLimitProposeList;
  }

  public void setLocaleStr(String localeStr) {
    this.localeStr = localeStr;
  }

  public boolean isUiShowMyProperty() {
    return uiShowMyProperty;
  }

  public void setUiShowMyProperty(boolean uiShowMyProperty) {
    this.uiShowMyProperty = uiShowMyProperty;
  }

  @JsonIgnore
  public void checkAndSetLocaleStr(String localeStr) {
    Locale locale = Locale.forLanguageTag(localeStr);

    if (LocaleUtils.isAvailableLocale(locale)) {
      setLocaleStr(localeStr);
    } else {
      throw new DataViolationException("locale", "locale.not.exists", null);
    }
  }

  @JsonIgnore
  public Language getLanguage() {
    return Language.getByCode(localeStr.substring(0, 2));
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Validated(AdminModify.class)
  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public Integer getId() {
    return idUser;
  }

  public interface AdminModify extends Default {
  }

}
