package grafiosch.rest;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * The integration suite of the reusable Grafiosch libraries, the counterpart of Grafioschtrader's
 * {@code ResoureTestSuite}.
 *
 * <p>
 * The order of {@code @SelectClasses} is significant: the suite builds a database state step by step.
 * {@link UserResourceTest} has to run first because it registers the {@code e2e='i'} users of
 * {@code testdata/users.csv} that every following class authenticates as, and
 * {@link UserEntityChangeLimitResourceTest} raises the daily CUD limit of the limited users before they change
 * anything.
 *
 * <p>
 * Run it on its own with {@code mvn test -pl grafiosch-test-integration -Dtest=ResourceTestSuite}; the browser suite
 * below {@code frontend/e2e/lib} expects the users it creates.
 */
@Suite
@SelectClasses({ UserResourceTest.class, UserEntityChangeLimitResourceTest.class, MailSendRecvResourceTest.class,
    MailSettingForwardResourceTest.class, TaskDataChangeResourceTest.class })
public class ResourceTestSuite {

}
