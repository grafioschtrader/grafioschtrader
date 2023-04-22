package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.MailSendForwardDefault;
import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.MailSettingForward;
import grafioschtrader.entities.Role;
import grafioschtrader.entities.User;
import grafioschtrader.types.MessageComType;
import grafioschtrader.types.MessageTargetType;
import jakarta.mail.MessagingException;

public class MailSettingForwardJpaRepositoryImpl extends BaseRepositoryImpl<MailSettingForward>
    implements MailSettingForwardJpaRepositoryCustom {

  @Autowired
  private MailSettingForwardJpaRepository mailSettingForwardJpaRepository;

  @Autowired
  private MailSendRecvJpaRepository mailSendRecvJpaRepository;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private MessageSource messagesSource;

  @Value("${gt.main.user.admin.mail}")
  private String mainUserAdminMail;

  @Override
  public MailSettingForward saveOnlyAttributes(MailSettingForward mailSettingForward, MailSettingForward existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if(user.getMostPrivilegedRole() != Role.ROLE_ADMIN && (mailSettingForward.getIdUserRedirect() != null
        || mailSettingForward.getMessageComType().getValue() >=  MailSendForwardDefault.MAIN_ADMIN_BASE_VALUE)
        || !MailSendForwardDefault.mailSendForwardDefaultMap.get(mailSettingForward.getMessageComType()).canRedirect
        && mailSettingForward.getIdUserRedirect() != null) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
    return RepositoryHelper.saveOnlyAttributes(mailSettingForwardJpaRepository, mailSettingForward, existingEntity,
        updatePropertyLevelClasses);
  }

  @Override
  public MailSendForwardDefault getMailSendForwardDefault() {
    var user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<ValueKeyHtmlSelectOptions> vkhsoList = new ArrayList<>();
    var isAdmin = user.getMostPrivilegedRole().equals(Role.ROLE_ADMIN);
    if (isAdmin) {
      userJpaRepository.getIdUserAndNicknameByRoleExcludeUser(Role.ROLE_ADMIN, user.getIdUser()).forEach(
          rs -> vkhsoList.add(new ValueKeyHtmlSelectOptions(String.valueOf(rs.getIdUser()), rs.getIdUser() + " - " + rs.getNickname())));
    }
    return new MailSendForwardDefault(vkhsoList, isAdmin);
  }

  @Override
  public Integer sendMailToMainAdminInternalOrExternal(Integer idUserFrom, String subjectKey, String message,
      MessageComType messageComType) throws MessagingException {
    Optional<User> userOpt = userJpaRepository.findByEmail(mainUserAdminMail);
    if (userOpt.isPresent()) {
      return sendMailInternOrExternal(idUserFrom, userOpt.get().getIdUser(), subjectKey, true, message, messageComType);
    }
    return null;
  }

  @Override
  public Integer sendMailInternOrExternal(Integer idUserFrom, Integer idUserTo, String subject, String message,
      MessageComType messageComType) throws MessagingException {
    return sendMailInternOrExternal(idUserFrom, idUserTo, subject, false, message, messageComType);
  }

  private Integer sendMailInternOrExternal(Integer idUserFrom, Integer idUserTo, String subjectOrSubjectKey,
      boolean isSubjectKey, String message, MessageComType messageComType) throws MessagingException {
    Optional<MailSettingForward> msfOpt = mailSettingForwardJpaRepository.findByIdUserAndMessageComType(idUserTo,
        messageComType.getValue());
    Integer idMailSendRecv = null;

    MessageTargetType msgTargetType = msfOpt.isPresent() ? msfOpt.get().getMessageTargetType()
        : MailSendForwardDefault.mailSendForwardDefaultMap.get(messageComType).messageTargetDefaultType;
    if (msgTargetType != MessageTargetType.NO_MAIL) {
      idUserTo = msfOpt.isPresent() && msfOpt.get().getIdUserRedirect() != null ? msfOpt.get().getIdUserRedirect()
          : idUserTo;
      User userTo = userJpaRepository.findById(idUserTo).get();
      String subject = getSubject(userTo, subjectOrSubjectKey, isSubjectKey);

      if (msgTargetType == MessageTargetType.INTERNAL_MAIL
          || msgTargetType == MessageTargetType.INTERNAL_AND_EXTERNAL_MAIL) {
        idMailSendRecv = mailSendRecvJpaRepository.sendInternalMail(idUserFrom, idUserTo, subject, message);
      }
      if (msgTargetType == MessageTargetType.EXTERNAL_MAIL
          || msgTargetType == MessageTargetType.INTERNAL_AND_EXTERNAL_MAIL) {
        String msgAddition = messagesSource.getMessage("gt.external.message.addition", new Object[] { idUserFrom },
            Locale.forLanguageTag(userTo.getLocaleStr()));
        userJpaRepository.sendSimpleMessage(userTo.getUsername(), "GT: " + subject,
            msgAddition + GlobalConstants.NEW_LINE_AND_RETURN + message);
      }
    }
    return idMailSendRecv;
  }

  private String getSubject(User userTo, String subjectOrSubjectKey, boolean isSubjectKey) {
    if (isSubjectKey) {
      return messagesSource.getMessage(subjectOrSubjectKey, null, Locale.forLanguageTag(userTo.getLocaleStr()));
    } else {
      return subjectOrSubjectKey;
    }
  }

}
