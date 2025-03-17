package grafiosch.entities;

/**
 * This class should extend an entity if the entities are associated with an information class of a client.
 */
public abstract class TenantBaseID extends BaseID {

  public abstract Integer getIdTenant();

  public abstract void setIdTenant(Integer idTenant);

}
