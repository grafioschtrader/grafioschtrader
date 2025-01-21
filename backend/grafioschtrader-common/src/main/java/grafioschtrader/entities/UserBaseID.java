package grafioschtrader.entities;

import grafiosch.entities.BaseID;

/**
 * This class should extend an entity if the entities of an information class are associated with a user.
 */
public abstract class UserBaseID extends BaseID {

  public abstract Integer getIdUser();

  public abstract void setIdUser(Integer idUser);
}
