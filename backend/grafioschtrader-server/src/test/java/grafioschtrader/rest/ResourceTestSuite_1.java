package grafioschtrader.rest;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Builds the integration-test state required by Playwright specs 005 through 020.
 *
 * <p>
 * The suite deliberately stops before {@link PortfolioResourceTest}: integration-owned portfolios can reference trading
 * platform plans created by Playwright spec 015. The roundtrip runner continues with {@link ResourceTestSuite_25} after
 * the early Playwright phase.
 *
 * <p>
 * {@link SimulationContextResourceTest} follows {@link TenantResourceTest}: it needs nothing but the registered users
 * and builds the tenants it examines itself, so it is placed as early as its prerequisites allow.
 */
@Suite
@SelectClasses({ UserResourceTest.class, EntityLimitResourceTest.class, ImportTransactionPlatformResourceTest.class,
    TenantResourceTest.class, SimulationContextResourceTest.class, TradingPlatformPlanResourceTest.class,
    StockexchangeResourceTest.class, TradingCalendarRuleSetResourceTest.class, AssetclassResourceTest.class,
    SecurityResourceTest.class, GTNetExchangeAuthorizationTest.class })
public class ResourceTestSuite_1 {

}
