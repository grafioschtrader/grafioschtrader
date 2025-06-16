package grafiosch.repository;

import grafiosch.entities.ProposeUserTask;
import jakarta.mail.MessagingException;

/**
 * Custom repository interface for managing user task proposals. Provides specialized operations for handling user
 * release requests and administrative task management beyond standard CRUD operations.
 */
public interface ProposeUserTaskJpaRepositoryCustom extends BaseRepositoryCustom<ProposeUserTask> {

  /**
   * Creates a release logout proposal for a user who has violated system policies. This method generates a proposal
   * request that can be reviewed and approved by administrators to reset user violation counters and restore normal
   * access privileges.
   * 
   * The proposal includes the specified field to be reset and sends notification to the main administrator about the
   * user's request for reinstatement.
   *
   * @param idTargetUser the ID of the user requesting release from logout restrictions
   * @param field        the specific field name to be reset (e.g., security breach count)
   * @param note         explanatory note or reason for the release request
   */
  void createReleaseLougout(Integer idTargetUser, String field, String note) throws Exception;

  /**
   * Rejects a pending user task proposal and notifies the requesting user. This method permanently removes the proposal
   * from the system and sends an email notification to the user explaining why their request was denied.
   * 
   * The rejection is processed by an administrator and includes a personalized rejection message that will be sent to
   * the user's registered email address.
   *
   * @param idProposeRequest the ID of the proposal request to reject
   * @param rejectNote       the explanation message sent to the user about the rejection
   * @return confirmation message indicating the rejection was processed and email sent
   * @throws MessagingException if there is an error sending the rejection notification email
   */
  String rejectUserTask(Integer idProposeRequest, String rejectNote) throws MessagingException;
}
