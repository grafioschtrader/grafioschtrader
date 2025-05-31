package grafiosch.service;

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

import grafiosch.BaseConstants;
import grafiosch.dto.MailSendForwardDefaultBase;
import grafiosch.entities.MailSendRecv;
import grafiosch.entities.MailSettingForward;
import grafiosch.entities.Role;
import grafiosch.entities.User;
import grafiosch.repository.MailSendRecvJpaRepository;
import grafiosch.repository.MailSettingForwardJpaRepository;
import grafiosch.repository.RoleJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.repository.UserJpaRepository.EMailLocale;
import grafiosch.types.IMessageComType;
import grafiosch.types.MessageComType;
import grafiosch.types.MessageTargetType;
import grafiosch.types.ReplyToRolePrivateType;
import grafiosch.types.SendRecvType;
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
  public MailSendRecv sendFromRESTApiMultiOrSingle(MailSendRecv mailSendRecv) throws Exception {

    boolean isAdmin = checkForSecurityPolice(mailSendRecv);
    if (ROLE_EVERY_USER.equals(mailSendRecv.getRoleNameTo())) {
      sendExternalAdminMessageToEveryUser(mailSendRecv);
      return sendInternalAdminMessageToEveryUser(mailSendRecv);
    } else {
      MessageComType mct = isAdmin && mailSendRecv.getIdReplyToLocal() == null && mailSendRecv.getIdRoleTo() == null
          ? MessageComType.USER_ADMIN_PERSONAL_TO_USER
          : null;
      if (mct != null) {
        sendMailInternAndOrExternal(mailSendRecv.getIdUserFrom(), mailSendRecv.getIdUserTo(), mailSendRecv.getSubject(),
            null, false, mailSendRecv.getMessage(), mct, false);
      }
      return sendFromRESTApi(mailSendRecv);
    }
  }

  private boolean checkForSecurityPolice(MailSendRecv mailSendRecv) {
    final User fromUser = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    boolean isAdmin = fromUser.getMostPrivilegedRole() == Role.ROLE_ADMIN;
    if ((Role.ROLE_LIMIT_EDIT.equals(mailSendRecv.getRoleNameTo())
        || Role.ROLE_USER.equals(mailSendRecv.getRoleNameTo()) || ROLE_EVERY_USER.equals(mailSendRecv.getRoleNameTo()))
        && !isAdmin) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    // this.initialMsgUserToUserSecurityCheck(mailSendRecv, fromUser, isAdmin);
    return isAdmin;
  }

  /**
   * This test cannot be performed. Because it must be possible that a user sends an initial message to a user if it is
   * related to entity. For example, a message to the creator of a security. Maybe the information class and entity ID
   * should also be transferred and checked in such a scenario?
   */
  private void initialMsgUserToUserSecurityCheck(MailSendRecv mailSendRecv, User fromUser, boolean isAdmin) {
    if (!isAdmin && mailSendRecv.getIdUserTo() != null) {
      if (mailSendRecv.getIdReplyToLocal() == null) {
        // Initial message from user to user only possible for admin
        throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
      } else {
        Optional<MailSendRecv> initialMsgOpt = mailSendRecvJpaRepository.findById(mailSendRecv.getIdReplyToLocal());
        if (initialMsgOpt.isEmpty()) {
          // Initial message must be present otherwise misuse is possible
          throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
        } else {
          MailSendRecv initialMsg = initialMsgOpt.get();
          if (!(initialMsg.getIdUserFrom().equals(fromUser.getId()) || fromUser.getId().equals(initialMsg.getIdUserTo())
              || (initialMsg.getIdRoleTo() != null && fromUser.hasIdRole(initialMsg.getIdRoleTo())))) {
            // There is no connection of this user with the initial messages.
            // Neither via the sender nor the receiver or the receiver role.
            throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
          }
        }
      }
    }
  }

  private void sendExternalAdminMessageToEveryUser(MailSendRecv mailSendRecv) throws MessagingException {
    List<EMailLocale> targetEmails = userJpaRepository.getEmailExcludeWhenMsgComTypeAndTargetTypeExists(
        MessageComType.USER_ADMIN_ANNOUNCEMENT.getValue(), MessageTargetType.INTERNAL_MAIL.getValue());
    Map<String, List<EMailLocale>> emailsPerLocale = targetEmails.stream()
        .collect(Collectors.groupingBy(EMailLocale::getLocale));
    for (String localeStr : emailsPerLocale.keySet()) {
      List<EMailLocale> emailList = emailsPerLocale.get(localeStr);
      String[] usersTo = emailList.stream().map(e -> e.getEmail()).toArray(String[]::new);
      sendExternal(MessageTargetType.EXTERNAL_MAIL, mailSendRecv.getIdUserFrom(), mailSendRecv.getSubject(),
          mailSendRecv.getMessage(), usersTo, localeStr);
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
   */
  public Integer sendMailToMainAdminInternalOrExternal(Integer idUserFrom, String subjectKey, Object[] subjectValues,
      String message, MessageComType messageComType) throws MessagingException {
    Optional<User> userOpt = userJpaRepository.findByEmail(mainUserAdminMail);
    if (userOpt.isPresent()) {
      return sendMailInternAndOrExternal(idUserFrom, userOpt.get().getIdUser(), subjectKey, subjectValues, true,
          message, messageComType, true);
    }
    return null;
  }

  /**
   * Sending an internal or external message to any user.
   */
  public Integer sendMailInternAndOrExternal(Integer idUserFrom, Integer idUserTo, String subject, String message,
      IMessageComType messageComType) throws MessagingException {
    return sendMailInternAndOrExternal(idUserFrom, idUserTo, subject, null, false, message, messageComType, true);
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

  private Integer sendMailInternAndOrExternal(Integer idUserFrom, Integer idUserTo, String subjectOrSubjectKey,
      Object subjectValues[], boolean isSubjectKey, String message, IMessageComType messageComType,
      boolean includeInternalMail) throws MessagingException {
    Optional<MailSettingForward> msfOpt = mailSettingForwardJpaRepository.findByIdUserAndMessageComType(idUserTo,
        messageComType.getValue());
    Integer idMailSendRecv = null;

    MessageTargetType msgTargetType = msfOpt.isPresent() ? msfOpt.get().getMessageTargetType()
        : MailSendForwardDefaultBase.mailSendForwardDefaultMap.get(messageComType).messageTargetDefaultType;
    if (msgTargetType != MessageTargetType.NO_MAIL) {
      idUserTo = msfOpt.isPresent() && msfOpt.get().getIdUserRedirect() != null ? msfOpt.get().getIdUserRedirect()
          : idUserTo;
      User userTo = userJpaRepository.findById(idUserTo).get();
      String subject = getSubject(userTo, subjectOrSubjectKey, subjectValues, isSubjectKey);

      if (includeInternalMail && (msgTargetType == MessageTargetType.INTERNAL_MAIL
          || msgTargetType == MessageTargetType.INTERNAL_AND_EXTERNAL_MAIL)) {
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
          msgAddition + BaseConstants.RETURN_AND_NEW_LINE + message);
    }
  }

  private String getSubject(User userTo, String subjectOrSubjectKey, Object[] subjectValues, boolean isSubjectKey) {
    if (isSubjectKey) {
      return messagesSource.getMessage(subjectOrSubjectKey, subjectValues,
          Locale.forLanguageTag(userTo.getLocaleStr()));
    } else {
      return subjectOrSubjectKey;
    }
  }

}
