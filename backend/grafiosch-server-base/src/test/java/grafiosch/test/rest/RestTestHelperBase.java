package grafiosch.test.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.entities.ProposeChangeField;
import grafiosch.security.JwtTokenHandler;

/**
 * Application independent part of the integration test fixture: it loads users from {@code testdata/users.json}
 * of the module under test, acquires a JWT for each of them and offers the small reflection helpers the resource tests
 * share.
 *
 * <p>
 * The JSON is looked up as the fixed classpath resource {@value #USERS_JSON}, so every application supplies its own
 * copy below its own {@code src/test/resources}. This artifact intentionally ships no {@code users.json} of its own,
 * because such a file would shadow the consumer's copy on the test class path.
 *
 * <p>
 * Applications extend this class only to add their own constants and domain helpers. The static members are inherited,
 * so {@code MyRestTestHelper.users} keeps resolving to the fields declared here.
 */
public class RestTestHelperBase {

  /** Classpath location of the fixture, identical in every consuming module. */
  private static final String USERS_JSON = "/testdata/users.json";

  public static final Random random = new Random();

  /** Every object from users.json, including both integration ('i') and browser ('e') tagged users. */
  public static final UserRegister[] allUsers;

  /** Integration-test subset of {@link #allUsers} (objects where e2e = 'i'). Used by every backend test. */
  public static UserRegister[] users;

  /** Nicknames of all users in {@link #users}, in fixture order. */
  public static final String[] ALL_USERS;

  /** Nicknames of integration-test users whose role is LIMITEDIT. Driven by the JSON fixture. */
  public static final String[] LIMIT_USERS;

  static {
    allUsers = loadUsersFromJson();
    users = Arrays.stream(allUsers).filter(u -> "i".equals(u.e2e)).toArray(UserRegister[]::new);
    ALL_USERS = Arrays.stream(users).map(u -> u.nickname).toArray(String[]::new);
    LIMIT_USERS = Arrays.stream(users).filter(u -> "LIMITEDIT".equals(u.role)).map(u -> u.nickname)
        .toArray(String[]::new);
  }

  private static UserRegister[] loadUsersFromJson() {
    List<UserRegister> loadedUsers = new ArrayList<>();
    try (InputStream is = RestTestHelperBase.class.getResourceAsStream(USERS_JSON)) {
      if (is == null) {
        throw new IllegalStateException("Missing test fixture " + USERS_JSON + " on the test class path. Every module "
            + "using RestTestHelperBase must provide its own src/test/resources" + USERS_JSON);
      }
      JsonNode root = new ObjectMapper().readTree(is);
      if (!root.isArray()) {
        throw new IllegalStateException("Expected a JSON array in " + USERS_JSON);
      }
      for (JsonNode user : root) {
        loadedUsers.add(new UserRegister(user.required("email").asText(), user.required("password").asText(),
            user.required("nickname").asText(), user.required("localeStr").asText(),
            user.required("timezoneOffset").asInt(), user.required("currency").asText(),
            user.required("role").asText(), user.required("e2e").asText()));
      }
      if (loadedUsers.isEmpty()) {
        throw new IllegalStateException("No users found in " + USERS_JSON);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to load " + USERS_JSON, e);
    }
    return loadedUsers.toArray(new UserRegister[0]);
  }

  /**
   * Returns the nickname of a random integration-test user, never the first one, which is the administrator by
   * convention.
   *
   * @return a nickname from {@link #ALL_USERS}
   */
  public static String getRadomUser() {
    return ALL_USERS[random.nextInt(ALL_USERS.length - 1) + 1];
  }

  /**
   * Builds an absolute URL for the randomly assigned test port.
   *
   * @param uri  the API path, starting with a slash
   * @param port the port of the running test server
   * @return the absolute URL
   */
  public static String createURLWithPort(String uri, int port) {
    return "http://localhost:" + port + uri;
  }

  /**
   * Looks a fixture user up by its nickname.
   *
   * @param nickname the nickname as written in users.json
   * @return the matching object of {@link #users}
   * @throws java.util.NoSuchElementException when no integration-test user carries that nickname
   */
  public static UserRegister getUserByNickname(String nickname) {
    return Arrays.stream(users).filter(user -> user.nickname.equals(nickname)).findFirst().get();
  }

  /**
   * Logs every integration-test user in once and stores the JWT and the generated user id on the fixture row. Repeated
   * calls are cheap: the method returns immediately once the first user carries a token, which is why every resource
   * test can call it from its own {@code @BeforeAll} without coordinating with the others.
   *
   * @param restTestClient  the unauthenticated client bound to the running test server
   * @param jwtTokenHandler used to read the user id out of the issued token
   */
  public static void inizializeUserTokens(RestTestClient restTestClient, JwtTokenHandler jwtTokenHandler) {
    if (users[0].authToken == null) {
      for (UserRegister user : users) {
        EntityExchangeResult<String> result = restTestClient.post()
            .uri("/api/login")
            .body(user)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .returnResult();
        HttpHeaders headers = result.getResponseHeaders();
        user.authToken = headers.getFirst("x-auth-token");
        assertThat(user.authToken).isNotNull();
        user.idUser = jwtTokenHandler.getUserId(user.authToken);
      }
    }
  }

  /**
   * Compares the fields of two objects of the same class. It is not a deep comparison; only fields of the main object
   * whose value is not null are compared. Used by the propose-change tests to build the expected change set.
   *
   * @param mainEntity the object holding the desired values
   * @param entity     the object to compare against
   * @return one {@link ProposeChangeField} per differing, non-null property of {@code mainEntity}
   * @throws IllegalAccessException    when a property accessor is not reachable
   * @throws InvocationTargetException when a property accessor throws
   * @throws NoSuchMethodException     when a property descriptor cannot be resolved
   */
  public static List<ProposeChangeField> getDiffPropertiesOfTwoObjects(Object mainEntity, Object entity)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    List<ProposeChangeField> proposeChangeFieldList = new ArrayList<>();

    List<Field> fields = FieldUtils.getAllFieldsList(mainEntity.getClass());
    for (Field field : fields) {
      String name = field.getName();

      PropertyDescriptor pd = PropertyUtils.getPropertyDescriptor(mainEntity.getClass(), name);

      if (pd != null && pd.getReadMethod() != null && pd.getWriteMethod() != null) {
        Object mainValue = PropertyUtils.getProperty(mainEntity, name);
        if (mainValue != null) {
          Object value = PropertyUtils.getProperty(entity, name);

          if (!Objects.equals(mainValue, value)) {
            proposeChangeFieldList
                .add(new ProposeChangeField(name, SerializationUtils.serialize((Serializable) mainValue)));
          }
        }
      }
    }
    return proposeChangeFieldList;
  }
}
