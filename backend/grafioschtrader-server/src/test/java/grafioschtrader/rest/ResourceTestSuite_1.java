package grafioschtrader.rest;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Builds the integration-test state required by Playwright specs 005 through 020.
 *
 * <p>
 * The suite deliberately stops before {@link PortfolioResourceTest}: integration-owned portfolios can reference
 * trading platform plans created by Playwright spec 015. The roundtrip runner continues with
 * {@link ResourceTestSuite_25} after the early Playwright phase.
 */
@Suite
@SelectClasses({ UserResourceTest.class, EntityLimitResourceTest.class,
    ImportTransactionPlatformResourceTest.class, TenantResourceTest.class, TradingPlatformPlanResourceTest.class,
    StockexchangeResourceTest.class, TradingCalendarRuleSetResourceTest.class, AssetclassResourceTest.class,
    SecurityResourceTest.class, GTNetExchangeAuthorizationTest.class })
public class ResourceTestSuite_1 {

}
