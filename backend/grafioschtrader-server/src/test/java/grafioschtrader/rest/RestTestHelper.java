package grafioschtrader.rest;

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

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.entities.ProposeChangeField;
import grafiosch.security.JwtTokenHandler;
import grafiosch.types.Language;
import grafioschtrader.entities.Assetclass;
import weka.core.Debug.Random;

public class RestTestHelper {

  private static final String USERS_CSV = "/testdata/users.csv";

  public static final String ADMIN = "admin";
  public static final String ALLEDIT = "alledit";
  public static final String USER = "user";
  public static final String LIMIT1 = "limit1";
  public static final String LIMIT2 = "limit2";

  public static final Random random = new Random();

  /** Every row from users.csv, including both integration ('i') and e2e ('e') tagged users. */
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
    try (InputStream is = RestTestHelper.class.getResourceAsStream(USERS_CSV);
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
      String line;
      while ((line = br.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        List<String> cols = parseCsvRow(line);
        // email, password, nickname, localeStr, timezoneOffset, currency, role, e2e
        rows.add(new UserRegister(cols.get(0), cols.get(1), cols.get(2), cols.get(3), Integer.valueOf(cols.get(4)),
            cols.get(5), cols.get(6), cols.get(7)));
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to load " + USERS_CSV, e);
    }
    return rows.toArray(new UserRegister[0]);
  }

  private static List<String> parseCsvRow(String line) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        inQuotes = !inQuotes;
        continue;
      }
      if (c == '|' && !inQuotes) {
        out.add(cur.toString());
        cur.setLength(0);
      } else {
        cur.append(c);
      }
    }
    out.add(cur.toString());
    return out;
  }

  public static String getRadomUser() {
    return ALL_USERS[random.nextInt(ALL_USERS.length - 1) + 1];
  }

  public static String createURLWithPort(String uri, int port) {
    return "http://localhost:" + port + uri;
  }

  /**
   * Compare the value of field of two objects of the same class. It is not a deep comparison and only fields of the
   * main object where there value is not null are compared.
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

  public static UserRegister getUserByNickname(String nickname) {
    return Arrays.stream(users).filter(user -> user.nickname.equals(nickname)).findFirst().get();
  }

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

  public static class UserRegister {
    public String email;
    public String password;
    public String nickname;
    public String localeStr;
    public Integer timezoneOffset;

    @JsonIgnore
    public String authToken;

    @JsonIgnore
    public Integer idUser;

    @JsonIgnore
    public String currency;

    @JsonIgnore
    public String role;

    @JsonIgnore
    public String e2e;

    public UserRegister(final String email, final String password, final String nickname, final String localeStr,
        final Integer timezoneOffset, String currency, String role, String e2e) {
      this.email = email;
      this.password = password;
      this.nickname = nickname;
      this.localeStr = localeStr;
      this.timezoneOffset = timezoneOffset;
      this.currency = currency;
      this.role = role;
      this.e2e = e2e;
    }
  }

  public static Assetclass getAssetclassBy(List<Assetclass> assetclasses, byte categoryType, String subCategoryDE,
      byte specialInvestmentInstrument) {
    return assetclasses.stream()
        .filter(a -> a.getCategoryType().getValue().equals(categoryType)
            && a.getSpecialInvestmentInstrument().getValue().equals(specialInvestmentInstrument)
            && a.getSubCategoryByLanguage(Language.GERMAN).equals(subCategoryDE))
        .findFirst().get();
  }

}
