package grafioschtrader.rest;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Creates integration-owned bank-account transactions after Playwright spec 045 has initialized currency pairs.
 *
 * <p>
 * In particular, the USD/CHF transfer fixture must not trigger on-demand currency-pair creation while a transaction is
 * being saved. The roundtrip runner therefore executes this suite after Playwright 045 and before resuming at 050.
 *
 * <p>
 * {@code HoldCashaccountReplayRebuildTest} follows, because it books on a cash account of the same fixture and asserts
 * that the incremental hold-table replay and the full rebuild agree.
 * </p>
 */
@Suite
@SelectClasses({ TransactionResourceTest.class, HoldCashaccountReplayRebuildTest.class })
public class ResourceTestSuite_50 {

}
