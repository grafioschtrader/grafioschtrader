package grafiosch.entities;

/**
 * This class should extend an entity if the entities of an information class are associated with a user.
 */
public abstract class UserBaseID extends BaseID<Integer> {

  public abstract Integer getIdUser();

  public abstract void setIdUser(Integer idUser);
}
