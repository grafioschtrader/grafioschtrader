package grafiosch.rest;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;

import grafiosch.GrafioschApplication;

/**
 * The Spring test context of every integration test of this module: the integration host application, a random port and
 * the {@code test} profile.
 *
 * <p>
 * <b>The {@code test} profile is not optional.</b> Without it the application falls back to
 * {@code application.properties}, which points at the developer database {@code grafiosch}; the tests would then create
 * users and tenants in real data. {@code application-test.properties} redirects to {@code grafiosch_t} and applies the
 * portable Flyway baseline.
 *
 * <p>
 * It is a composed annotation because two classes need the identical block and neither can inherit it from the other:
 * {@link BaseIntegrationTest} is the parent of the ordinary resource tests, while {@link UserResourceTest} extends the
 * shared {@code AbstractUserResourceTest} from {@code grafiosch-server-base} instead.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(classes = GrafioschApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
public @interface GrafioschIntegrationTestContext {
}
