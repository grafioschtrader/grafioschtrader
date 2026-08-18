package grafiosch.entities;

import java.io.Serializable;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.dto.LimitKey;
import grafiosch.types.CountScope;
import grafiosch.types.LimitType;
import grafiosch.types.OwnerScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One configured anti-flooding limit. Replaces the {@code gt.max.*} / {@code gt.limit.day.*} / {@code g.limit.day.*}
 * rows of {@code globalparameters} and the former {@code user_entity_change_limit} table with a single, role- and
 * user-aware configuration table.
 *
 * <p>
 * The first five columns form the limit key (see {@link LimitKey}); {@code idRole} and {@code idUser} are mutually
 * exclusive and say who the row applies to. Both {@code null} makes it the {@code ALL} default row of that key. The
 * resolver consults a user row first, then the row of the user's most privileged role, then the {@code ALL} row, and
 * treats a key with no row at all as unlimited.
 * </p>
 *
 * <p>
 * The validation rule for {@link #limitValue} is deliberately <b>not</b> a column: it belongs to the key and lives in
 * the limit key registry, so a per-role or per-user override is validated exactly like the {@code ALL} default. This
 * differs from {@code globalparameters}, where the rule sits on the row.
 * </p>
 */
@Schema(description = """
    A single anti-flooding limit, configured per limit key and optionally narrowed to one role or one user. Rows with
    neither a role nor a user are the default for their key.""")
@Entity
@Table(name = EntityLimit.TABNAME)
public class EntityLimit extends Auditable implements AdminEntity, Serializable {

  public static final String TABNAME = "entity_limit";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_entity_limit")
  private Integer idEntityLimit;

  @Schema(description = "Which of the three limit mechanisms this row configures")
  @NotNull
  @Column(name = "limit_type")
  private byte limitType;

  @Schema(description = "JPA entity simple name or a registered pseudo name such as 'HistoryquoteRead'")
  @Size(min = 1, max = 40)
  @Column(name = "entity_name")
  private String entityName;

  @Schema(description = """
      Element entity of a nested-collection limit, for example 'Securitycurrency' under a watchlist. Null for a flat
      cap.""")
  @Size(max = 40)
  @Column(name = "relation_entity_name")
  private String relationEntityName;

  @Schema(description = """
      SINGLE counts the elements of the one parent the new element goes into, ALL counts them across every parent of
      the owner. Null for a flat cap.""")
  @Column(name = "count_scope")
  private Byte countScope;

  @Schema(description = """
      Column by which existing rows are counted for a MAX limit: TENANT by id_tenant, CREATOR by created_by, GLOBAL
      over the whole table. Null for the daily limit types.""")
  @Column(name = "owner_scope")
  private Byte ownerScope;

  @Schema(description = "Row applies to this role. Mutually exclusive with idUser; both null makes it the default row")
  @Column(name = "id_role")
  private Integer idRole;

  @Schema(description = "Row applies to this user. Mutually exclusive with idRole; both null makes it the default row")
  @Column(name = "id_user")
  private Integer idUser;

  @Schema(description = "The cap itself, validated against the input rule of the limit key")
  @NotNull
  @Column(name = "limit_value")
  @PropertyAlwaysUpdatable
  private Integer limitValue;

  @Schema(description = """
      Last day on which the row is honoured. Null means it never expires. An expired row is invisible to the resolver
      and falls through to the next step; it is not purged automatically.""")
  @Column(name = "valid_until")
  @PropertyAlwaysUpdatable
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT)
  private LocalDate validUntil;

  /**
   * Wire form of the limit key. The edit form posts this instead of the five key columns; the repository parses it,
   * checks it against the registry or the derived set, and writes the columns from it. It is filled again on load so
   * the client can match a row against the key definitions it fetched.
   */
  @Schema(description = "The five key parts joined with '|', empty segment for a null part")
  @Transient
  private String keyId;

  public EntityLimit() {
  }

  @JsonCreator(mode = JsonCreator.Mode.DISABLED)
  public EntityLimit(LimitKey limitKey, Integer idRole, Integer idUser, Integer limitValue, LocalDate validUntil) {
    setLimitKey(limitKey);
    this.idRole = idRole;
    this.idUser = idUser;
    this.limitValue = limitValue;
    this.validUntil = validUntil;
  }

  @PostLoad
  private void fillKeyId() {
    this.keyId = getLimitKey().keyId();
  }

  public LimitKey getLimitKey() {
    return new LimitKey(getLimitType(), entityName, relationEntityName, getCountScope(), getOwnerScope());
  }

  /** Writes the five key columns and the transient key id from one key. */
  public void setLimitKey(LimitKey limitKey) {
    setLimitType(limitKey.limitType());
    this.entityName = limitKey.entityName();
    this.relationEntityName = limitKey.relationEntityName();
    setCountScope(limitKey.countScope());
    setOwnerScope(limitKey.ownerScope());
    this.keyId = limitKey.keyId();
  }

  /**
   * Translation key under which the limit type is displayed. The enum constant name alone cannot serve as the key:
   * {@code MAX}, {@code ALL} and {@code TENANT} are already taken by unrelated texts, so every constant of the three
   * limit enums is prefixed with its enum name.
   *
   * @return for example {@code LIMIT_TYPE_DAY_CUD}
   */
  public String getLimitTypeKey() {
    return "LIMIT_TYPE_" + getLimitType().name();
  }

  /** Translation key of the counting scope, {@code null} for a flat cap. @see #getLimitTypeKey() */
  public String getCountScopeKey() {
    return countScope == null ? null : "COUNT_SCOPE_" + getCountScope().name();
  }

  /** Translation key of the owner scope, {@code null} for the daily limit types. @see #getLimitTypeKey() */
  public String getOwnerScopeKey() {
    return ownerScope == null ? null : "OWNER_SCOPE_" + getOwnerScope().name();
  }

  public LimitType getLimitType() {
    return LimitType.getLimitTypeByValue(this.limitType);
  }

  public void setLimitType(LimitType limitType) {
    this.limitType = limitType.getValue();
  }

  public String getEntityName() {
    return entityName;
  }

  public void setEntityName(String entityName) {
    this.entityName = entityName;
  }

  public String getRelationEntityName() {
    return relationEntityName;
  }

  public void setRelationEntityName(String relationEntityName) {
    this.relationEntityName = relationEntityName;
  }

  public CountScope getCountScope() {
    return countScope == null ? null : CountScope.getCountScopeByValue(countScope);
  }

  public void setCountScope(CountScope countScope) {
    this.countScope = countScope == null ? null : countScope.getValue();
  }

  public OwnerScope getOwnerScope() {
    return ownerScope == null ? null : OwnerScope.getOwnerScopeByValue(ownerScope);
  }

  public void setOwnerScope(OwnerScope ownerScope) {
    this.ownerScope = ownerScope == null ? null : ownerScope.getValue();
  }

  public Integer getIdRole() {
    return idRole;
  }

  public void setIdRole(Integer idRole) {
    this.idRole = idRole;
  }

  public Integer getIdUser() {
    return idUser;
  }

  public void setIdUser(Integer idUser) {
    this.idUser = idUser;
  }

  public Integer getLimitValue() {
    return limitValue;
  }

  public void setLimitValue(Integer limitValue) {
    this.limitValue = limitValue;
  }

  public LocalDate getValidUntil() {
    return validUntil;
  }

  public void setValidUntil(LocalDate validUntil) {
    this.validUntil = validUntil;
  }

  public String getKeyId() {
    return keyId;
  }

  public void setKeyId(String keyId) {
    this.keyId = keyId;
  }

  public Integer getIdEntityLimit() {
    return idEntityLimit;
  }

  @Override
  public Integer getId() {
    return idEntityLimit;
  }
}
