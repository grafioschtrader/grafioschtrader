import {ConfigurationWithLogin} from '../model/configuration-with-login';

/**
 * Abstract handler for application-specific post-login actions.
 * Implementations can be provided by the consuming application to execute
 * custom logic after successful authentication.
 */
export abstract class AfterLoginHandler {
  /**
   * Called after successful login and session initialization.
   * @param configurationWithLogin The login configuration from the backend
   */
  abstract handleAfterLogin(configurationWithLogin: ConfigurationWithLogin): void;
}
