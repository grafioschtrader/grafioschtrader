package grafioschtrader.entities;

import java.util.HashMap;
import java.util.Map;

import grafioschtrader.usertask.LimitCudChange;
import grafioschtrader.usertask.ReleaseLogout;
import grafioschtrader.usertask.UserTaskType;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * An user-generated request for a specified role, which cannot be mapped to a
 * specific entity. The addressee can handle the request positively for the
 * sender with different actions.
 *
 */
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

  @Basic(optional = false)
  @Column(name = "id_target_user")
  private Integer idTargetUser;

  /**
   * Addressee for the request
   */
  @Column(name = "id_role_to")
  private Integer idRoleTo;

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
