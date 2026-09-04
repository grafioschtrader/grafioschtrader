package grafioschtrader;

import java.time.LocalDate;
import java.time.LocalDateTime;

import grafiosch.GlobalParamKeyBaseDefault;

/**
 * GrafioschTrader-specific global parameter keys and default values extending base configuration.
 *
 * <p>
 * This class defines trading platform-specific configuration parameters including connector settings, data feed
 * configurations, market data processing parameters, tenant limits, and trading-related constraints. It extends the
 * base global parameters with domain-specific settings for financial data management, price updates, and trading
 * platform operations.
 * </p>
 */
public class GlobalParamKeyDefault extends GlobalParamKeyBaseDefault {

  /** Default currency precision configuration. */
  public static final String DEFAULT_CURRENCY_PRECISION = "BTC=8,ETH=7,JPY=0,ZAR=0";

  /** Default number of retry attempts for intraday price updates. */
  public static final short DEFAULT_INTRA_RETRY = 4;

  /** Default number of retry attempts for historical price updates. */
  public static final short DEFAULT_HISTORY_RETRY = 4;

  /**
   * Default number of additional GTNet retry attempts allowed once the connector retry counter has reached its cap
   * (gt.history.retry / gt.intra.retry). Acts as the GTNet-only fallback budget; a single value applies to both
   * historical and intraday flows.
   */
  public static final short DEFAULT_GTNET_QUOTE_RETRY = 8;

  /**
   * Default weight of the OHL richness preference when ranking GTNet historical price suppliers, expressed in percent.
   * 50 means a peer reporting 100% complete open/high/low data scores 1.5 times a peer reporting none of it, while
   * instrument coverage and reliability remain the dominant factors. 0 disables the preference.
   */
  public static final int DEFAULT_GTNET_OHL_WEIGHT = 50;

  /** Default number of retry attempts for dividend data updates. */
  public static final short DEFAULT_DIVIDEND_RETRY = 2;

  /** Default number of retry attempts for stock split data updates. */
  public static final short DEFAULT_SPLIT_RETRY = 2;

  /** Default timeout in seconds for security intraday update operations. */
  public static final int DEFAULT_SC_INTRA_UPDATE_TIMEOUT_SECONDS = 300;
  public static final int DEFAULT_W_INTRA_UPDATE_TIMEOUT_SECONDS = 1200;
  /** Default additional delay in seconds for GTNet lastprice freshness calculation. */
  public static final int DEFAULT_GTNET_LASTPRICE_DELAY_SECONDS = 300;
  public static final int DEFUALT_MAX_WATCHLIST = 30;
  public static final LocalDate DEFAULT_START_FEED_DATE = LocalDate.of(2000, 1, 1);
  public static final int DEFAULT_INTRADAY_OBSERVATION_OR_DAYS_BACK = 60;
  public static final int DEFAULT_INTRADAY_OBSERVATION_RETRY_MINUS = 0;
  public static final int DEFAULT_INTRADAY_OBSERVATION_FALLING_PERCENTAGE = 80;
  public static final int DEFAULT_HISTORY_OBSERVATION_DAYS_BACK = 60;
  public static final int DEFAULT_HISTORY_OBSERVATION_RETRY_MINUS = 0;
  public static final int DEFAULT_HISTORY_OBSERVATION_FALLING_PERCENTAGE = 80;
  public static final int DEFAULT_HISTORY_MAX_FILLDAYS_CURRENCY = 5;
  public static final int DEFAULT_UPDATE_PRICE_BY_EXCHANGE = 0;

  /** Default mode for connector / asset class compatibility enforcement (0=off, 1=server only, 2=server + UI). */
  public static final int DEFAULT_FORCE_CONNECTOR_MATCH = 0;

  /** Default maximum number of split entries a user may record per instrument. */
  public static final int DEFAULT_MAX_INSTRUMENT_SPLITS = 20;
  /** Default maximum number of history-quote periods a user may record per instrument. */
  public static final int DEFAULT_MAX_INSTRUMENT_HISTORYQUOTE_PERIODS = 20;

  /**
   * Default bounds, written as {@code min:max}, for the per-standing-order quote tolerance. The tolerance says how far
   * the price or exchange rate may deviate from the execution date; the default leaves both directions open, while
   * {@code 0:3} would forbid reaching into the past and {@code 0:0} would enforce exact dates.
   */
  public static final String DEFAULT_STANDING_ORDER_QUOTE_TOLERANCE = "-3:3";

  public static final String GLOB_KEY_CURRENCY_PRECISION = GlobalConstants.GT_PREFIX + "currency.precision";
  public static final String GLOB_KEY_STANDING_ORDER_QUOTE_TOLERANCE = GlobalConstants.GT_PREFIX
      + "standing.order.quote.tolerance";
  /** Connector settings */
  public static final String GLOB_KEY_CRYPTOCURRENCY_HISTORY_CONNECTOR = GlobalConstants.GT_PREFIX
      + "cryptocurrency.history.connector";
  public static final String GLOB_KEY_CRYPTOCURRENCY_INTRA_CONNECTOR = GlobalConstants.GT_PREFIX
      + "cryptocurrency.intra.connector";
  public static final String GLOB_KEY_CURRENCY_HISTORY_CONNECTOR = GlobalConstants.GT_PREFIX
      + "currency.history.connector";
  public static final String GLOB_KEY_CURRENCY_INTRA_CONNECTOR = GlobalConstants.GT_PREFIX + "currency.intra.connector";
  public static final String GLOB_KEY_INTRA_RETRY = GlobalConstants.GT_PREFIX + "intra.retry";
  public static final String GLOB_KEY_HISTORY_RETRY = GlobalConstants.GT_PREFIX + "history.retry";
  /**
   * Number of additional retry attempts allowed via GTNet after the connector retry cap is reached. Counter resumes
   * climbing past gt.history.retry / gt.intra.retry only via GTNet failures.
   */
  public static final String GLOB_KEY_GTNET_QUOTE_RETRY = GlobalConstants.GT_PREFIX + "gtnet.quote.retry";
  public static final String GLOB_KEY_DIVIDEND_RETRY = GlobalConstants.GT_PREFIX + "dividend.retry";
  public static final String GLOB_KEY_SPLIT_RETRY = GlobalConstants.GT_PREFIX + "split.retry";
  public static final String GLOB_KEY_START_FEED_DATE = GlobalConstants.GT_PREFIX + "core.data.feed.start.date";
  public static final String GLOB_KEY_SC_INTRA_UPDATE_TIMEOUT_SECONDS = GlobalConstants.GT_PREFIX
      + "sc.intra.update.timeout.seconds";
  public static final String GLOB_KEY_W_INTRA_UPDATE_TIMEOUT_SECONDS = GlobalConstants.GT_PREFIX
      + "w.intra.update.timeout.seconds";
  /** Additional delay in seconds for GTNet lastprice freshness threshold calculation. */
  public static final String GLOB_KEY_GTNET_LASTPRICE_DELAY_SECONDS = GlobalConstants.GT_PREFIX
      + "gtnet.lastprice.delay.seconds";
  /**
   * Weight in percent of the OHL richness preference used when ranking GTNet historical price suppliers. Applies to
   * securities only; currency pairs never report an OHL percentage and are therefore ranked without this factor.
   */
  public static final String GLOB_KEY_GTNET_OHL_WEIGHT = GlobalConstants.GT_PREFIX + "gtnet.ohl.weight";
  public static final String GLOB_KEY_HISTORY_MAX_FILLDAYS_CURRENCY = GlobalConstants.GT_PREFIX
      + "history.max.filldays.currency";
  public static final String GLOB_KEY_INTRADAY_OBSERVATION_OR_DAYS_BACK = GlobalConstants.GT_PREFIX
      + "intraday.observation.or.days.back";
  public static final String GLOB_KEY_INTRADAY_OBSERVATION_RETRY_MINUS = GlobalConstants.GT_PREFIX
      + "intraday.observation.retry.minus";
  public static final String GLOB_KEY_INTRADAY_OBSERVATION_FALLING_PERCENTAGE = GlobalConstants.GT_PREFIX
      + "intraday.observation.falling.percentage";
  public static final String GLOB_KEY_HISTORY_OBSERVATION_DAYS_BACK = GlobalConstants.GT_PREFIX
      + "history.observation.days.back";
  public static final String GLOB_KEY_HISTORY_OBSERVATION_RETRY_MINUS = GlobalConstants.GT_PREFIX
      + "history.observation.retry.minus";
  public static final String GLOB_KEY_HISTORY_OBSERVATION_FALLING_PERCENTAGE = GlobalConstants.GT_PREFIX
      + "history.observation.falling.percentage";

  /** History quote quality. Date which last time when a history quality update was happened */
  public static final String GLOB_KEY_HISTORYQUOTE_QUALITY_UPDATE_DATE = GlobalConstants.GT_PREFIX
      + "historyquote.quality.update.date";
  public static final String GLOB_KEY_YOUNGEST_SPLIT_APPEND_DATE = GlobalConstants.GT_PREFIX
      + "securitysplit.append.date";
  public static final String GLOB_KEY_YOUNGEST_DIVIDEND_APPEND_DATE = GlobalConstants.GT_PREFIX
      + "securitydividend.append.date";
  public static final String GLOB_KEY_UDF_GENERAL_RECREATE = GlobalConstants.GT_PREFIX + "udf.general.recreate";

  /** Timestamp of last GTNet exchange synchronization with peers. */
  public static final String GLOB_KEY_GTNET_EXCHANGE_SYNC_TIMESTAMP = GlobalConstants.GT_PREFIX
      + "gtnet.exchange.sync.timestamp";
  /** Default value for GTNet exchange sync timestamp - epoch start means never synced. */
  public static final LocalDateTime DEFAULT_GTNET_EXCHANGE_SYNC_TIMESTAMP = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
  public static final String GLOB_KEY_UPDATE_PRICE_BY_EXCHANGE = GlobalConstants.GT_PREFIX + "update.price.by.exchange";
  /**
   * Three-state switch for connector ↔ asset class compatibility checking, evaluated against only, 2 enforces
   * server-side AND tells the frontend dropdown to hide incompatible connectors.
   */
  public static final String GLOB_KEY_FORCE_CONNECTOR_MATCH = GlobalConstants.GT_PREFIX + "force.connector.match";

  /**
   * Id of the import platform holding the Grafioschtrader authored import templates (receipt PDFs, transaction CSV
   * export). A property of the instance rather than of a client: an administrator picks the platform in the import
   * template screen, a tenant only opts in through its use GT import templates flag. Without this row the option is not
   * offered at all.
   */
  public static final String GLOB_KEY_GT_IMPORT_PLATFORM_ID = GlobalConstants.GT_PREFIX + "import.platform.id";

  public static final String GLOB_KEY_SOURCE_DEMO_ID_TENANT_DE = GlobalConstants.GT_PREFIX + "source.demo.idtenant.de";
  public static final String GLOB_KEY_SOURCE_DEMO_ID_TENANT_EN = GlobalConstants.GT_PREFIX + "source.demo.idtenant.en";

}
