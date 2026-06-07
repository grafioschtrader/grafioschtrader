package grafioschtrader;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import grafiosch.GlobalParamKeyBaseDefault;
import grafiosch.dto.MaxDefaultDBValue;
import grafiosch.entities.Globalparameters;
import grafiosch.entities.ProposeUserTask;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GenericConnectorDef;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.HistoryquoteLegacy;
import grafioschtrader.entities.ImportTransactionPlatform;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.entities.RiskFreeRateMapping;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.entities.UDFMetadataSecurity;

/**
 * GrafioschTrader-specific global parameter keys and default values extending base configuration.
 * 
 * <p>This class defines trading platform-specific configuration parameters including connector settings,
 * data feed configurations, market data processing parameters, tenant limits, and trading-related constraints.
 * It extends the base global parameters with domain-specific settings for financial data management,
 * price updates, and trading platform operations.</p>
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

  public static final String GLOB_KEY_CURRENCY_PRECISION = GlobalConstants.GT_PREFIX + "currency.precision";
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
  public static final String GLOB_KEY_GTNET_EXCHANGE_SYNC_TIMESTAMP = GlobalConstants.GT_PREFIX + "gtnet.exchange.sync.timestamp";
  /** Default value for GTNet exchange sync timestamp - epoch start means never synced. */
  public static final LocalDateTime DEFAULT_GTNET_EXCHANGE_SYNC_TIMESTAMP = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
  /** Tenant data entity limits */
  private static final String MAX = "max.";
  /** Total (lifetime) number of transactions a single tenant may create. Resolves to {@code gt.max.transaction}. */
  public static final String GLOB_KEY_MAX_TRANSACTION = GlobalConstants.GT_PREFIX + MAX + "transaction";
  public static final String GLOB_KEY_MAX_CASH_ACCOUNT = GlobalConstants.GT_PREFIX + MAX + "cash.account";
  public static final String GLOB_KEY_MAX_PORTFOLIO = GlobalConstants.GT_PREFIX + MAX + "portfolio";
  public static final String GLOB_KEY_MAX_SECURITY_ACCOUNT = GlobalConstants.GT_PREFIX + MAX + "security.account";
  public static final String GLOB_KEY_MAX_SECURITIES_CURRENCIES = GlobalConstants.GT_PREFIX + MAX
      + "securities.currencies";
  public static final String GLOB_KEY_MAX_WATCHTLIST = GlobalConstants.GT_PREFIX + MAX + "watchlist";
  public static final String GLOB_KEY_MAX_WATCHLIST_LENGTH = GlobalConstants.GT_PREFIX + MAX + "watchlist.length";
  public static final String GLOB_KEY_MAX_CORRELATION_SET = GlobalConstants.GT_PREFIX + MAX + "correlation.set";
  public static final String GLOB_KEY_MAX_CORRELATION_INSTRUMENTS = GlobalConstants.GT_PREFIX + MAX
      + "correlation.instruments";
  /** Maximum number of split entries per instrument. Resolves to {@code gt.max.instrument.splits}. */
  public static final String GLOB_KEY_MAX_INSTRUMENT_SPLITS = GlobalConstants.GT_PREFIX + MAX + "instrument.splits";
  /**
   * Maximum number of history-quote periods per instrument. Resolves to
   * {@code gt.max.instrument.historyquote.periods}.
   */
  public static final String GLOB_KEY_MAX_INSTRUMENT_HISTORYQUOTE_PERIODS = GlobalConstants.GT_PREFIX + MAX
      + "instrument.historyquote.periods";
  public static final String GLOB_KEY_UPDATE_PRICE_BY_EXCHANGE = GlobalConstants.GT_PREFIX + "update.price.by.exchange";
  /**
   * Three-state switch for connector ↔ asset class compatibility checking, evaluated against
   * only, 2 enforces server-side AND tells the frontend dropdown to hide incompatible connectors.
   */
  public static final String GLOB_KEY_FORCE_CONNECTOR_MATCH = GlobalConstants.GT_PREFIX + "force.connector.match";

  public static final String GLOB_KEY_LIMIT_DAY_ASSETCLASS = GlobalConstants.GT_LIMIT_DAY
      + Assetclass.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_STOCKEXCHANGE = GlobalConstants.GT_LIMIT_DAY
      + Stockexchange.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_PROPOSEUSERTASK = GlobalConstants.GT_LIMIT_DAY
      + ProposeUserTask.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_SECURITY = GlobalConstants.GT_LIMIT_DAY
      + Security.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_CURRENCYPAIR = GlobalConstants.GT_LIMIT_DAY
      + Currencypair.class.getSimpleName();

  public static final String GLOB_KEY_LIMIT_DAY_IMPORTTRANSACTIONTEMPLATE = GlobalConstants.GT_LIMIT_DAY
      + ImportTransactionTemplate.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_IMPORTTRANSACTIONPLATFORM = GlobalConstants.GT_LIMIT_DAY
      + ImportTransactionPlatform.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_TRADINGPLATFORMPLAN = GlobalConstants.GT_LIMIT_DAY
      + TradingPlatformPlan.class.getSimpleName();

  public static final String GLOB_KEY_LIMIT_DAY_UDFMETADATASEUCIRTY = GlobalConstants.GT_LIMIT_DAY
      + UDFMetadataSecurity.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_GTNETSECURITYIMPORT = GlobalConstants.GT_LIMIT_DAY
      + "GTNetSecurityImport";
  public static final String GLOB_KEY_LIMIT_DAY_GENERICCONNECTORDEF = GlobalConstants.GT_LIMIT_DAY
      + GenericConnectorDef.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_RISKFREERATEMAPPING = GlobalConstants.GT_LIMIT_DAY
      + RiskFreeRateMapping.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_HISTORYQUOTE = GlobalConstants.GT_LIMIT_DAY
      + Historyquote.class.getSimpleName();
  public static final String GLOB_KEY_LIMIT_DAY_HISTORYQUOTELEGACY = GlobalConstants.GT_LIMIT_DAY
      + HistoryquoteLegacy.class.getSimpleName();
  public static final String GLOB_KEY_MAX_SIMULATION_ENVIRONMENTS = GlobalConstants.GT_PREFIX + MAX
      + "simulation.environments";
  public static final String GLOB_KEY_MAX_STANDING_ORDER = GlobalConstants.GT_PREFIX + MAX + "standing.order";
  public static final String GLOB_KEY_SOURCE_DEMO_ID_TENANT_DE = GlobalConstants.GT_PREFIX + "source.demo.idtenant.de";
  public static final String GLOB_KEY_SOURCE_DEMO_ID_TENANT_EN = GlobalConstants.GT_PREFIX + "source.demo.idtenant.en";

  public GlobalParamKeyDefault() {
    super();
    Map<String, MaxDefaultDBValue> defaultLimitMap = Globalparameters.defaultLimitMap;
    /** Set tenant data entity limits in total on not shared entries. */
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_MAX_TRANSACTION, new MaxDefaultDBValue(5000));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_MAX_CASH_ACCOUNT, new MaxDefaultDBValue(30));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_MAX_PORTFOLIO, new MaxDefaultDBValue(20));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_MAX_SECURITY_ACCOUNT, new MaxDefaultDBValue(20));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_MAX_SECURITIES_CURRENCIES, new MaxDefaultDBValue(2000));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_MAX_WATCHTLIST, new MaxDefaultDBValue(30));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_MAX_WATCHLIST_LENGTH, new MaxDefaultDBValue(200));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_MAX_CORRELATION_SET, new MaxDefaultDBValue(10));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_MAX_CORRELATION_INSTRUMENTS, new MaxDefaultDBValue(20));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_MAX_SIMULATION_ENVIRONMENTS, new MaxDefaultDBValue(5));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_MAX_STANDING_ORDER, new MaxDefaultDBValue(50));

    // Set tenant regulations violations, with daily CUD limits on shared entries
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_ASSETCLASS, new MaxDefaultDBValue(10));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_STOCKEXCHANGE, new MaxDefaultDBValue(10));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_PROPOSEUSERTASK, new MaxDefaultDBValue(10));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_SECURITY, new MaxDefaultDBValue(50));

    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_CURRENCYPAIR, new MaxDefaultDBValue(15));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_IMPORTTRANSACTIONTEMPLATE, new MaxDefaultDBValue(10));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_IMPORTTRANSACTIONPLATFORM, new MaxDefaultDBValue(3));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_TRADINGPLATFORMPLAN, new MaxDefaultDBValue(3));

    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_UDFMETADATASEUCIRTY, new MaxDefaultDBValue(20));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_GTNETSECURITYIMPORT, new MaxDefaultDBValue(150));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_GENERICCONNECTORDEF, new MaxDefaultDBValue(10));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_RISKFREERATEMAPPING, new MaxDefaultDBValue(2));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_HISTORYQUOTE, new MaxDefaultDBValue(15));
    defaultLimitMap.put(GlobalParamKeyDefault.GLOB_KEY_LIMIT_DAY_HISTORYQUOTELEGACY, new MaxDefaultDBValue(15));

  }

}
