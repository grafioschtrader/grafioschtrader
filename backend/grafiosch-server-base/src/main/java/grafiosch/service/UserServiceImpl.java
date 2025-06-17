package grafiosch.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import grafiosch.BaseConstants;
import grafiosch.dto.ChangePasswordDTO;
import grafiosch.dto.PasswordRegexProperties;
import grafiosch.dto.UserDTO;
import grafiosch.entities.Role;
import grafiosch.entities.TaskDataChange;
import grafiosch.entities.User;
import grafiosch.entities.projection.SuccessfullyChanged;
import grafiosch.entities.projection.UserOwnProjection;
import grafiosch.exceptions.DataViolationException;
import grafiosch.exceptions.RequestLimitAndSecurityBreachException;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.RoleJpaRepository;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.repository.VerificationTokenJpaRepository;
import grafiosch.rest.helper.RestHelper;
import grafiosch.types.TaskDataExecPriority;
import grafiosch.types.TaskTypeBase;
import grafiosch.types.UserRightLimitCounter;
import jakarta.mail.MessagingException;

/**
 * Comprehensive implementation of user management and authentication services.
 * 
 * <p>This service class provides a complete implementation of the UserService interface,
 * handling all aspects of user lifecycle management from registration through authentication
 * to profile maintenance. It integrates Spring Security authentication with custom business
 * logic for user limits, security monitoring, and administrative features.</p>
 * 
 * <h3>Administrative Features:</h3>
 * <ul>
 *   <li><strong>Main Admin Detection:</strong> Automatic identification and role assignment
 *       for the primary administrator account</li>
 *   <li><strong>User Limit Enforcement:</strong> Configurable limits on total active users
 *       to manage system resources and licensing</li>
 *   <li><strong>Data Migration Support:</strong> Automated task scheduling for admin account
 *       data consolidation and ownership transfer</li>
 * </ul>
 * 
 * <h3>Security Architecture:</h3>
 * <p>Implements a comprehensive security model with password encryption using BCrypt,
 * configurable password policies, demo account protection, and detailed violation tracking
 * for monitoring and automated security responses.</p>
 * 
 * <h3>Internationalization Support:</h3>
 * <p>Full support for multi-language environments with locale-aware messaging, timezone
 * handling, and user preference management for consistent international user experiences.</p>
 */
@Service
public class UserServiceImpl implements UserService {

  private final MessageSource messages;

  @Value("${gt.main.user.admin.mail}")
  private String mainUserAdminMail;

  @Value("${gt.allowed.users}")
  private int allowedUsers;

  @Value("${gt.demo.account.pattern.de}")
  private String demoAccountPatternDE;

  @Value("${gt.demo.account.pattern.en}")
  private String demoAccountPatternEN;

  @Autowired
  private VerificationTokenJpaRepository verificationTokenJpaRepository;

  @Autowired
  private MailExternalService mailExternalService;

  private final UserJpaRepository userJpaRepository;

  private final RoleJpaRepository roleJpaRepository;

  private final GlobalparametersJpaRepository globalparametersJpaRepository;

  private final TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  public UserServiceImpl(final UserJpaRepository userJpaRepository, final RoleJpaRepository roleJpaRepository,
      final MessageSource messages, GlobalparametersJpaRepository globalparametersJpaRepository,
      final TaskDataChangeJpaRepository taskDataChangeJpaRepository) {
    this.userJpaRepository = userJpaRepository;
    this.roleJpaRepository = roleJpaRepository;
    this.messages = messages;
    this.globalparametersJpaRepository = globalparametersJpaRepository;
    this.taskDataChangeJpaRepository = taskDataChangeJpaRepository;
  }

  /**
   * It is called with every request!
   */
  @Override
  public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
    final User user = userJpaRepository.findByEmail(username).orElse(null);
    if (user != null) {
      new AccountStatusUserDetailsChecker().check(user);
    } else {
      throw new UsernameNotFoundException("user not found.");
    }
    return user;
  }

  @Override
  public UserDetails loadUserByUserIdAndCheckUsername(final Integer idUser, final String username) {
    final User user = userJpaRepository.findById(idUser).orElse(null);
    if (user != null && user.getUsername().equals(username)) {
      new AccountStatusUserDetailsChecker().check(user);
      checkUserLimits(user);
    } else {
      throw new UsernameNotFoundException("user not found.");
    }
    return user;
  }

  @Override
  public void checkUserLimits(User user) throws RequestLimitAndSecurityBreachException {
    if (user.getLimitRequestExceedCount() > globalparametersJpaRepository.getMaxLimitExceededCount()) {
      throw new RequestLimitAndSecurityBreachException(
          messages.getMessage("limit.request.exceeded", null, user.createAndGetJavaLocale()),
          User.LIMIT_REQUEST_EXCEED_COUNT);
    }
    if (user.getSecurityBreachCount() > globalparametersJpaRepository.getMaxSecurityBreachCount()) {
      throw new RequestLimitAndSecurityBreachException(
          messages.getMessage("limit.security.breach.exceeded", null, user.createAndGetJavaLocale()),
          User.SECURITY_BREACH_COUNT);
    }
  }

//  @Override
//  public User updateButPassword(final UserDTO params) {
//    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
//    params.getEmail().ifPresent(user::setUsername);
//    params.getEncodedPassword().ifPresent(user::setPassword);
//    params.getNickname().ifPresent(user::setNickname);
//    return userJpaRepository.save(user);
//  }

  @Override
  public Optional<User> findUser(final Integer id) {
    return Optional.of(userJpaRepository.getReferenceById(id));
  }

  @Override
  public User createUserForVerification(final UserDTO userDTO, final String hostNameAndBaseName) throws Exception {
    checkApplExeedsUserLimit(userDTO.getLocaleStr());
    checkPasswordAgainstRegex(userDTO.getPassword());
    // Unique
    Optional<User> user = userJpaRepository.findByEmail(userDTO.getEmail().get());
    if (user.isPresent()) {
      throw new DataViolationException("email", "email.already.used", userDTO.getEmail().get(), userDTO.getLocaleStr());
    }
    user = userJpaRepository.findByNickname(userDTO.getNickname().get());
    if (user.isPresent()) {
      throw new DataViolationException("nickname", "nickname.already.used", userDTO.getNickname().get(),
          userDTO.getLocaleStr());
    }
    final User newUser = createUser(userDTO);
    confirmRegistration(newUser, hostNameAndBaseName);
    return newUser;
  }

  /**
   * Initiates the email verification process for newly registered users.
   * 
   * <p>This private method handles the complete email verification workflow including
   * token generation, email composition, and delivery. It creates a secure verification
   * link that users must access to activate their accounts, ensuring email address
   * ownership and reducing fraudulent registrations.</p>
   * 
   * <p><strong>Verification Process:</strong></p>
   * <ol>
   *   <li>Generates a unique UUID-based verification token</li>
   *   <li>Stores the token in the database linked to the user account</li>
   *   <li>Composes localized email subject and message content</li>
   *   <li>Constructs verification URL with the generated token</li>
   *   <li>Sends the verification email to the user's registered address</li>
   * </ol>
   * 
   * <p><strong>Security Features:</strong></p>
   * <ul>
   *   <li><strong>Unique Token Generation:</strong> Uses UUID for cryptographically secure,
   *       unique verification tokens that cannot be predicted or forged</li>
   *   <li><strong>Database Token Storage:</strong> Links tokens to specific user accounts
   *       with appropriate expiration and usage tracking</li>
   *   <li><strong>Secure URL Construction:</strong> Builds verification URLs with the
   *       provided hostname and base path for proper domain verification</li>
   * </ul>
   * 
   * <p><strong>Internationalization:</strong></p>
   * <p>Email content is fully localized based on the user's locale preference, including
   * both the subject line and message body. This ensures that verification emails are
   * sent in the user's preferred language for better user experience.</p>
   * 
   * @param user the User entity for whom to send the verification email
   * @param hostNameAndBaseNam the hostname and base path for constructing the verification URL
   * @throws MessagingException if email composition or delivery fails
   */
  private void confirmRegistration(User user, final String hostNameAndBaseNam) throws MessagingException {
    final String token = UUID.randomUUID().toString();
    verificationTokenJpaRepository.createVerificationTokenForUser(user, token);

    final String subject = messages.getMessage("registraion.success.subject", null, user.createAndGetJavaLocale());
    final String confirmationUrl = hostNameAndBaseNam + "/tokenverify?token=" + token;
    final String message = messages.getMessage("registraion.success.text", null, user.createAndGetJavaLocale());
    mailExternalService.sendSimpleMessage(user.getUsername(), subject,
        message + BaseConstants.RETURN_AND_NEW_LINE + confirmationUrl);
  }

  /**
   * Validates that the application has not exceeded the configured user limit.
   * 
   * <p>This private method enforces application-wide user limits to manage system resources,
   * licensing constraints, and operational capacity. It prevents new user registrations
   * when the maximum allowed number of active users has been reached, ensuring system
   * stability and compliance with usage agreements.</p>
   * 
   * @param localeStr the locale string for localizing the error message if limit is exceeded
   * @throws DataViolationException if the number of enabled users meets or exceeds the configured limit
   */
  private void checkApplExeedsUserLimit(String localeStr) {
    if (userJpaRepository.countByEnabled(true) >= allowedUsers) {
      throw new DataViolationException("applimit", "appl.exeeds.user.limit", allowedUsers, localeStr);
    }
  }

  @Override
  public User createUser(final UserDTO userDTO) {
    List<Role> roles = new ArrayList<>();
    boolean isMainUserAdmin = userDTO.getEmail().get().equals(mainUserAdminMail);
    if (isMainUserAdmin) {
      roles.add(roleJpaRepository.findByRolename(Role.ROLE_ADMIN));
      roles.add(roleJpaRepository.findByRolename(Role.ROLE_ALL_EDIT));
      roles.add(roleJpaRepository.findByRolename(Role.ROLE_USER));
    } else {
      roles.add(roleJpaRepository.findByRolename(Role.ROLE_LIMIT_EDIT));
    }
    User user = userJpaRepository.save(userDTO.toUser(roles));
    if (isMainUserAdmin) {
      // It is not possible to give this user the id 1 when @GeneratedValue(strategy =
      // GenerationType.IDENTITY) is used
      // But we move all existing entities to this user
      TaskDataChange tdc = new TaskDataChange(TaskTypeBase.MOVE_CREATED_BY_USER_TO_OTHER_USER,
          TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now().plusMinutes(5), user.getIdUser(), User.TABNAME);
      tdc.setOldValueNumber(1.0);
      taskDataChangeJpaRepository.save(tdc);
    }
    return user;
  }

  @Override
  public User updateTimezoneOffset(final User user, final Integer timezoneOffset) {
    user.setTimezoneOffset(timezoneOffset);
    return userJpaRepository.save(user);
  }

  @Override
  public SuccessfullyChanged updateNicknameLocal(UserOwnProjection userOwnProjection) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    user.setNickname(userOwnProjection.nickname);
    user.checkAndSetLocaleStr(userOwnProjection.localeStr);
    user.setUiShowMyProperty(userOwnProjection.uiShowMyProperty);
    userJpaRepository.save(user);
    return new SuccessfullyChanged(true,
        messages.getMessage("locale.nickname.success", null, user.createAndGetJavaLocale()));
  }

  @Override
  public SuccessfullyChanged changePassword(final ChangePasswordDTO changePasswordDTO) throws Exception {
    final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    RestHelper.isDemoAccount(demoAccountPatternDE, user.getUsername());
    RestHelper.isDemoAccount(demoAccountPatternEN, user.getUsername());

    if (bCryptPasswordEncoder.matches(changePasswordDTO.passwordOld, user.getPassword())) {
      checkPasswordAgainstRegex(changePasswordDTO.passwordNew);
      user.setPassword(bCryptPasswordEncoder.encode(changePasswordDTO.passwordNew));
      userJpaRepository.save(user);
      return new SuccessfullyChanged(true,
          messages.getMessage("password.changed.success", null, user.createAndGetJavaLocale()));
    }
    throw new DataViolationException("oldpassword", "password.changed.old.wrong", null);
  }

  /**
   * Validates a password against the configured security regex pattern.
   * 
   * @param password the password string to validate against the configured regex pattern
   * @throws SecurityException if the password does not match the required regex pattern
   * @throws Exception if password policy retrieval or validation processing fails
   */
  private void checkPasswordAgainstRegex(String password) throws Exception {
    String regex = globalparametersJpaRepository.getPasswordRegexProperties().regex;
    if (!password.matches(regex)) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
  }

  @Override
  public boolean isPasswordAccepted(String password) throws Exception {
    PasswordRegexProperties prp = globalparametersJpaRepository.getPasswordRegexProperties();
    return !prp.forceRegex || password.matches(prp.regex);
  }

  @Override
  public User incrementRightsLimitCount(Integer userId, UserRightLimitCounter userRightLimitCounter) {
    User user = userJpaRepository.findById(userId).orElse(null);
    switch (userRightLimitCounter) {
    case SECURITY_BREACH:
      user.setSecurityBreachCount((short) (user.getSecurityBreachCount() + 1));
      break;
    case LIMIT_EXCEEDED_TENANT_DATA:
      user.setLimitRequestExceedCount((short) (user.getLimitRequestExceedCount() + 1));
      break;
    }
    return userJpaRepository.save(user);
  }

}
