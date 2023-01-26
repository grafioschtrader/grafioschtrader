package grafioschtrader.types;



public enum MessageComType {

  /** General purpose user to user communication **/
  GENERAL_PUROPOSE_USER_TO_USER((byte) 0),
  /** Missing user action in the system. For example, the user has an open position on a security that is now no longer active. **/
  SYSTEM_MISSING_USER_ACTION((byte) 1),
  /** User received a proposed change of the shared data **/
  RECEIVED_PROPOSED_CHANGE((byte) 2);
      
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
