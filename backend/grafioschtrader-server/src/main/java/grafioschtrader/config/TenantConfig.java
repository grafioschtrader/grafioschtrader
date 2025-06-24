package grafioschtrader.config;

import grafiosch.repository.TenantLimitsHelper;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.CorrelationSet;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Watchlist;

public abstract class TenantConfig {

  public static void initialzie() {
    /**
     * Put only entities in here which can be checked when added with "SELECT count() FROM ..."
     */
    TenantLimitsHelper.GLOBAL_LIMIT_KEY_TO_ENTITY_MAP.put(GlobalParamKeyDefault.GLOB_KEY_MAX_CASH_ACCOUNT,
        Cashaccount.class);
    TenantLimitsHelper.GLOBAL_LIMIT_KEY_TO_ENTITY_MAP.put(GlobalParamKeyDefault.GLOB_KEY_MAX_CORRELATION_SET,
        CorrelationSet.class);
    TenantLimitsHelper.GLOBAL_LIMIT_KEY_TO_ENTITY_MAP.put(GlobalParamKeyDefault.GLOB_KEY_MAX_PORTFOLIO, Portfolio.class);
    TenantLimitsHelper.GLOBAL_LIMIT_KEY_TO_ENTITY_MAP.put(GlobalParamKeyDefault.GLOB_KEY_MAX_SECURITY_ACCOUNT,
        Securityaccount.class);
    TenantLimitsHelper.GLOBAL_LIMIT_KEY_TO_ENTITY_MAP.put(GlobalParamKeyDefault.GLOB_KEY_MAX_WATCHTLIST, Watchlist.class);
  }
}
