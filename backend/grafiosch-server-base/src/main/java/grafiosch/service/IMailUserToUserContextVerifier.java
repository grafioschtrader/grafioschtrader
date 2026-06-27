package grafiosch.service;

import grafiosch.entities.User;

/**
 * Strategy for verifying that an <em>initial</em> user-to-user message from a non-privileged user originates from a
 * legitimate in-app context.
 *
 * <p>
 * By policy only privileged users (admin / all-edit) may freely start a direct message to another user. Every other
 * user may only initiate one when the request carries a context reference — for example the security whose creator is
 * being contacted from a watchlist. This interface is the generic contract the mailing service in the
 * {@code grafiosch} library dispatches to; the concrete, entity-specific rules live in the application layer
 * ({@code grafioschtrader}). The library stays free of application references and gains new contexts simply by an
 * additional Spring bean implementing this interface — no library change required.
 *
 * <p>
 * Implementations are discovered as Spring beans and selected by {@link #supports(String)} against the
 * {@code contextEntity} sent with the message.
 */
public interface IMailUserToUserContextVerifier {

  /**
   * Indicates whether this verifier handles the given context entity type.
   *
   * @param contextEntity the context entity-type key sent with the message (e.g. {@code "Securitycurrency"})
   * @return {@code true} if this verifier is responsible for the given context entity type
   */
  boolean supports(String contextEntity);

  /**
   * Verifies that the sender may legitimately start a direct message to the given recipient from the referenced
   * context entity. Implementations throw a {@link SecurityException} when the context does not justify the message
   * (e.g. recipient is not the entity's creator, or the sender has no legitimate relationship to the entity).
   *
   * @param fromUser        the user initiating the message
   * @param idUserTo        the intended recipient
   * @param idEntityContext the id of the context entity referenced by the message
   * @throws SecurityException if the message is not justified by the referenced context
   */
  void verify(User fromUser, Integer idUserTo, Integer idEntityContext);
}
