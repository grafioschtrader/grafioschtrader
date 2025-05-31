package grafioschtrader.types;

import grafiosch.entities.MailSettingForward;
import grafiosch.types.IMessageComType;

public enum MessageGTComType implements IMessageComType {

  // The user holds a position of a security although it is no longer active
  USER_SECURITY_HELD_INACTIVE((byte) 1),

  // Possibly the user has not yet entered the dividend or interest of a security
  USER_SECURITY_MISSING_DIV_INTEREST((byte) 2),

  // Possibly missing dividend connector, in the calendar it has dividends for
  // this security.
  USER_SECURITY_MISSING_CONNECTOR((byte) 5),

  // Maybe a historical data provider is not working anymore
  MAIN_ADMIN_HISTORY_PROVIDER_NOT_WORKING(MailSettingForward.MAIN_ADMIN_BASE_VALUE),;

  private final Byte value;

  private MessageGTComType(final Byte value) {
    this.value = value;
  }

  @Override
  public Byte getValue() {
    return this.value;
  }

  @Override
  public Enum<MessageGTComType>[] getValues() {
    return MessageGTComType.values();
  }
}
