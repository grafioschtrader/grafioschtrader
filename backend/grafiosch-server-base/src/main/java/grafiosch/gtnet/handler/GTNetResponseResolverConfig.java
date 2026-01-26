package grafiosch.gtnet.handler;

import grafiosch.entities.GTNet;
import grafiosch.gtnet.GTNetMessageCode;

/**
 * Configuration interface for GTNetResponseResolver.
 *
 * Provides application-specific functionality such as:
 * <ul>
 *   <li>Looking up typed message codes from byte values</li>
 *   <li>Getting the local GTNet entry ID</li>
 *   <li>Getting max limits for entity kinds</li>
 * </ul>
 *
 * Applications should implement this interface and inject it into GTNetResponseResolver.
 */
public interface GTNetResponseResolverConfig {

  /**
   * Looks up a message code by its byte value.
   *
   * @param codeValue the byte value
   * @return the message code
   */
  GTNetMessageCode lookupMessageCode(byte codeValue);

  /**
   * Gets the ID of the local GTNet entry for this server instance.
   *
   * @return the local GTNet ID, or null if not configured
   */
  Integer getMyGTNetEntryId();

  /**
   * Gets the max limit for an entity kind from a GTNet entry.
   *
   * @param gtNet the GTNet entry
   * @param entityKindValue the entity kind byte value
   * @return the max limit, or null if not found
   */
  Short getMaxLimitForEntityKind(GTNet gtNet, byte entityKindValue);
}
