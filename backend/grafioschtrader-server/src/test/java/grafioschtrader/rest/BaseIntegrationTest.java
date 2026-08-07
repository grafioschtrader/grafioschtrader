package grafioschtrader.rest;

import grafiosch.test.rest.BaseIntegrationTestSupport;

/**
 * Base class for Grafioschtrader integration tests. The infrastructure — the in-process GreenMail SMTP server, the
 * auto-configured {@code RestTestClient}, the random port and {@code authenticatedClient(nickname)} — comes from
 * {@link BaseIntegrationTestSupport} in {@code grafiosch-server-base}; this class only binds it to the Grafioschtrader
 * application context via {@link GTIntegrationTestContext}.
 */
@GTIntegrationTestContext
public abstract class BaseIntegrationTest extends BaseIntegrationTestSupport {

}
