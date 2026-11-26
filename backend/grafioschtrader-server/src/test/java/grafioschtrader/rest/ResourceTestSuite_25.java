package grafioschtrader.rest;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Continues the integration-test state after Playwright specs 005 through 050 have completed.
 *
 * <p>
 * {@link PortfolioResourceTest} runs first because its fixtures can consume trading platform plans created by
 * Playwright spec 045. {@link PerformanceBenchmarkResourceTest} needs those portfolios and leaves no benchmark behind.
 * Transaction creation remains deferred to {@link ResourceTestSuite_50}, after Playwright spec 095 has initialized the
 * required currency pairs.
 */
@Suite
@SelectClasses({ PortfolioResourceTest.class, CostBasisMethodResourceTest.class, PerformanceBenchmarkResourceTest.class,
    GermanTaxHelpResourceTest.class, WatchlistResourceTest.class, UDFSharingResourceTest.class,
    TaskDataChangeResourceTest.class, EntityLimitRoleResourceTest.class })
public class ResourceTestSuite_25 {

}
