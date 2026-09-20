package grafioschtrader.dto;

import java.time.LocalDateTime;

/**
 * One instrument a tenant holds at a reference date, collapsed over all of its security accounts, together with the
 * intraday snapshot of that instrument.
 *
 * <p>
 * The units are already split adjusted: {@code holdings * split_price_factor} is the factor by which a price becomes a
 * position value, which is the same product the period-performance queries use. Margin positions are not reported,
 * because their value does not follow that product.
 * </p>
 */
public interface IHeldInstrumentIntraday {

  Integer getIdSecuritycurrency();

  String getName();

  /** Currency of the instrument, needed to tell an already main-currency position from one that needs a rate. */
  String getCurrency();

  /** Exchange the instrument trades on, which decides whether the intraday snapshot belongs to a session at all. */
  Integer getIdStockexchange();

  /** Split adjusted units summed over every security account of the tenant; may be negative for a short position. */
  double getUnits();

  /**
   * Currency pair leading from the instrument currency to the tenant currency, denormalised on the holding row. Null
   * when the instrument is already denominated in the tenant currency.
   */
  Integer getIdCurrencypairTenant();

  /** Last intraday price seen by a connector, or null when none was ever delivered. */
  Double getSLast();

  /**
   * Change against the previous close in percent, as delivered by the connector, and the only trustworthy account of an
   * intraday move.
   *
   * <p>
   * {@code s_prev_close} is deliberately absent from this projection and must not be added back. Roughly a third of the
   * connectors - Yahoo, comdirect, boursorama, finanzen.net, investing among them - never write that column, and
   * nothing clears a field a connector omits, so it keeps an arbitrarily old value while the last price and this
   * percentage are refreshed on every tick. Deriving the previous price from this figure is the only way to get a pair
   * that agrees with itself, and with what the watchlist shows.
   * </p>
   */
  Double getSChangePercentage();

  /** When the intraday snapshot was taken. A timestamp older than the current session makes the row stale. */
  LocalDateTime getSTimestamp();
}
