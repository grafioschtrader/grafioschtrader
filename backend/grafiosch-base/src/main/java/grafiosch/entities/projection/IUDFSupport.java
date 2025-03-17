package grafiosch.entities.projection;

public interface IUDFSupport {
  /**
   * Checks whether a client has access to this entity.
   * @param idTenant id of the tenant
   * @return true if the client has access to this entity
   */
  boolean tenantHasAccess(Integer idTenant);
}
