package grafioschtrader.service;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.beanutils.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.MailSendForwardDefault;
import grafioschtrader.entities.MailSendRecv;
import grafioschtrader.entities.MailSettingForward;
import grafioschtrader.entities.Role;
import grafioschtrader.entities.User;
import grafioschtrader.repository.MailSendRecvJpaRepository;
import grafioschtrader.repository.MailSettingForwardJpaRepository;
import grafioschtrader.repository.RoleJpaRepository;
import grafioschtrader.repository.UserJpaRepository;
import grafioschtrader.repository.UserJpaRepository.EMailLocale;
import grafioschtrader.types.MessageComType;
import grafioschtrader.types.MessageTargetType;
import grafioschtrader.types.ReplyToRolePrivateType;
import grafioschtrader.types.SendRecvType;
import jakarta.mail.MessagingException;

@Service
public class SendMailInternalExternalService {

  // This is not a real role. This is used to send a message to everyone.
  private static final String ROLE_EVERY_USER = "ROLE_EVERY_USER";

  @Autowired
  private MailSettingForwardJpaRepository mailSettingForwardJpaRepository;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private MailSendRecvJpaRepository mailSendRecvJpaRepository;

  @Autowired
  private MessageSource messagesSource;

  @Autowired
  private RoleJpaRepository roleJpaRepository;

  @Value("${gt.main.user.admin.mail}")
  private String mainUserAdminMail;

  @Autowired
  private MailExternalService mailExternalService;

  /**
   * This message comes in via the REST API and needs additional validation.
   */
  public MailSendRecv sendFromRESTApiMultiSingle(MailSendRecv mailSendRecv) throws Exception {

    final User fromUser = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if ((Role.ROLE_LIMIT_EDIT.equals(mailSendRecv.getRoleNameTo())
        || Role.ROLE_USER.equals(mailSendRecv.getRoleNameTo()) || ROLE_EVERY_USER.equals(mailSendRecv.getRoleNameTo()))
        && fromUser.getMostPrivilegedRole() != Role.ROLE_ADMIN) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }

    if (ROLE_EVERY_USER.equals(mailSendRecv.getRoleNameTo())) {
      sendExternalAdminMessageToEveryUser(mailSendRecv);
      return sendInternalAdminMessageToEveryUser(mailSendRecv);
    } else {
      return sendFromRESTApi(mailSendRecv);
    }
  }

  private void sendExternalAdminMessageToEveryUser(MailSendRecv mailSendRecv) throws MessagingException {
    List<EMailLocale> targetEmails = userJpaRepository.getEmailExcludeWhenMsgComTypeAndTargetTypeExists(
        MessageComType.USER_ADMIN_ANNOUNCEMENT.getValue(), MessageTargetType.INTERNAL_MAIL.getValue());
    Map<String, List<EMailLocale>> emailsPerLocale = targetEmails.stream()
        .collect(Collectors.groupingBy(EMailLocale::getLocale));
    for (String localeStr: emailsPerLocale.keySet()) {
      List<EMailLocale> emailList = emailsPerLocale.get(localeStr);
      String[] usersTo = emailList.stream().map(e -> e.getEmail()).toArray(String[]::new);
      sendExternal(MessageTargetType.EXTERNAL_MAIL, mailSendRecv.getIdUserFrom(), mailSendRecv.getSubject(), mailSendRecv.getMessage(),
          usersTo, localeStr);
    }
    
  }

  private MailSendRecv sendInternalAdminMessageToEveryUser(MailSendRecv mailSendRecv)
      throws IllegalAccessException, InvocationTargetException {
    MailSendRecv msr = new MailSendRecv();
    BeanUtils.copyProperties(msr, mailSendRecv);
    msr.setRoleNameTo(Role.ROLE_USER);
    sendFromRESTApi(msr);
    mailSendRecv.setRoleNameTo(Role.ROLE_LIMIT_EDIT);
    return sendFromRESTApi(mailSendRecv);
  }

  private MailSendRecv sendFromRESTApi(MailSendRecv mailSendRecv) {
    MailSendRecv mailSendRecvS = saveInternalMail(mailSendRecv.getIdUserFrom(), mailSendRecv.getIdUserTo(),
        mailSendRecv.getRoleNameTo(), mailSendRecv.getSubject(), mailSendRecv.getMessage(),
        mailSendRecv.getIdReplyToLocal(), isRoleReplySend(mailSendRecv));
    connectRoleNameToMail(mailSendRecvS);
    return mailSendRecvS;
  }

  public void connectRoleNameToMail(MailSendRecv mailSendRecv) {
    if (mailSendRecv.getIdRoleTo() != null) {
      List<Role> roles = roleJpaRepository.findAll();
      roles.stream().filter(r -> r.getIdRole().equals(mailSendRecv.getIdRoleTo())).findFirst()
          .ifPresent(r -> mailSendRecv.setRoleNameTo(r.getRolename()));
    }
  }

  private ReplyToRolePrivateType isRoleReplySend(MailSendRecv mailSendRecv) {
    if (mailSendRecv.getIdReplyToLocal() != null && (mailSendRecv.getReplyToRolePrivate() != null
        && mailSendRecv.getReplyToRolePrivate() != ReplyToRolePrivateType.REPLY_IS_PRIVATE
        || mailSendRecv.getReplyToRolePrivate() == null)) {
      Optional<MailSendRecv> groupMsr = mailSendRecvJpaRepository.findById(mailSendRecv.getIdReplyToLocal());
      if (groupMsr.isPresent() && groupMsr.get().getIdRoleTo() != null) {
        final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
        return user.hasIdRole(groupMsr.get().getIdRoleTo()) ? ReplyToRolePrivateType.REPLY_AS_ROLE
            : mailSendRecv.getReplyToRolePrivate();
      }
    }
    return mailSendRecv.getReplyToRolePrivate();
  }

  /**
   * Sending an internal or external message to the main administrator.
   * 
   * @param idUserFrom
   * @param subjectKey
   * @param message
   * @param messageComType
   * @return
   * @throws MessagingException
   */
  public Integer sendMailToMainAdminInternalOrExternal(Integer idUserFrom, String subjectKey, String message,
      MessageComType messageComType) throws MessagingException {
    Optional<User> userOpt = userJpaRepository.findByEmail(mainUserAdminMail);
    if (userOpt.isPresent()) {
      return sendMailInternOrExternal(idUserFrom, userOpt.get().getIdUser(), subjectKey, true, message, messageComType);
    }
    return null;
  }

  /**
   * Sending an internal or external message to any user.
   * 
   * @param idUserFrom
   * @param idUserTo
   * @param subject
   * @param message
   * @param messageComType
   * @return
   * @throws MessagingException
   */
  public Integer sendMailInternOrExternal(Integer idUserFrom, Integer idUserTo, String subject, String message,
      MessageComType messageComType) throws MessagingException {
    return sendMailInternOrExternal(idUserFrom, idUserTo, subject, false, message, messageComType);
  }

  public Integer sendInternalMail(Integer idUserFrom, Integer idUserTo, String subject, String message) {
    return sendInternalMail(idUserFrom, idUserTo, null, subject, message, null, ReplyToRolePrivateType.REPLY_NORMAL);
  }

  public Integer sendInternalMail(Integer idUserFrom, Integer idUserTo, String roleName, String subject, String message,
      Integer idReplyToLocal, ReplyToRolePrivateType replyToRolePrivate) {
    return saveInternalMail(idUserFrom, idUserTo, roleName, subject, message, idReplyToLocal, replyToRolePrivate)
        .getIdMailSendRecv();
  }

  private MailSendRecv saveInternalMail(Integer idUserFrom, Integer idUserTo, String roleName, String subject,
      String message, Integer idReplyToLocal, ReplyToRolePrivateType replyToRolePrivate) {
    MailSendRecv mailSendRecvS = new MailSendRecv(SendRecvType.SEND, idUserFrom, idUserTo, roleName, subject, message,
        idReplyToLocal, replyToRolePrivate);
    setIdRoleToFromRoleName(mailSendRecvS);
    mailSendRecvS = mailSendRecvJpaRepository.save(mailSendRecvS);
    MailSendRecv mailSendRecvR = new MailSendRecv(SendRecvType.RECEIVE, idUserFrom, idUserTo, roleName, subject,
        message, mailSendRecvS.getIdReplyToLocal() == null ? mailSendRecvS.getIdMailSendRecv()
            : mailSendRecvS.getIdReplyToLocal(),
        replyToRolePrivate);
    mailSendRecvR.setIdRoleTo(mailSendRecvS.getIdRoleTo());
    return mailSendRecvJpaRepository.save(mailSendRecvR);

  }

  private void setIdRoleToFromRoleName(MailSendRecv mailSendRecv) {
    if (mailSendRecv.getRoleNameTo() != null) {
      Role role = roleJpaRepository.findByRolename(mailSendRecv.getRoleNameTo());
      mailSendRecv.setIdRoleTo(role.getIdRole());
    }
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
        idMailSendRecv = sendInternalMail(idUserFrom, idUserTo, subject, message);
      }
      sendExternal(msgTargetType, idUserFrom, subject, message, new String[] { userTo.getUsername() },
          userTo.getLocaleStr());
    }
    return idMailSendRecv;
  }

  private void sendExternal(MessageTargetType msgTargetType, Integer idUserFrom, String subject, String message,
      String[] usersTo, String localeStr) throws MessagingException {
    if (msgTargetType == MessageTargetType.EXTERNAL_MAIL
        || msgTargetType == MessageTargetType.INTERNAL_AND_EXTERNAL_MAIL) {
      String msgAddition = messagesSource.getMessage("gt.external.message.addition", new Object[] { idUserFrom },
          Locale.forLanguageTag(localeStr));
      mailExternalService.sendSimpleMessageAsync(usersTo, "GT: " + subject,
          msgAddition + GlobalConstants.NEW_LINE_AND_RETURN + message);
    }
  }

  private String getSubject(User userTo, String subjectOrSubjectKey, boolean isSubjectKey) {
    if (isSubjectKey) {
      return messagesSource.getMessage(subjectOrSubjectKey, null, Locale.forLanguageTag(userTo.getLocaleStr()));
    } else {
      return subjectOrSubjectKey;
    }
  }

}
