package grafiosch.rest;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

import grafiosch.dashboard.DashboardPersistenceTest;

/**
 * The integration suite of the reusable Grafiosch libraries, the counterpart of Grafioschtrader's
 * {@code ResourceTestSuite_1}/{@code ResourceTestSuite_25}/{@code ResourceTestSuite_50} phases.
 *
 * <p>
 * The order of {@code @SelectClasses} is significant: the suite builds a database state step by step.
 * {@link UserResourceTest} has to run first because it registers the {@code e2e='i'} users of
 * {@code testdata/users.json} that every following class authenticates as, and {@link EntityLimitResourceTest} raises
 * the daily CUD limit of the limited users before they change anything.
 *
 * <p>
 * {@link TenantContextAuthorizationTest} runs last. It issues and revokes {@code tenant_access} grants, and keeping
 * that at the end means no earlier class can observe a tenant context it did not set up itself.
 *
 * <p>
 * Run it on its own with {@code mvn test -pl grafiosch-test-integration -Dtest=ResourceTestSuite}; the browser suite
 * below {@code frontend/e2e/lib} expects the users it creates.
 */
@Suite
@SelectClasses({ UserResourceTest.class, EntityLimitResourceTest.class, GTNetAuthorizationTest.class,
    GTNetDeleteResourceTest.class, MailSendRecvResourceTest.class, MailSettingForwardResourceTest.class,
    TaskDataChangeResourceTest.class, DashboardPersistenceTest.class,
    TenantContextAuthorizationTest.class })
public class ResourceTestSuite {

}
