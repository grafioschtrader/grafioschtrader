package grafioschtrader.gtnet.peer;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Client-only payload suite for the two Grafioschtrader processes owned by {@code e2eTest --gtnet-app}.
 *
 * It starts no Spring context of its own: the peers are already running, and every assertion goes over HTTP against
 * their REST API, exactly as the browser specs do. That is also why it joins none of the numbered
 * {@code ResourceTestSuite_*} phases - those own the single-instance database and would fight over the GTNet identity.
 *
 * The order is by prerequisite: the grant class runs first because it is the only one that needs a peer without an
 * accepted exchange, and the exchange classes produce the traffic the supplier-detail class inspects.
 */
@Suite
@SelectClasses({ GTNetAppGrantEnforcementTest.class, GTNetAppSecurityLookupTest.class,
    GTNetAppLastpriceExchangeTest.class, GTNetAppHistoryquoteExchangeTest.class, GTNetAppExchangeSyncTest.class,
    GTNetAppSupplierDetailTest.class })
public class GTNetApplicationPeerTestSuite {
}
