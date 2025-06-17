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

/**
 * Service for managing both internal and external mail delivery within the framework.
 * 
 * <p>
 * This service handles the complete mail flow including internal messaging system, external email delivery, role-based
 * messaging, and user forwarding preferences. It supports various message types including admin announcements,
 * user-to-user communication, and role-based conversations with appropriate security controls.
 * 
 * <p>
 * Key features:
 * <ul>
 * <li>Dual delivery system (internal messages + external emails)</li>
 * <li>Role-based messaging with privacy controls</li>
 * <li>User forwarding preferences and redirection</li>
 * <li>Admin broadcast messaging to all users</li>
 * <li>Conversation threading and reply management</li>
 * <li>Security validation for message permissions</li>
 * </ul>
 */
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
   * Processes mail messages received via REST API with validation and routing.
   * 
   * <p>
   * This method handles incoming messages from the REST API and applies appropriate security checks, routing logic, and
   * delivery mechanisms. It supports both individual and broadcast messaging, with special handling for admin
   * announcements.
   * 
   * @param mailSendRecv the mail message to process and send
   * @return the processed and saved {@link MailSendRecv} entity
   * @throws Exception         if validation fails, security is breached, or delivery fails
   * @throws SecurityException if the user lacks permission for the requested operation
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

  /**
   * Validates security permissions for the mail operation.
   * 
   * <p>
   * Checks whether the current user has the necessary permissions to send messages to the specified targets (roles,
   * users, or broadcast recipients). Admin-only operations are restricted to users with admin privileges.
   * 
   * @param mailSendRecv the mail message to validate
   * @return true if the current user is an admin, false otherwise
   * @throws SecurityException if the user lacks permission for the operation
   */
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
   * Security validation for initial user-to-user messages (currently disabled).
   * 
   * <p>
   * This method would validate that non-admin users can only send initial messages to other users in specific contexts
   * (e.g., related to shared entities). The validation is currently commented out as it requires additional context
   * about entity relationships.
   * 
   * @param mailSendRecv the message being sent
   * @param fromUser     the user sending the message
   * @param isAdmin      whether the sender has admin privileges
   * @throws SecurityException if the message violates security policies
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

  /**
   * Sends external email announcements to all users who haven't configured internal-only delivery.
   * 
   * <p>
   * This method handles admin broadcast messages by sending external emails to users based on their locale preferences
   * and forwarding settings. Users who have configured internal-only delivery are excluded from external email
   * delivery.
   * 
   * @param mailSendRecv the admin announcement message to broadcast
   * @throws MessagingException if external email delivery fails
   */
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

  /**
   * Sends internal messages to all user roles for admin announcements.
   * 
   * <p>
   * Creates separate internal messages for both USER and LIMIT_EDIT roles to ensure all users receive admin
   * announcements in their internal message inbox.
   * 
   * @param mailSendRecv the admin announcement to send internally
   * @return the last processed {@link MailSendRecv} entity
   */
  private MailSendRecv sendInternalAdminMessageToEveryUser(MailSendRecv mailSendRecv)
      throws IllegalAccessException, InvocationTargetException {
    MailSendRecv msr = new MailSendRecv();
    BeanUtils.copyProperties(msr, mailSendRecv);
    msr.setRoleNameTo(Role.ROLE_USER);
    sendFromRESTApi(msr);
    mailSendRecv.setRoleNameTo(Role.ROLE_LIMIT_EDIT);
    return sendFromRESTApi(mailSendRecv);
  }

  /**
   * Processes and saves a message received from the REST API.
   * 
   * <p>
   * Handles the core message processing logic including role name resolution and internal message storage. This method
   * is used for both individual and role-based messages after security validation.
   * 
   * @param mailSendRecv the message to process
   * @return the saved {@link MailSendRecv} entity with role information attached
   */
  private MailSendRecv sendFromRESTApi(MailSendRecv mailSendRecv) {
    MailSendRecv mailSendRecvS = saveInternalMail(mailSendRecv.getIdUserFrom(), mailSendRecv.getIdUserTo(),
        mailSendRecv.getRoleNameTo(), mailSendRecv.getSubject(), mailSendRecv.getMessage(),
        mailSendRecv.getIdReplyToLocal(), isRoleReplySend(mailSendRecv));
    connectRoleNameToMail(mailSendRecvS);
    return mailSendRecvS;
  }

  /**
   * Attaches role name information to mail messages for display purposes.
   * 
   * <p>
   * Resolves role IDs to human-readable role names for messages addressed to roles. This is used for UI display to show
   * which role received the message.
   * 
   * @param mailSendRecv the message to enhance with role name information
   */
  public void connectRoleNameToMail(MailSendRecv mailSendRecv) {
    if (mailSendRecv.getIdRoleTo() != null) {
      List<Role> roles = roleJpaRepository.findAll();
      roles.stream().filter(r -> r.getIdRole().equals(mailSendRecv.getIdRoleTo())).findFirst()
          .ifPresent(r -> mailSendRecv.setRoleNameTo(r.getRolename()));
    }
  }

  /**
   * Determines the appropriate reply privacy type for role-based conversations.
   * 
   * <p>
   * Analyzes the conversation context to determine whether replies should be visible to all role members or remain
   * private. This depends on the user's role membership and the original message configuration.
   * 
   * @param mailSendRecv the reply message being sent
   * @return the appropriate {@link ReplyToRolePrivateType} for the reply
   */
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
   * Sends a message to the main administrator via internal and/or external channels.
   * 
   * <p>
   * This method is used for system-generated messages that need to reach the main administrator, such as security
   * alerts or system notifications.
   * 
   * @param idUserFrom     the ID of the user or system generating the message
   * @param subjectKey     the message key for internationalized subject line
   * @param subjectValues  parameters for the subject line message template
   * @param message        the message content
   * @param messageComType the type of communication for forwarding rules
   * @return the ID of the created internal message, or null if admin not found
   * @throws MessagingException if external email delivery fails
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
   * Sends a message to any user via internal and/or external channels.
   * 
   * <p>
   * Convenience method for sending messages with pre-formatted subjects. Delivery method is determined by user
   * preferences and message type.
   * 
   * @param idUserFrom     the ID of the sender
   * @param idUserTo       the ID of the recipient
   * @param subject        the message subject line
   * @param message        the message content
   * @param messageComType the type of communication for forwarding rules
   * @return the ID of the created internal message, or null if not delivered internally
   * @throws MessagingException if external email delivery fails
   */
  public Integer sendMailInternAndOrExternal(Integer idUserFrom, Integer idUserTo, String subject, String message,
      IMessageComType messageComType) throws MessagingException {
    return sendMailInternAndOrExternal(idUserFrom, idUserTo, subject, null, false, message, messageComType, true);
  }

  /**
   * Sends an internal-only message between users.
   * 
   * <p>
   * Convenience method for simple user-to-user internal messaging without external email delivery or special forwarding
   * rules.
   * 
   * @param idUserFrom the ID of the sender
   * @param idUserTo   the ID of the recipient
   * @param subject    the message subject line
   * @param message    the message content
   * @return the ID of the created internal message
   */
  public Integer sendInternalMail(Integer idUserFrom, Integer idUserTo, String subject, String message) {
    return sendInternalMail(idUserFrom, idUserTo, null, subject, message, null, ReplyToRolePrivateType.REPLY_NORMAL);
  }

  /**
   * Sends an internal message with full conversation and role support.
   * 
   * <p>
   * Creates internal messages with support for role addressing, conversation threading, and reply privacy controls.
   * This is the core method for internal message delivery.
   * 
   * @param idUserFrom         the ID of the sender
   * @param idUserTo           the ID of the recipient (null for role messages)
   * @param roleName           the target role name (null for user messages)
   * @param subject            the message subject line
   * @param message            the message content
   * @param idReplyToLocal     the ID of the message being replied to (null for new conversations)
   * @param replyToRolePrivate the privacy level for role conversation replies
   * @return the ID of the created internal message
   */
  public Integer sendInternalMail(Integer idUserFrom, Integer idUserTo, String roleName, String subject, String message,
      Integer idReplyToLocal, ReplyToRolePrivateType replyToRolePrivate) {
    return saveInternalMail(idUserFrom, idUserTo, roleName, subject, message, idReplyToLocal, replyToRolePrivate)
        .getIdMailSendRecv();
  }

  /**
   * Creates and saves internal message entities for the messaging system.
   * 
   * <p>
   * This method creates both SEND and RECEIVE message records to maintain the dual-record system used by the internal
   * messaging infrastructure. It handles role resolution and conversation threading.
   * 
   * @param idUserFrom         the ID of the sender
   * @param idUserTo           the ID of the recipient (null for role messages)
   * @param roleName           the target role name (null for user messages)
   * @param subject            the message subject line
   * @param message            the message content
   * @param idReplyToLocal     the ID of the message being replied to (null for new conversations)
   * @param replyToRolePrivate the privacy level for role conversation replies
   * @return the saved RECEIVE {@link MailSendRecv} entity
   */
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

  /**
   * Resolves role names to role IDs for database storage.
   * 
   * <p>
   * Converts human-readable role names to the corresponding role IDs required for database relationships and role-based
   * message filtering.
   * 
   * @param mailSendRecv the message to update with role ID information
   */
  private void setIdRoleToFromRoleName(MailSendRecv mailSendRecv) {
    if (mailSendRecv.getRoleNameTo() != null) {
      Role role = roleJpaRepository.findByRolename(mailSendRecv.getRoleNameTo());
      mailSendRecv.setIdRoleTo(role.getIdRole());
    }
  }

  /**
   * Sends messages via both internal and external channels based on user preferences.
   * 
   * <p>
   * This is the core message delivery method that respects user forwarding preferences, handles message redirection,
   * and manages both internal and external delivery channels. Subject lines are internationalized based on recipient
   * locale.
   * 
   * @param idUserFrom          the ID of the sender
   * @param idUserTo            the ID of the recipient
   * @param subjectOrSubjectKey the subject line or message key for internationalization
   * @param subjectValues       parameters for subject line template (if using message key)
   * @param isSubjectKey        whether the subject parameter is a message key or literal text
   * @param message             the message content
   * @param messageComType      the type of communication for forwarding rules
   * @param includeInternalMail whether to create internal message records
   * @return the ID of the created internal message, or null if not delivered internally
   * @throws MessagingException if external email delivery fails
   */
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

  /**
   * Handles external email delivery based on message target type.
   * 
   * <p>
   * Sends external emails when the message target type includes external delivery. Adds framework-specific message
   * headers and formatting to distinguish external messages from direct emails.
   * 
   * @param msgTargetType the target delivery method (internal, external, or both)
   * @param idUserFrom    the ID of the sender (for message attribution)
   * @param subject       the email subject line
   * @param message       the email message content
   * @param usersTo       array of recipient email addresses
   * @param localeStr     the locale for message formatting
   * @throws MessagingException if email delivery fails
   */
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

  /**
   * Generates internationalized subject lines for messages.
   * 
   * <p>
   * Creates subject lines either from literal text or by resolving message keys with parameters based on the
   * recipient's locale preferences.
   * 
   * @param userTo              the recipient user (for locale determination)
   * @param subjectOrSubjectKey the subject text or message key
   * @param subjectValues       parameters for message key resolution
   * @param isSubjectKey        whether to treat the subject parameter as a message key
   * @return the formatted subject line in the recipient's locale
   */
  private String getSubject(User userTo, String subjectOrSubjectKey, Object[] subjectValues, boolean isSubjectKey) {
    if (isSubjectKey) {
      return messagesSource.getMessage(subjectOrSubjectKey, subjectValues,
          Locale.forLanguageTag(userTo.getLocaleStr()));
    } else {
      return subjectOrSubjectKey;
    }
  }

}
