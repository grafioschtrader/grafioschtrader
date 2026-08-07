package grafiosch.rest;

import grafiosch.test.rest.BaseIntegrationTestSupport;

/**
 * Base class for the integration tests of the reusable Grafiosch libraries. The infrastructure — the in-process
 * GreenMail SMTP server, the auto-configured {@code RestTestClient}, the random port and
 * {@code authenticatedClient(nickname)} — comes from {@link BaseIntegrationTestSupport} in
 * {@code grafiosch-server-base}; this class only binds it to the integration host application via
 * {@link GrafioschIntegrationTestContext}.
 */
@GrafioschIntegrationTestContext
public abstract class BaseIntegrationTest extends BaseIntegrationTestSupport {

}
