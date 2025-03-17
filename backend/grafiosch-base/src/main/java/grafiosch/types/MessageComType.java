package grafiosch.types;

import grafiosch.entities.MailSettingForward;

/**
 * Messages can be forwarded to the e-mail account specified by the user. This
 * means that the messages can be marked with this type.
 */
public enum MessageComType implements IMessageComType {

  // General purpose user to user communication
  USER_GENERAL_PURPOSE_USER_TO_USER((byte) 0),
  // Administrator has sent a message to all
  USER_ADMIN_ANNOUNCEMENT((byte) 3),
  // Administrator has sent a personal message
  USER_ADMIN_PERSONAL_TO_USER((byte) 4),
  // User received a proposed change of the shared data
  USER_RECEIVED_PROPOSED_CHANGE((byte) 8),
  // User locked by limits wants one of his limit counters reset. By default, the
  // request for unlocking does not generate a message, but only a request to
  // change the corresponding settings of this user.
  MAIN_ADMIN_RELEASE_LOGOUT((byte) (MailSettingForward.MAIN_ADMIN_BASE_VALUE + 1));
  
  
  private final Byte value;

  private MessageComType(final Byte value) {
    this.value = value;
  }

  @Override
  public Byte getValue() {
    return this.value;
  }

  @Override
  public Enum<MessageComType>[] getValues() {
    return MessageComType.values();
  }
}