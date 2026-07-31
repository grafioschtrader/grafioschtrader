package grafioschtrader.types;

import java.util.List;

import grafiosch.common.EnumHelper;
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
    // The consistency report is only produced when something is wrong, so it defaults to an internal message rather
    // than to NO_MAIL. NO_MAIL is offered as well, so that an administrator who knows about a drift can silence it.
    mailSendForwardDefaultMap.put(MessageGTComType.MAIN_ADMIN_HOLD_TABLE_INCONSISTENT,
        new MailSendForwardDefaultConfig(MessageTargetType.INTERNAL_MAIL,
            EnumHelper.cloneSetAndAddEnum(standardTargetTypeSet, MessageTargetType.NO_MAIL), false));
  }

  public MailSendForwardDefault(List<ValueKeyHtmlSelectOptions> canRedirectToUsers, boolean isAdmin) {
    super(canRedirectToUsers, isAdmin);
  }
}
