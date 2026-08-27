package grafiosch.gtnet.peer;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/** Client-only protocol suite for the two backend processes owned by {@code e2eTest --gtnet}. */
@Suite
@SelectClasses({ GTNetPeerProtocolTest.class, GTNetPeerAuthenticatedProtocolTest.class, GTNetPeerPingMetadataTest.class,
    GTNetPeerHandshakeWireTest.class, GTNetPeerEnvelopeLimitTest.class, GTNetPeerIdempotencyTest.class,
    GTNetPeerResponseRuleTest.class, GTNetPeerDailyRequestLimitTest.class, GTNetPeerFutureDeliveryTest.class })
public class GTNetLibraryPeerTestSuite {
}
