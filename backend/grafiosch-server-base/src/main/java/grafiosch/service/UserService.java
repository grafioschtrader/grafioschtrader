package grafiosch.service;

import java.util.Optional;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import grafiosch.dto.ChangePasswordDTO;
import grafiosch.dto.UserDTO;
import grafiosch.entities.User;
import grafiosch.entities.projection.SuccessfullyChanged;
import grafiosch.entities.projection.UserOwnProjection;
import grafiosch.exceptions.RequestLimitAndSecurityBreachException;
import grafiosch.types.UserRightLimitCounter;

/**
 * Service interface for comprehensive user management and authentication operations.
 * 
 * <p>This interface extends Spring Security's UserDetailsService to provide a complete
 * user management solution that handles authentication, authorization, profile management,
 * security monitoring, and user lifecycle operations. It serves as the central service
 * for all user-related functionality in the application.</p>
 * 
 * <h3>Core Responsibilities:</h3>
 * <ul>
 *   <li><strong>Authentication Integration:</strong> Seamless integration with Spring Security
 *       for user authentication and authorization</li>
 *   <li><strong>User Lifecycle Management:</strong> Complete user creation, verification,
 *       and profile management capabilities</li>
 *   <li><strong>Security Monitoring:</strong> Tracking and enforcement of security limits,
 *       violations, and protective measures</li>
 *   <li><strong>Profile Management:</strong> User preference updates, password changes,
 *       and personalization features</li>
 * </ul>
 * 
 * <h3>Security Features:</h3>
 * <ul>
 *   <li><strong>Limit Enforcement:</strong> Monitors and enforces user-specific security
 *       and usage limits to prevent abuse</li>
 *   <li><strong>Password Validation:</strong> Comprehensive password policy enforcement
 *       with configurable complexity requirements</li>
 *   <li><strong>Account Verification:</strong> Email-based account verification workflow
 *       for secure user registration</li>
 *   <li><strong>Violation Tracking:</strong> Records and tracks security violations for
 *       monitoring and protective actions</li>
 * </ul>
 * 
 * <h3>Integration Points:</h3>
 * <ul>
 *   <li><strong>Spring Security:</strong> Provides UserDetailsService implementation for
 *       authentication framework integration</li>
 *   <li><strong>JWT Authentication:</strong> Supports token-based authentication with
 *       user verification and validation</li>
 *   <li><strong>REST Controllers:</strong> Services REST API endpoints for user operations
 *       and profile management</li>
 *   <li><strong>Security Filters:</strong> Integrates with authentication and authorization
 *       filters for request processing</li>
 * </ul>
 * 
 * <h3>Data Management:</h3>
 * <p>The service manages user data with support for internationalization, timezone handling,
 * and user preferences while maintaining data integrity and security throughout all operations.</p>
 */
public interface UserService extends UserDetailsService {

  Optional<User> findUser(Integer id);
  
  /**
   * Updates user profile information excluding password changes.
   * 
   * <p>This method handles profile updates for the currently authenticated user,
   * allowing modification of email, nickname, and other profile attributes while
   * specifically excluding password changes which require separate security handling.</p>
   * 
   * <p><strong>Security Context:</strong></p>
   * <p>The method operates on the currently authenticated user from the Spring Security
   * context, ensuring that users can only modify their own profile information.</p>
   * 
   * <p><strong>Supported Updates:</strong></p>
   * <ul>
   *   <li>Email address changes (with appropriate validation)</li>
   *   <li>Nickname modifications</li>
   *   <li>Profile preferences and settings</li>
   *   <li>Locale and internationalization preferences</li>
   * </ul>
   * 
   * @param params UserDTO containing the profile updates to apply
   * @return updated User entity with the applied changes
   */
 // User updateButPassword(UserDTO params);

 

  /**
   * Validates that a user has not exceeded configured security and usage limits.
   * 
   * <p>This method enforces application-wide security policies by checking user-specific
   * limits for various violation types. If limits are exceeded, appropriate exceptions
   * are thrown to prevent further access and trigger security responses.</p>
   * 
   * @param user the User entity to validate against configured limits
   * @throws RequestLimitAndSecurityBreachException if any limits are exceeded
   */
  void checkUserLimits(User user) throws RequestLimitAndSecurityBreachException;

  /**
   * Creates a new user account with basic profile information.
   * 
   * <p>This method handles the core user creation process including password encryption,
   * role assignment, and initial account setup. It is typically used as part of the
   * registration workflow after validation and verification processes.</p>
   * 
   * @param userDTO data transfer object containing user creation information
   * @return newly created User entity with generated identifiers and encrypted credentials
   */
  User createUser(UserDTO userDTO);

  /**
   * Updates a user's timezone offset preference.
   * 
   * <p>This method handles timezone preference updates to ensure that user interfaces
   * and data display are properly localized to the user's current timezone. The
   * timezone offset is typically updated during login when client timezone information
   * is available.</p>
   * 
   * @param user the User entity to update
   * @param timezoneOffset timezone offset in minutes from UTC
   * @return updated User entity with the new timezone preference
   */
  User updateTimezoneOffset(User user, Integer timezoneOffset);

  /**
   * Creates a new user account with email verification workflow.
   * 
   * <p>This method implements the complete user registration process including account
   * creation, verification email generation, and email delivery. It handles all aspects
   * of secure user onboarding with email-based account verification.</p>
   * 
   * <p><strong>Registration Workflow:</strong></p>
   * <ol>
   *   <li>Validates user data against application limits and constraints</li>
   *   <li>Checks for duplicate email addresses and usernames</li>
   *   <li>Creates user account with encrypted credentials</li>
   *   <li>Generates unique verification token</li>
   *   <li>Sends verification email with activation link</li>
   * </ol>
   * 
   * <p><strong>Security Features:</strong></p>
   * <ul>
   *   <li>User limit validation to prevent system abuse</li>
   *   <li>Duplicate detection for email addresses and nicknames</li>
   *   <li>Password policy enforcement</li>
   *   <li>Secure token generation for email verification</li>
   * </ul>
   * 
   * @param userDTO user registration data containing credentials and profile information
   * @param hostName hostname and base path for verification link generation
   * @return newly created User entity with verification status
   * @throws Exception if registration validation fails or email delivery encounters errors
   */
  User createUserForVerification(UserDTO userDTO, String hostName) throws Exception;

  /**
   * Changes a user's password with security validation and demo account protection.
   * 
   * <p>This method implements secure password change functionality with comprehensive
   * validation including old password verification, new password policy compliance,
   * and demo account protection. It ensures that password changes maintain security
   * standards while preventing unauthorized modifications.</p>
   * 
   * <p><strong>Security Validation:</strong></p>
   * <ol>
   *   <li>Verifies the current password matches the stored encrypted password</li>
   *   <li>Validates new password against configured complexity requirements</li>
   *   <li>Checks for demo account restrictions to prevent demo account modifications</li>
   *   <li>Encrypts and stores the new password securely</li>
   * </ol>
   * 
   * <p><strong>Demo Account Protection:</strong></p>
   * <p>The method includes protection against password changes on demonstration accounts
   * to maintain demo environment integrity and prevent security exposure.</p>
   * 
   * @param changePasswordDTO data transfer object containing old and new password information
   * @return SuccessfullyChanged object with success status and localized confirmation message
   * @throws Exception if password validation fails, old password is incorrect, or demo account restrictions apply
   */
  SuccessfullyChanged changePassword(ChangePasswordDTO changePasswortDTO) throws Exception;

  /**
   * Validates a password against configured security policies and requirements.
   * 
   * <p>This method checks whether a provided password meets the application's security
   * requirements including complexity rules, length requirements, and pattern matching.
   * It supports configurable password policies that can be adjusted based on security needs.</p>
   * 
   * @param password the password string to validate against security policies
   * @return true if the password meets all requirements, false if it fails validation
   * @throws Exception if password validation processing encounters errors
   */
  boolean isPasswordAccepted(String password) throws Exception;

  /**
   * Increments violation counters for security monitoring and limit enforcement.
   * 
   * <p>This method tracks various types of user violations and security breaches to
   * maintain application security and enable automated protective responses. Different
   * violation types are tracked separately to provide granular security monitoring.</p>
   * 
   * <p><strong>Tracked Violation Types:</strong></p>
   * <ul>
   *   <li><strong>Security Breaches:</strong> Authentication failures, suspicious activities, policy violations</li>
   *   <li><strong>Request Limit Violations:</strong> API rate limit exceeding, resource abuse</li>
   * </ul>
   * 
   * @param userId unique identifier of the user for whom to increment violation counts
   * @param userRightLimitCounter enumeration specifying the type of violation to record
   * @return updated User entity with incremented violation counters
   */
  User incrementRightsLimitCount(Integer userId, UserRightLimitCounter userRightLimitCounter);

  /**
   * Loads user details by ID with additional username verification for JWT token validation.
   * 
   * <p>This method provides enhanced user loading functionality specifically designed for
   * JWT token validation workflows. It combines user ID lookup with username verification
   * to ensure token integrity and prevent token manipulation attacks.</p>
   * 
   * @param idUser the user ID extracted from the JWT token
   * @param username the username extracted from the JWT token for verification
   * @return UserDetails object for the validated user
   * @throws UsernameNotFoundException if user is not found or username verification fails
   */  
  UserDetails loadUserByUserIdAndCheckUsername(Integer idUser, String username);

  /**
   * Updates user locale preferences and nickname with comprehensive profile management.
   * 
   * <p>This method handles localized profile updates for the currently authenticated user,
   * including nickname changes, locale preferences, and UI customization settings. It
   * ensures that user preferences are properly validated and persisted for consistent
   * application behavior.</p>
   * 
   * @param userOwnProjection projection containing the profile updates to apply
   * @return SuccessfullyChanged object with success status and localized confirmation message
   */
  SuccessfullyChanged updateNicknameLocal(UserOwnProjection userOwnProjection);
}
