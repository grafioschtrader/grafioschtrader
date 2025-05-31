package grafioschtrader.types;

import java.util.List;

import grafiosch.dto.MailSendForwardDefaultBase;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.types.MessageTargetType;

public class MailSendForwardDefault extends MailSendForwardDefaultBase {

  public static void initialize() {
    MailSendForwardDefaultBase.initialize();
    mailSendForwardDefaultMap.put(MessageGTComType.USER_SECURITY_HELD_INACTIVE,
        new MailSendForwardDefaultConfig(MessageTargetType.INTERNAL_MAIL, standardTargetTypeSet, false));
    mailSendForwardDefaultMap.put(MessageGTComType.USER_SECURITY_MISSING_DIV_INTEREST,
        new MailSendForwardDefaultConfig(MessageTargetType.INTERNAL_MAIL, standardTargetTypeSet, false));
    mailSendForwardDefaultMap.put(MessageGTComType.USER_SECURITY_MISSING_CONNECTOR,
        new MailSendForwardDefaultConfig(MessageTargetType.INTERNAL_AND_EXTERNAL_MAIL, intExtTargetTypeSet, false));
  }

  public MailSendForwardDefault(List<ValueKeyHtmlSelectOptions> canRedirectToUsers, boolean isAdmin) {
    super(canRedirectToUsers, isAdmin);
  }
}
