package grafioschtrader.rest;

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
import org.springframework.test.context.TestPropertySource;

import grafioschtrader.test.start.GTforTest;

/**
 * The Spring test context of every Grafioschtrader integration test: the application under test, a random port, the
 * {@code test} profile and the Flyway settings that bootstrap {@code grafioschtrader_t}.
 *
 * <p>
 * It exists as a composed annotation because two classes need the identical block and neither can inherit it from the
 * other: {@link BaseIntegrationTest} is the parent of the ordinary resource tests, while {@code UserResourceTest}
 * extends the shared {@code AbstractUserResourceTest} from {@code grafiosch-server-base} instead.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(classes = GTforTest.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/test",
    "spring.flyway.baseline-on-migrate=true",
    "spring.flyway.clean-disabled=false"
})
public @interface GTIntegrationTestContext {
}
