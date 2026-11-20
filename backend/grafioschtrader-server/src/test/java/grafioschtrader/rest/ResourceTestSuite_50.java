package grafioschtrader.rest;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

import grafioschtrader.mcp.McpServerIntegrationTest;
import grafioschtrader.mcp.oauth.McpOAuthIntegrationTest;

/**
 * Creates integration-owned bank-account transactions after Playwright spec 095 has initialized currency pairs.
 *
 * <p>
 * {@code TradingDaysPlusResourceTest} runs first: it creates the global trading calendar through next year, which every
 * dated security transaction of the Playwright specs from 100 on is validated against. Playwright spec 115 adds the
 * remaining future years through the UI.
 * </p>
 *
 * <p>
 * In particular, the USD/CHF transfer fixture must not trigger on-demand currency-pair creation while a transaction is
 * being saved. The roundtrip runner therefore executes this suite after Playwright 095 and before resuming at 100.
 *
 * <p>
 * {@code HoldCashaccountReplayRebuildTest} follows, because it books on a cash account of the same fixture and asserts
 * that the incremental hold-table replay and the full rebuild agree.
 * </p>
 *
 * <p>
 * {@code McpServerIntegrationTest} and {@code McpOAuthIntegrationTest} come last: the tools read the portfolios and
 * transactions created before.
 * </p>
 */
@Suite
@SelectClasses({ TradingDaysPlusResourceTest.class, TransactionResourceTest.class,
    HoldCashaccountReplayRebuildTest.class, McpServerIntegrationTest.class, McpOAuthIntegrationTest.class })
public class ResourceTestSuite_50 {

}
