package grafioschtrader.config;

import grafiosch.config.LimitKeyBaseConfig;
import grafiosch.dto.LimitKey;
import grafiosch.entities.User;
import grafiosch.limit.EntityLimitCounter;
import grafiosch.limit.LimitCounters;
import grafiosch.limit.LimitKeyRegistration;
import grafiosch.limit.LimitKeyRegistry;
import grafiosch.types.CountScope;
import grafiosch.types.OwnerScope;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoEventLog;
import grafioschtrader.entities.AlgoExecutionState;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.CorrelationSet;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNetSecurityImpHead;
import grafioschtrader.entities.GTNetSecurityImpPos;
import grafioschtrader.entities.GenericConnectorDef;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionPlatform;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.RiskFreeRateMapping;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.StandingOrder;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.TaxYearCorrection;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.TradingCalendarRuleSet;
import grafioschtrader.entities.TradingDaysMinus;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.entities.Watchlist;
import jakarta.persistence.EntityManager;

/**
 * Declares Grafioschtrader's lifetime ({@code MAX}) limit keys and its pseudo entity names, and registers them with the
 * limit key registry. It replaces the former {@code TenantConfig.initialzie()} and the {@code defaultLimitMap}
 * constructors, and is the single place where the application says which caps exist.
 *
 * <p>
 * The registrations live in the application layer so that {@code grafiosch-base} and {@code grafiosch-server-base} name
 * no {@code grafioschtrader-*} class. The daily {@code DAY_CUD} and {@code DAY_READ} keys are deliberately absent: they
 * are derived from the JPA metamodel plus the pseudo names registered here, which keeps an administrator's reach as
 * wide as it is today, including entities that have no configured default at all.
 * </p>
 *
 * <p>
 * The {@code defaultValue} of a registration is the <b>fresh-install</b> value only. In an installation that ever
 * changed a limit, the value in use is the one the migration copied out of the former {@code globalparameters} row.
 * {@code EntityLimitSeedGuardTest} checks that the literals in the migration and the defaults here agree.
 * </p>
 */
public abstract class LimitKeyConfig {

  /**
   * Pseudo entity name for the number of simulation environments. The limit counts {@code tenant} rows by
   * {@code id_parent_tenant}, which is not expressible as a plain {@code Tenant} cap, so it must never be enforced on
   * an ordinary tenant create.
   */
  public static final String ENTITY_NAME_SIMULATION_TENANT = "SimulationTenant";

  /**
   * Pseudo entity name for securities created by the GTNet import. Kept separate from the ordinary {@code Security}
   * cap so that network synchronisation does not consume a user's manual budget. It carries both a daily CUD limit and
   * a lifetime cap.
   */
  public static final String ENTITY_NAME_GTNET_SECURITY_IMPORT = "GTNetSecurityImport";

  /** Pseudo entity name for the daily budget of distinct instruments whose price history may be read over REST. */
  public static final String ENTITY_NAME_HISTORYQUOTE_READ = "HistoryquoteRead";

  /**
   * Pseudo entity name for the daily budget of the trading calendar. Its unit is one stock exchange year touched, not
   * one {@code trading_days_minus} row: a calendar is always edited or copied a year at a time, and counting rows would
   * make a single sparse year and a whole copied decade cost almost the same. {@code TradingDaysMinus} has no daily key
   * of its own either way — it is not a {@code BaseID} entity, so it never appears in the derived daily key space.
   */
  public static final String ENTITY_NAME_TRADING_DAYS_MINUS_YEAR = "TradingDaysMinusYear";

  /**
   * Pseudo entity name for the daily budget of manual alert actions: an immediate evaluation of the tenant's alerts or
   * the retry of a notification delivery. Its unit is one action, not one row of any table.
   */
  public static final String ENTITY_NAME_ALGO_ALERT_ACTION = "AlgoAlertAction";

  private static final String RELATION_SECURITYCURRENCY = "Securitycurrency";

  private static final String RULE_SHARED_DATA = "min:10,max:100000";

  /** Staging positions of a tenant, reached through their head because the position itself carries no tenant. */
  private static final String GTNET_IMP_POS_OF_TENANT = "SELECT count(p) FROM GTNetSecurityImpPos p, "
      + "GTNetSecurityImpHead h WHERE h.idGtNetSecurityImpHead = p.idGtNetSecurityImpHead AND h.idTenant = ?1";

  // Flat per-tenant caps.
  public static final LimitKey KEY_CASH_ACCOUNT = LimitKey.max(Cashaccount.class.getSimpleName(), OwnerScope.TENANT);
  public static final LimitKey KEY_PORTFOLIO = LimitKey.max(Portfolio.class.getSimpleName(), OwnerScope.TENANT);
  public static final LimitKey KEY_SECURITY_ACCOUNT = LimitKey.max(Securityaccount.class.getSimpleName(),
      OwnerScope.TENANT);
  public static final LimitKey KEY_WATCHLIST = LimitKey.max(Watchlist.class.getSimpleName(), OwnerScope.TENANT);
  public static final LimitKey KEY_CORRELATION_SET = LimitKey.max(CorrelationSet.class.getSimpleName(),
      OwnerScope.TENANT);
  public static final LimitKey KEY_TRANSACTION = LimitKey.max(Transaction.class.getSimpleName(), OwnerScope.TENANT);
  public static final LimitKey KEY_STANDING_ORDER = LimitKey.max(StandingOrder.class.getSimpleName(),
      OwnerScope.TENANT);
  public static final LimitKey KEY_SIMULATION_TENANT = LimitKey.max(ENTITY_NAME_SIMULATION_TENANT, OwnerScope.TENANT);
  public static final LimitKey KEY_IMPORT_TRANSACTION_HEAD = LimitKey
      .max(ImportTransactionHead.class.getSimpleName(), OwnerScope.TENANT);
  public static final LimitKey KEY_IMPORT_TRANSACTION_POS = LimitKey.max(ImportTransactionPos.class.getSimpleName(),
      OwnerScope.TENANT);
  public static final LimitKey KEY_TAX_YEAR_CORRECTION = LimitKey.max(TaxYearCorrection.class.getSimpleName(),
      OwnerScope.TENANT);
  public static final LimitKey KEY_GTNET_SECURITY_IMP_HEAD = LimitKey.max(GTNetSecurityImpHead.class.getSimpleName(),
      OwnerScope.TENANT);
  public static final LimitKey KEY_ALGO_TOP = LimitKey.max(AlgoTop.class.getSimpleName(), OwnerScope.TENANT);
  public static final LimitKey KEY_ALGO_ASSETCLASS = LimitKey.max(AlgoAssetclass.class.getSimpleName(),
      OwnerScope.TENANT);
  public static final LimitKey KEY_ALGO_SECURITY = LimitKey.max(AlgoSecurity.class.getSimpleName(), OwnerScope.TENANT);
  public static final LimitKey KEY_ALGO_STRATEGY = LimitKey.max(AlgoStrategy.class.getSimpleName(),
      OwnerScope.TENANT);
  public static final LimitKey KEY_ALGO_EXECUTION_STATE = LimitKey.max(AlgoExecutionState.class.getSimpleName(),
      OwnerScope.TENANT);
  public static final LimitKey KEY_ALGO_EVENT_LOG = LimitKey.max(AlgoEventLog.class.getSimpleName(),
      OwnerScope.TENANT);

  // Caps on the elements of a nested collection.
  /** Instruments in one watchlist. */
  public static final LimitKey KEY_WATCHLIST_LENGTH = LimitKey.max(Watchlist.class.getSimpleName(),
      RELATION_SECURITYCURRENCY, CountScope.SINGLE, OwnerScope.TENANT);
  /** Instruments across all watchlists of the tenant. */
  public static final LimitKey KEY_SECURITIES_CURRENCIES = LimitKey.max(Watchlist.class.getSimpleName(),
      RELATION_SECURITYCURRENCY, CountScope.ALL, OwnerScope.TENANT);
  public static final LimitKey KEY_CORRELATION_INSTRUMENTS = LimitKey.max(CorrelationSet.class.getSimpleName(),
      RELATION_SECURITYCURRENCY, CountScope.SINGLE, OwnerScope.TENANT);
  public static final LimitKey KEY_INSTRUMENT_SPLITS = LimitKey.max(Security.class.getSimpleName(), "Securitysplit",
      CountScope.SINGLE, OwnerScope.GLOBAL);
  public static final LimitKey KEY_INSTRUMENT_HISTORYQUOTE_PERIODS = LimitKey.max(Security.class.getSimpleName(),
      "HistoryquotePeriod", CountScope.SINGLE, OwnerScope.GLOBAL);
  /** Positions of one import set. Bounds a single upload; the flat key above bounds the table of the tenant. */
  public static final LimitKey KEY_IMPORT_TRANSACTION_POS_PER_HEAD = LimitKey.max(
      ImportTransactionHead.class.getSimpleName(), ImportTransactionPos.class.getSimpleName(), CountScope.SINGLE,
      OwnerScope.TENANT);
  /** Staging positions over all GTNet import sets of the tenant. */
  public static final LimitKey KEY_GTNET_SECURITY_IMP_POS_ALL = LimitKey.max(
      GTNetSecurityImpHead.class.getSimpleName(), GTNetSecurityImpPos.class.getSimpleName(), CountScope.ALL,
      OwnerScope.TENANT);
  /** Staging positions of one GTNet import set. */
  public static final LimitKey KEY_GTNET_SECURITY_IMP_POS_SINGLE = LimitKey.max(
      GTNetSecurityImpHead.class.getSimpleName(), GTNetSecurityImpPos.class.getSimpleName(), CountScope.SINGLE,
      OwnerScope.TENANT);

  // Lifetime caps on shared data, counted by the current created_by.
  public static final LimitKey KEY_SECURITY = LimitKey.max(Security.class.getSimpleName(), OwnerScope.CREATOR);
  public static final LimitKey KEY_CURRENCYPAIR = LimitKey.max(Currencypair.class.getSimpleName(), OwnerScope.CREATOR);
  public static final LimitKey KEY_ASSETCLASS = LimitKey.max(Assetclass.class.getSimpleName(), OwnerScope.CREATOR);
  public static final LimitKey KEY_STOCKEXCHANGE = LimitKey.max(Stockexchange.class.getSimpleName(),
      OwnerScope.CREATOR);
  public static final LimitKey KEY_GTNET_SECURITY_IMPORT = LimitKey.max(ENTITY_NAME_GTNET_SECURITY_IMPORT,
      OwnerScope.CREATOR);
  public static final LimitKey KEY_GENERIC_CONNECTOR_DEF = LimitKey.max(GenericConnectorDef.class.getSimpleName(),
      OwnerScope.CREATOR);
  public static final LimitKey KEY_IMPORT_TRANSACTION_PLATFORM = LimitKey
      .max(ImportTransactionPlatform.class.getSimpleName(), OwnerScope.CREATOR);
  public static final LimitKey KEY_IMPORT_TRANSACTION_TEMPLATE = LimitKey
      .max(ImportTransactionTemplate.class.getSimpleName(), OwnerScope.CREATOR);
  public static final LimitKey KEY_TRADING_CALENDAR_RULE_SET = LimitKey
      .max(TradingCalendarRuleSet.class.getSimpleName(), OwnerScope.CREATOR);
  public static final LimitKey KEY_TRADING_PLATFORM_PLAN = LimitKey.max(TradingPlatformPlan.class.getSimpleName(),
      OwnerScope.CREATOR);
  public static final LimitKey KEY_RISK_FREE_RATE_MAPPING = LimitKey.max(RiskFreeRateMapping.class.getSimpleName(),
      OwnerScope.CREATOR);
  public static final LimitKey KEY_UDF_METADATA_SECURITY = LimitKey.max(UDFMetadataSecurity.class.getSimpleName(),
      OwnerScope.CREATOR);

  // Derived daily keys the application checks explicitly rather than through the generic CUD path.
  public static final LimitKey KEY_DAY_GTNET_SECURITY_IMPORT = LimitKey.dayCud(ENTITY_NAME_GTNET_SECURITY_IMPORT);
  public static final LimitKey KEY_DAY_HISTORYQUOTE_READ = LimitKey.dayRead(ENTITY_NAME_HISTORYQUOTE_READ);
  public static final LimitKey KEY_DAY_TRADING_DAYS_MINUS_YEAR = LimitKey
      .dayCud(ENTITY_NAME_TRADING_DAYS_MINUS_YEAR);

  private LimitKeyConfig() {
  }

  public static void initialize() {
    // Keys the reusable library enforces itself. They are registered from here because the application layer decides
    // the whole set of caps and is the only layer that can seed their defaults in a migration.
    LimitKeyBaseConfig.initialize();
    registerTenantCaps();
    registerNestedCaps();
    registerSharedDataCaps();
    registerPseudoEntityNames();
  }

  /**
   * Flat per-tenant caps. Only the first five are enforced by the generic create path, exactly as before; the other
   * three keep their hand-written call site, which produces a specific translated message that the generic
   * {@code LIMIT_SECURITY_BREACH} would otherwise replace with a bare 401.
   */
  private static void registerTenantCaps() {
    registerFlat(KEY_CASH_ACCOUNT, Cashaccount.class, 30, null, "MAX_CASH_ACCOUNT", true);
    registerFlat(KEY_PORTFOLIO, Portfolio.class, 20, null, "MAX_PORTFOLIO", true);
    registerFlat(KEY_SECURITY_ACCOUNT, Securityaccount.class, 20, null, "MAX_SECURITY_ACCOUNT", true);
    registerFlat(KEY_WATCHLIST, Watchlist.class, 30, null, "MAX_WATCHLIST", true);
    registerFlat(KEY_CORRELATION_SET, CorrelationSet.class, 10, null, "MAX_CORRELATION_SET", true);

    registerFlat(KEY_TRANSACTION, Transaction.class, 5000, null, "MAX_TRANSACTION", false);
    registerFlat(KEY_STANDING_ORDER, StandingOrder.class, 50, null, "MAX_STANDING_ORDER", false);

    // Staging tables of the transaction import and of the GTNet instrument import. They had no cap at all: the daily
    // check skips every TenantBaseID, so nothing bounded how large a tenant could grow them.
    registerFlat(KEY_IMPORT_TRANSACTION_HEAD, ImportTransactionHead.class, 20, null, "MAX_IMPORT_TRANSACTION_HEAD",
        true);
    registerFlat(KEY_TAX_YEAR_CORRECTION, TaxYearCorrection.class, 1000, null, "MAX_TAX_YEAR_CORRECTION", true);
    registerFlat(KEY_GTNET_SECURITY_IMP_HEAD, GTNetSecurityImpHead.class, 200, null, "MAX_GT_NET_SECURITY_IMP_HEAD",
        true);
    // Import positions have no generic create at all - rows arrive only through the head scoped upload endpoints,
    // which check this key at the single site where a new position row is written.
    registerFlat(KEY_IMPORT_TRANSACTION_POS, ImportTransactionPos.class, 8000, null, "MAX_IMPORT_TRANSACTION_POS",
        false);

    // Strategy hierarchy and its engine state. Checked at their own write sites rather than by the generic create:
    // the repository implementations of the hierarchy check every new node, the hierarchy generation from holdings or
    // a watchlist checks a whole generated tree at once, and the position lifecycle of mean reversion and the event
    // cap of a historical replay are written by the engine. The hierarchy caps report a translated message instead of
    // the generic LIMIT_SECURITY_BREACH, which would count towards locking the user out.
    registerFlat(KEY_ALGO_TOP, AlgoTop.class, 20, null, "MAX_ALGO_TOP", false);
    registerFlat(KEY_ALGO_ASSETCLASS, AlgoAssetclass.class, 200, null, "MAX_ALGO_ASSETCLASS", false);
    registerFlat(KEY_ALGO_SECURITY, AlgoSecurity.class, 2000, null, "MAX_ALGO_SECURITY", false);
    registerFlat(KEY_ALGO_STRATEGY, AlgoStrategy.class, 4000, null, "MAX_ALGO_STRATEGY", false);
    registerFlat(KEY_ALGO_EXECUTION_STATE, AlgoExecutionState.class, 10000, null, "MAX_ALGO_EXECUTION_STATE", false);
    registerFlat(KEY_ALGO_EVENT_LOG, AlgoEventLog.class, 200000, null, "MAX_ALGO_EVENT_LOG", false);

    // Counts tenant rows by their parent, so it can only ever be checked where a simulation environment is created.
    LimitKeyRegistry.register(new LimitKeyRegistration(KEY_SIMULATION_TENANT, Tenant.class,
        (entityManager, user, _, _) -> user == null ? 0
            : entityManager.createQuery("SELECT count(t) FROM Tenant t WHERE t.idParentTenant = ?1", Long.class)
                .setParameter(1, user.getIdTenant()).getSingleResult().intValue(),
        5, null, "MAX_SIMULATION_ENVIRONMENTS", false));
  }

  /**
   * Caps on the elements of a nested collection. Their parent id is the watchlist, correlation set or instrument the
   * new element goes into, so they are always checked at the call site that knows it.
   */
  private static void registerNestedCaps() {
    LimitKeyRegistry.register(new LimitKeyRegistration(KEY_WATCHLIST_LENGTH, Watchlist.class,
        (entityManager, user, _, parentId) -> countSingleParent(entityManager,
            "SELECT count(s) FROM Watchlist w JOIN w.securitycurrencyList s WHERE w.idTenant = ?1 AND w.idWatchlist = ?2",
            user, parentId),
        200, null, "MAX_WATCHLIST_LENGTH", false));

    LimitKeyRegistry.register(new LimitKeyRegistration(KEY_SECURITIES_CURRENCIES, Watchlist.class,
        (entityManager, user, _, _) -> user == null ? 0
            : entityManager
                .createQuery("SELECT count(s) FROM Watchlist w JOIN w.securitycurrencyList s WHERE w.idTenant = ?1",
                    Long.class)
                .setParameter(1, user.getIdTenant()).getSingleResult().intValue(),
        2000, null, "MAX_SECURITIES_CURRENCIES", false));

    LimitKeyRegistry.register(new LimitKeyRegistration(KEY_CORRELATION_INSTRUMENTS, CorrelationSet.class,
        (entityManager, user, _, parentId) -> countSingleParent(entityManager,
            "SELECT count(s) FROM CorrelationSet c JOIN c.securitycurrencyList s WHERE c.idTenant = ?1 AND c.idCorrelationSet = ?2",
            user, parentId),
        20, "min:2,max:24", "MAX_CORRELATION_INSTRUMENTS", false));

    // Both instrument caps are GLOBAL: an instrument is shared data, so its splits and history-quote periods are not
    // counted per tenant.
    LimitKeyRegistry.register(new LimitKeyRegistration(KEY_INSTRUMENT_SPLITS, Security.class,
        countByInstrument("Securitysplit"), 20, null, "MAX_INSTRUMENT_SPLITS", false));

    LimitKeyRegistry.register(new LimitKeyRegistration(KEY_INSTRUMENT_HISTORYQUOTE_PERIODS, Security.class,
        countByInstrument("HistoryquotePeriod"), 20, null, "MAX_INSTRUMENT_HISTORYQUOTE_PERIODS", false));

    // Both staging tables get a ceiling on two levels. A tenant total alone would still allow one absurdly large
    // import set, and a per parent cap alone would allow an unbounded number of parents.
    LimitKeyRegistry.register(new LimitKeyRegistration(KEY_IMPORT_TRANSACTION_POS_PER_HEAD, ImportTransactionHead.class,
        (entityManager, user, _, parentId) -> countSingleParent(entityManager,
            "SELECT count(p) FROM ImportTransactionPos p WHERE p.idTenant = ?1 AND p.idTransactionHead = ?2", user,
            parentId),
        2000, null, "MAX_IMPORT_TRANSACTION_POS_PER_HEAD", false));

    // GTNetSecurityImpPos carries no tenant column - it extends plain BaseID and reaches the tenant only through its
    // head - so even its tenant total has to be the nested ALL key rather than a flat one.
    LimitKeyRegistry.register(new LimitKeyRegistration(KEY_GTNET_SECURITY_IMP_POS_ALL, GTNetSecurityImpHead.class,
        (entityManager, user, _, _) -> user == null || user.getIdTenant() == null ? 0
            : entityManager.createQuery(GTNET_IMP_POS_OF_TENANT, Long.class).setParameter(1, user.getIdTenant())
                .getSingleResult().intValue(),
        1000, null, "MAX_GT_NET_SECURITY_IMP_POS_ALL", false));

    LimitKeyRegistry.register(new LimitKeyRegistration(KEY_GTNET_SECURITY_IMP_POS_SINGLE, GTNetSecurityImpHead.class,
        (entityManager, user, _, parentId) -> countSingleParent(entityManager,
            GTNET_IMP_POS_OF_TENANT + " AND h.idGtNetSecurityImpHead = ?2", user, parentId),
        200, null, "MAX_GT_NET_SECURITY_IMP_POS_SINGLE", false));
  }

  /**
   * Lifetime caps on shared data, counted by the current {@code created_by}. These did not exist before: shared data
   * had only a daily rate limit, so rows accumulated without bound over time.
   *
   * <p>
   * A count moves with the rows when {@code MoveCreatedByUserToOtherUserTask} reassigns the shared data of a deleted
   * user. That is intended — the cap bounds who is answerable for the rows, not who originally typed them.
   * </p>
   *
   * <p>
   * Some shared tables have a daily budget but deliberately no lifetime cap, because nothing counts them per user:
   * </p>
   * <ul>
   * <li>The GTNet tables ({@code GTNet}, {@code GTNetConfig}, {@code GTNetConfigEntity}, {@code GTNetMessage},
   * {@code GTNetMessageAnswer}) carry no {@code created_by}, are written over REST by an administrator only, and grow
   * mostly through the machine-to-machine traffic of remote peers. Bounding a peer is a matter of GTNet, not of a
   * user's limit.</li>
   * <li>{@code Historyquote}, {@code HistoryquoteLegacy} and the trading calendar ({@code TradingDaysMinusYear}) have
   * one row per instrument or stock exchange and date, and no owner. Their size follows from the capped number of
   * instruments and stock exchanges, and their growth rate from the daily budget.</li>
   * <li>{@code GTNetSecurityImpPos} is bounded per tenant by {@link #KEY_GTNET_SECURITY_IMP_POS_ALL}, which already is
   * its flat cap reached through the head.</li>
   * </ul>
   */
  private static void registerSharedDataCaps() {
    // Securities the GTNet import created are excluded here and counted against their own key below.
    LimitKeyRegistry.register(new LimitKeyRegistration(KEY_SECURITY, Security.class,
        (entityManager, user, _, _) -> user == null ? 0
            : entityManager.createQuery("""
                SELECT count(s) FROM Security s WHERE s.createdBy = ?1 AND NOT EXISTS
                  (SELECT p FROM GTNetSecurityImpPos p WHERE p.security = s AND p.securityCreatedByImport = true)
                """, Long.class).setParameter(1, user.getIdUser()).getSingleResult().intValue(),
        2000, RULE_SHARED_DATA, "MAX_SECURITY", true));

    registerFlat(KEY_CURRENCYPAIR, Currencypair.class, 500, RULE_SHARED_DATA, "MAX_CURRENCYPAIR", true);
    registerFlat(KEY_ASSETCLASS, Assetclass.class, 200, RULE_SHARED_DATA, "MAX_ASSETCLASS", true);
    registerFlat(KEY_STOCKEXCHANGE, Stockexchange.class, 100, RULE_SHARED_DATA, "MAX_STOCKEXCHANGE", true);

    // Counted through the staging table rather than a column on security, because nothing on the security itself
    // records that an import created it.
    LimitKeyRegistry.register(new LimitKeyRegistration(KEY_GTNET_SECURITY_IMPORT, Security.class,
        (entityManager, user, _, _) -> user == null ? 0
            : entityManager.createQuery("""
                SELECT count(p) FROM GTNetSecurityImpPos p WHERE p.securityCreatedByImport = true
                  AND p.security.createdBy = ?1
                """, Long.class).setParameter(1, user.getIdUser()).getSingleResult().intValue(),
        20000, RULE_SHARED_DATA, "MAX_GT_NET_SECURITY_IMPORT", false));

    // Reference data a user may create beside the instruments. Each had a daily budget only; the defaults are ceilings on
    // abuse, several times what the largest creator of the production database holds today.
    registerFlat(KEY_GENERIC_CONNECTOR_DEF, GenericConnectorDef.class, 20, RULE_SHARED_DATA,
        "MAX_GENERIC_CONNECTOR_DEF", true);
    registerFlat(KEY_IMPORT_TRANSACTION_PLATFORM, ImportTransactionPlatform.class, 50, RULE_SHARED_DATA,
        "MAX_IMPORT_TRANSACTION_PLATFORM", true);
    // The template file upload writes new templates outside the generic create and checks this key itself.
    registerFlat(KEY_IMPORT_TRANSACTION_TEMPLATE, ImportTransactionTemplate.class, 300, RULE_SHARED_DATA,
        "MAX_IMPORT_TRANSACTION_TEMPLATE", true);
    registerFlat(KEY_TRADING_CALENDAR_RULE_SET, TradingCalendarRuleSet.class, 200, RULE_SHARED_DATA,
        "MAX_TRADING_CALENDAR_RULE_SET", true);
    registerFlat(KEY_TRADING_PLATFORM_PLAN, TradingPlatformPlan.class, 100, RULE_SHARED_DATA,
        "MAX_TRADING_PLATFORM_PLAN", true);
    registerFlat(KEY_RISK_FREE_RATE_MAPPING, RiskFreeRateMapping.class, 50, RULE_SHARED_DATA,
        "MAX_RISK_FREE_RATE_MAPPING", true);
    // Private to its user and without created_by, so counted through id_user like its library sibling
    // UDFMetadataGeneral.
    LimitKeyRegistry.register(new LimitKeyRegistration(KEY_UDF_METADATA_SECURITY, UDFMetadataSecurity.class,
        LimitCounters.userCount(UDFMetadataSecurity.class.getSimpleName(), "idUser"), 50, null,
        "MAX_UDF_METADATA_SECURITY", true));
  }

  /**
   * Pseudo entity names have no JPA entity behind them, so the derived daily key space cannot find them in the
   * metamodel. {@code HistoryquoteRead} is registered for the read family only: a row written for it as
   * {@code DAY_CUD} would be invisible to the read resolver and would silently drop that user's read exception.
   */
  private static void registerPseudoEntityNames() {
    LimitKeyRegistry.registerCudPseudoEntityName(ENTITY_NAME_GTNET_SECURITY_IMPORT, Security.class);
    LimitKeyRegistry.registerCudPseudoEntityName(ENTITY_NAME_TRADING_DAYS_MINUS_YEAR, TradingDaysMinus.class);
    // Not pseudo names in the usual sense: these are real entities, but tenant-private ones, which the derived daily
    // key space excludes because UpdateCreate skips the generic daily check for them. They carry an explicit check at
    // their own write site, so without this registration a seeded row would be enforced yet invisible to the admin.
    LimitKeyRegistry.registerCudPseudoEntityName(ImportTransactionHead.class.getSimpleName(),
        ImportTransactionHead.class);
    LimitKeyRegistry.registerCudPseudoEntityName(ImportTransactionPos.class.getSimpleName(),
        ImportTransactionPos.class);
    LimitKeyRegistry.registerCudPseudoEntityName(GTNetSecurityImpHead.class.getSimpleName(),
        GTNetSecurityImpHead.class);
    // Manual alert actions - an immediate evaluation or a delivery retry - are budgeted per day at their endpoint. The
    // name stands for no table; the strategies the alerts belong to are tenant private, which is what the
    // administration shows it as.
    LimitKeyRegistry.registerCudPseudoEntityName(ENTITY_NAME_ALGO_ALERT_ACTION, AlgoStrategy.class);
    LimitKeyRegistry.registerReadPseudoEntityName(ENTITY_NAME_HISTORYQUOTE_READ, Historyquote.class);
  }

  private static void registerFlat(LimitKey limitKey, Class<?> entityClass, int defaultValue, String inputRule,
      String msgKey, boolean checkedOnGenericCreate) {
    EntityLimitCounter counter = limitKey.ownerScope() == OwnerScope.TENANT
        ? LimitCounters.tenantCount(entityClass.getSimpleName())
        : LimitCounters.creatorCount(entityClass.getSimpleName());
    LimitKeyRegistry.register(new LimitKeyRegistration(limitKey, entityClass, counter, defaultValue, inputRule, msgKey,
        checkedOnGenericCreate));
  }

  private static EntityLimitCounter countByInstrument(String jpqlEntityName) {
    return (entityManager, _, _, parentId) -> parentId == null ? 0
        : entityManager
            .createQuery("SELECT count(t) FROM " + jpqlEntityName + " t WHERE t.idSecuritycurrency = ?1", Long.class)
            .setParameter(1, parentId).getSingleResult().intValue();
  }

  private static int countSingleParent(EntityManager entityManager, String jpql, User user, Integer parentId) {
    return user == null || parentId == null ? 0
        : entityManager.createQuery(jpql, Long.class).setParameter(1, user.getIdTenant()).setParameter(2, parentId)
            .getSingleResult().intValue();
  }
}
