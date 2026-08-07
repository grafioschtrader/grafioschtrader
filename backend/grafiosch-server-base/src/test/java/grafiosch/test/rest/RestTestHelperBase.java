package grafiosch.test.rest;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.beans.PropertyDescriptor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
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

import grafiosch.entities.ProposeChangeField;
import grafiosch.security.JwtTokenHandler;

/**
 * Application independent part of the integration test fixture: it loads the users from the {@code testdata/users.csv}
 * of the module under test, acquires a JWT for each of them and offers the small reflection helpers the resource tests
 * share.
 *
 * <p>
 * The CSV is looked up as the fixed classpath resource {@value #USERS_CSV}, so every application supplies its own copy
 * below its own {@code src/test/resources}. This artifact intentionally ships no {@code users.csv} of its own, because
 * such a file would shadow the consumer's copy on the test class path.
 *
 * <p>
 * Applications extend this class only to add their own constants and domain helpers. The static members are inherited,
 * so {@code MyRestTestHelper.users} keeps resolving to the fields declared here.
 */
public class RestTestHelperBase {

  /** Classpath location of the fixture, identical in every consuming module. */
  private static final String USERS_CSV = "/testdata/users.csv";

  /** Column separator of the fixture files; a {@code |} inside double quotes is not a separator. */
  private static final char CSV_DELIMITER = '|';

  public static final Random random = new Random();

  /** Every row from users.csv, including both integration ('i') and browser ('e') tagged users. */
  public static final UserRegister[] allCsvUsers;

  /** Integration-test subset of {@link #allCsvUsers} (rows where e2e = 'i'). Used by every backend test. */
  public static UserRegister[] users;

  /** Nicknames of all users in {@link #users}, in CSV order. */
  public static final String[] ALL_USERS;

  /** Nicknames of integration-test users whose role is LIMITEDIT. Driven by the CSV. */
  public static final String[] LIMIT_USERS;

  static {
    allCsvUsers = loadUsersFromCsv();
    users = Arrays.stream(allCsvUsers).filter(u -> "i".equals(u.e2e)).toArray(UserRegister[]::new);
    ALL_USERS = Arrays.stream(users).map(u -> u.nickname).toArray(String[]::new);
    LIMIT_USERS = Arrays.stream(users).filter(u -> "LIMITEDIT".equals(u.role)).map(u -> u.nickname)
        .toArray(String[]::new);
  }

  private static UserRegister[] loadUsersFromCsv() {
    List<UserRegister> rows = new ArrayList<>();
    try (InputStream is = RestTestHelperBase.class.getResourceAsStream(USERS_CSV)) {
      if (is == null) {
        throw new IllegalStateException("Missing test fixture " + USERS_CSV + " on the test class path. Every module "
            + "using RestTestHelperBase must provide its own src/test/resources" + USERS_CSV);
      }
      try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
        String line;
        boolean header = true;
        while ((line = br.readLine()) != null) {
          if (line.isBlank()) {
            continue;
          }
          if (header) {
            header = false;
            continue;
          }
          List<String> cols = parseCsvRow(line);
          // email, password, nickname, localeStr, timezoneOffset, currency, role, e2e
          rows.add(new UserRegister(cols.get(0), cols.get(1), cols.get(2), cols.get(3), Integer.valueOf(cols.get(4)),
              cols.get(5), cols.get(6), cols.get(7)));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to load " + USERS_CSV, e);
    }
    return rows.toArray(new UserRegister[0]);
  }

  /**
   * Splits one fixture line into its fields, honouring double quotes so a delimiter inside a quoted section is kept.
   * The TypeScript counterpart in the browser suite implements exactly the same rules.
   *
   * @param line one non-empty, non-header line of a pipe separated fixture file
   * @return the field values in column order, quotes removed
   */
  public static List<String> parseCsvRow(String line) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        inQuotes = !inQuotes;
        continue;
      }
      if (c == CSV_DELIMITER && !inQuotes) {
        out.add(cur.toString());
        cur.setLength(0);
      } else {
        cur.append(c);
      }
    }
    out.add(cur.toString());
    return out;
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
   * @param nickname the nickname as written in users.csv
   * @return the matching row of {@link #users}
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
        assertThat(user.authToken, is(not(nullValue())));
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
