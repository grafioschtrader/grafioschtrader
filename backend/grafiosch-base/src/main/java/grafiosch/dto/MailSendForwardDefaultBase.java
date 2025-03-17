package grafiosch.dto;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import grafiosch.common.EnumHelper;
import grafiosch.entities.MailSettingForward;
import grafiosch.types.IMessageComType;
import grafiosch.types.MessageComType;
import grafiosch.types.MessageTargetType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Contains the default settings and the possible configuration for MailSendForward")
public class MailSendForwardDefaultBase {

  public static final Map<IMessageComType, MailSendForwardDefaultConfig> mailSendForwardDefaultMap = new HashMap<>();
  public List<ValueKeyHtmlSelectOptions> canRedirectToUsers;
  public Map<IMessageComType, MailSendForwardDefaultConfig> mailSendForwardDefaultMapForUser;

  protected static EnumSet<MessageTargetType> intExtTargetTypeSet = EnumSet.of(MessageTargetType.INTERNAL_MAIL,
      MessageTargetType.INTERNAL_AND_EXTERNAL_MAIL);
  protected static EnumSet<MessageTargetType> standardTargetTypeSet = EnumSet.of(MessageTargetType.EXTERNAL_MAIL,
      MessageTargetType.INTERNAL_MAIL, MessageTargetType.INTERNAL_AND_EXTERNAL_MAIL);

  public static void initialize() {
    mailSendForwardDefaultMap.put(MessageComType.USER_ADMIN_ANNOUNCEMENT,
        new MailSendForwardDefaultConfig(MessageTargetType.INTERNAL_AND_EXTERNAL_MAIL, intExtTargetTypeSet, false));
    mailSendForwardDefaultMap.put(MessageComType.USER_ADMIN_PERSONAL_TO_USER,
        new MailSendForwardDefaultConfig(MessageTargetType.INTERNAL_AND_EXTERNAL_MAIL, intExtTargetTypeSet, false));
    mailSendForwardDefaultMap.put(MessageComType.MAIN_ADMIN_RELEASE_LOGOUT,
        new MailSendForwardDefaultConfig(MessageTargetType.NO_MAIL,
            EnumHelper.cloneSetAndAddEnum(standardTargetTypeSet, MessageTargetType.NO_MAIL), true));
  }

  public MailSendForwardDefaultBase(List<ValueKeyHtmlSelectOptions> canRedirectToUsers, boolean isAdmin) {
    this.canRedirectToUsers = canRedirectToUsers;
    this.mailSendForwardDefaultMapForUser = mailSendForwardDefaultMap.entrySet().stream()
        .filter(es -> es.getKey().getValue() < MailSettingForward.MAIN_ADMIN_BASE_VALUE
            || es.getKey().getValue() >= MailSettingForward.MAIN_ADMIN_BASE_VALUE && isAdmin)
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
