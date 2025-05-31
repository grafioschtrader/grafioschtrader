package grafiosch.entities;

import java.util.HashMap;
import java.util.Map;

import grafiosch.usertask.LimitCudChange;
import grafiosch.usertask.ReleaseLogout;
import grafiosch.usertask.UserTaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Schema(description = """
    These data change requests always affect the limits of a user.
    Perhaps the user has reached a limit on the CUD operations of an information class or has
    violated the number of requests per time unit too often, for example.
    A corresponding user role can resolve this problem for this user.
    """)
@Entity
@Table(name = ProposeUserTask.TABNAME)
@DiscriminatorValue("U")
public class ProposeUserTask extends ProposeRequest {

  public static final String TABNAME = "propose_user_task";

  private static final long serialVersionUID = 1L;

  private static Map<UserTaskType, Class<?>> userTaskTypeModelMap;

  static {
    userTaskTypeModelMap = new HashMap<>();
    /** User may have produced too many security breach */
    userTaskTypeModelMap.put(UserTaskType.RELEASE_LOGOUT, ReleaseLogout.class);
    /** User touched entity modification limit */
    userTaskTypeModelMap.put(UserTaskType.LIMIT_CUD_CHANGE, LimitCudChange.class);
  }

  @Schema(description = "ID of the user who wishes to make the change.")
  @Basic(optional = false)
  @Column(name = "id_target_user")
  private Integer idTargetUser;

  @Schema(description = """
      To which user role is the data change request addressed?
      Normally, this will be the role with the strongest user rights.""")
  @Column(name = "id_role_to")
  private Integer idRoleTo;

  @Schema(description = """
      Enum constants for marking violations of a user against the limit for request
      to client or the number of CUD operations on an information class.""")
  @Basic(optional = false)
  @Column(name = "user_task_type ")
  private byte userTaskType;

  public Integer getIdTargetUser() {
    return idTargetUser;
  }

  public void setIdTargetUser(Integer idTargetUser) {
    this.idTargetUser = idTargetUser;
  }

  public Integer getIdRoleTo() {
    return idRoleTo;
  }

  public void setIdRoleTo(Integer idRoleTo) {
    this.idRoleTo = idRoleTo;
  }

  public UserTaskType getUserTaskType() {
    return UserTaskType.getUserTaskTypeByValue(userTaskType);
  }

  public void setUserTaskType(UserTaskType userTaskType) {
    this.userTaskType = userTaskType.getValue();
  }

  public static Class<?> getModelByUserTaskType(UserTaskType userTaskType) {
    return userTaskTypeModelMap.get(userTaskType);

  }

}
