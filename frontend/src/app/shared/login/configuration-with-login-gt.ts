import {ConfigurationWithLogin} from '../../lib/login/model/configuration-with-login';

/**
 * Extended configuration interface for GrafioschTrader-specific login settings.
 *
 * <p>This interface extends the base ConfigurationWithLogin to include trading application-specific
 * configuration data such as cryptocurrency support and currency precision settings.</p>
 */
export interface ConfigurationWithLoginGT extends ConfigurationWithLogin {
  /**
   * List of supported cryptocurrencies for trading operations.
   *
   * <p>Contains the cryptocurrencies that the application supports for trading,
   * portfolio management, and price tracking. This list is used by the frontend
   * to validate and display available cryptocurrency options.</p>
   */
  cryptocurrencies: string[];

  /**
   * Currency-specific decimal precision configuration.
   * Certain currencies have a deviation different from two decimal places.
   * This mapping provides the correct precision for each currency.
   */
  currencyPrecision: { [currency: string]: number };
}
