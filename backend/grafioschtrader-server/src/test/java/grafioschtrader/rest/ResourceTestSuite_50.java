package grafioschtrader.rest;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Creates integration-owned bank-account transactions after Playwright spec 045 has initialized currency pairs.
 *
 * <p>
 * In particular, the USD/CHF transfer fixture must not trigger on-demand currency-pair creation while a transaction is
 * being saved. The roundtrip runner therefore executes this suite after Playwright 045 and before resuming at 050.
 */
@Suite
@SelectClasses({ TransactionResourceTest.class })
public class ResourceTestSuite_50 {

}
