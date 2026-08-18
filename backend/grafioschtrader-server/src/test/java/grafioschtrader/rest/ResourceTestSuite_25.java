package grafioschtrader.rest;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Continues the integration-test state after Playwright specs 005 through 020 have completed.
 *
 * <p>
 * {@link PortfolioResourceTest} runs first because its fixtures can consume trading platform plans created by
 * Playwright spec 015. Transaction creation remains deferred to {@link ResourceTestSuite_50}, after Playwright spec
 * 045 has initialized the required currency pairs.
 */
@Suite
@SelectClasses({ PortfolioResourceTest.class, WatchlistResourceTest.class, TaskDataChangeResourceTest.class,
    EntityLimitRoleResourceTest.class })
public class ResourceTestSuite_25 {

}
