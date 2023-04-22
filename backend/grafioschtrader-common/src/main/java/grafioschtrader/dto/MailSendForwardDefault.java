package grafioschtrader.dto;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import grafioschtrader.common.EnumHelper;
import grafioschtrader.types.MessageComType;
import grafioschtrader.types.MessageTargetType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Contains the default settings and the possible configuration for MailSendForward")
public class MailSendForwardDefault {

  public static final byte MAIN_ADMIN_BASE_VALUE = 50;
  public static final Map<MessageComType, MailSendForwardDefaultConfig> mailSendForwardDefaultMap = new HashMap<>();
  public List<ValueKeyHtmlSelectOptions> canRedirectToUsers;
  public Map<MessageComType, MailSendForwardDefaultConfig> mailSendForwardDefaultMapForUser;

  private static EnumSet<MessageTargetType> standardTargetTypeSet = EnumSet.of(MessageTargetType.EXTERNAL_MAIL,
      MessageTargetType.INTERNAL_MAIL, MessageTargetType.INTERNAL_AND_EXTERNAL_MAIL);

  static {
    mailSendForwardDefaultMap.put(MessageComType.USER_SECURITY_HELD_INACTIVE,
        new MailSendForwardDefaultConfig(MessageTargetType.INTERNAL_MAIL, standardTargetTypeSet, false));
    mailSendForwardDefaultMap.put(MessageComType.USER_SECURITY_MISSING_DIV_INTEREST,
        new MailSendForwardDefaultConfig(MessageTargetType.INTERNAL_MAIL, standardTargetTypeSet, false));
    mailSendForwardDefaultMap.put(MessageComType.MAIN_ADMIN_RELEASE_LOGOUT,
        new MailSendForwardDefaultConfig(MessageTargetType.NO_MAIL,
            EnumHelper.cloneSetAndAddEnum(standardTargetTypeSet, MessageTargetType.NO_MAIL), true));
  }

  public MailSendForwardDefault(List<ValueKeyHtmlSelectOptions> canRedirectToUsers, boolean isAdmin) {
    this.canRedirectToUsers = canRedirectToUsers;
    this.mailSendForwardDefaultMapForUser = mailSendForwardDefaultMap.entrySet().stream()
        .filter(es -> es.getKey().getValue() < MAIN_ADMIN_BASE_VALUE
            || es.getKey().getValue() >= MAIN_ADMIN_BASE_VALUE && isAdmin)
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  public static class MailSendForwardDefaultConfig {

    @Schema(description = "Contains the default message channel.")
    public final MessageTargetType messageTargetDefaultType;

    @Schema(description = "Which message channel can be used for this type of message.")
    public final EnumSet<MessageTargetType> mttPossibleTypeSet;

    @Schema(description = "Can the message be forwarded to another user?")
    public final boolean canRedirect;

    public MailSendForwardDefaultConfig(MessageTargetType messageTargetType,
        EnumSet<MessageTargetType> mttPossibleTypeSet, boolean canRedirect) {
      this.messageTargetDefaultType = messageTargetType;
      this.mttPossibleTypeSet = mttPossibleTypeSet;
      this.canRedirect = canRedirect;
    }
  }
}
