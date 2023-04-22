package grafioschtrader.types;

import grafioschtrader.dto.MailSendForwardDefault;

/**
 * Messages can be forwarded to the e-mail account specified by the user. This
 * means that the messages can be marked with this type.
 *
 */
public enum MessageComType {
 
  // General purpose user to user communication
  USER_GENERAL_PURPOSE_USER_TO_USER((byte) 0),
  // The user holds a position of a security although it is no longer active
  USER_SECURITY_HELD_INACTIVE((byte) 1),
  // Possibly the user has not yet entered the dividend or interest of a security
  USER_SECURITY_MISSING_DIV_INTEREST((byte) 2),
  // User received a proposed change of the shared data
  USER_RECEIVED_PROPOSED_CHANGE((byte) 8),
  // Maybe a historical data provider is not working anymore
  MAIN_ADMIN_HISTORY_PROVIDER_NOT_WORKING(MailSendForwardDefault.MAIN_ADMIN_BASE_VALUE),
  // User locked by limits wants one of his limit counters reset. By default, the
  // request for unlocking does not generate a message, but only a request to
  // change the corresponding settings of this user.
  MAIN_ADMIN_RELEASE_LOGOUT((byte) (MailSendForwardDefault.MAIN_ADMIN_BASE_VALUE + 1));

  
 
  private final Byte value;

  private MessageComType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static MessageComType getMessageComTypeByValue(byte value) {
    for (MessageComType messageComType : MessageComType.values()) {
      if (messageComType.getValue() == value) {
        return messageComType;
      }
    }
    return null;
  }
}
