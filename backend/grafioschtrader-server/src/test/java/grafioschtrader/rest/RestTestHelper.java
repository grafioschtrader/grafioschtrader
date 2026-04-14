package grafioschtrader.rest;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
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
import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Assetclass;
import weka.core.Debug.Random;

public class RestTestHelper {
  public static final String ADMIN = "admin";
  public static final String ALLEDIT = "alledit";
  public static final String USER = "user";
  public static final String LIMIT1 = "limit1";
  public static final String LIMIT2 = "limit2";

  public static final Random random = new Random();

  public static final String[] ALL_USERS = new String[] { ADMIN, ALLEDIT, USER, LIMIT1, LIMIT2 };
  public static final String[] LIMIT_USERS = new String[] { LIMIT1, LIMIT2 };

  public static UserRegister[] users = {
      new UserRegister("hg@hugograf.com", "A123abcd", ADMIN, "de-CH", -60, GlobalConstants.MC_CHF),
      new UserRegister("hugo.graf@grafiosch.com", "A123abcd", ALLEDIT, "de-CH", -60, GlobalConstants.MC_CHF),
      new UserRegister("hugo.graf@outlook.com", "A123abcd", USER, "de-CH", -60, GlobalConstants.MC_USD),
      new UserRegister("grafiosch@outlook.com", "A123abcd", LIMIT1, "de-CH", -60, GlobalConstants.MC_EUR),
      new UserRegister("hugo.graf@wirtschaftsfilz.ch", "A123abcd", LIMIT2, "de-CH", -60, GlobalConstants.MC_CHF) };

  public static String getRadomUser() {
    return ALL_USERS[random.nextInt(4) + 1];
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

    public UserRegister(final String email, final String password, final String nickname, final String localeStr,
        final Integer timezoneOffset, String currency) {
      this.email = email;
      this.password = password;
      this.nickname = nickname;
      this.localeStr = localeStr;
      this.timezoneOffset = timezoneOffset;
      this.currency = currency;
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
