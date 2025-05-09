package grafiosch.entities;

import java.io.Serializable;
import java.util.Date;

import grafiosch.common.PropertyAlwaysUpdatable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Sets a limit of possible changes and additions which a user with limit rights can do")
@Entity
@Table(name = UserEntityChangeLimit.TABNAME)
public class UserEntityChangeLimit extends Auditable implements AdminEntity, Serializable {

  public static final String TABNAME = "user_entity_change_limit";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_user_entity_change_limit")
  private Integer idUserEntityChangeLimit;

  @Schema(description = "ID of the user who is limited in his rights")
  @NotNull
  @Column(name = "id_user")
  private Integer idUser;

  @Schema(description = "The name of the entitly like ")
  @NotNull
  @Column(name = "entity_name")
  private String entityName;

  @Schema(description = "Maximum number of CUD operations that may be performed daily.")
  @NotNull
  @Column(name = "day_limit")
  @PropertyAlwaysUpdatable
  private Integer dayLimit;

  @Schema(description = "Until which date these rights are valid")
  @NotNull
  @Column(name = "until_date")
  @PropertyAlwaysUpdatable
  private Date untilDate;

  public UserEntityChangeLimit() {
  }

  public UserEntityChangeLimit(Integer idUser, String entityName, Date untilDate, Integer dayLimit) {
    this.idUser = idUser;
    this.entityName = entityName;
    this.untilDate = untilDate;
    this.dayLimit = dayLimit;
  }

  public Integer getIdUser() {
    return idUser;
  }

  public void setIdUser(Integer idUser) {
    this.idUser = idUser;
  }

  public String getEntityName() {
    return entityName;
  }

  public void setEntityName(String entityName) {
    this.entityName = entityName;
  }

  public Integer getDayLimit() {
    return dayLimit;
  }

  public void setDayLimit(Integer dayLimit) {
    this.dayLimit = dayLimit;
  }

  public Date getUntilDate() {
    return untilDate;
  }

  public void setUntilDate(Date untilDate) {
    this.untilDate = untilDate;
  }

  public Integer getIdUserEntityChangeLimit() {
    return idUserEntityChangeLimit;
  }

  @Override
  public Integer getId() {
    return idUserEntityChangeLimit;
  }

}
