import {ConfigurationWithLogin} from '../../lib/login/model/configuration-with-login';

/**
 * Extended configuration interface for GrafioschTrader-specific login settings.
 *
 * <p>This interface extends the base ConfigurationWithLogin to include trading application-specific
 * configuration data such as cryptocurrency support, currency precision settings, and tenant-level
 * period locking configuration.</p>
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

  /**
   * Tenant-level closed-until date for transaction period locking (ISO date string).
   *
   * <p>Transactions on or before this date are protected from modification at the tenant level.
   * Individual portfolios can override this with their own closedUntil value, which takes priority.
   * If both portfolio and tenant closedUntil are null, there is no date restriction.</p>
   */
  tenantClosedUntil: string | null;
}
