package grafiosch.test.rest;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * One object of the {@code testdata/users.json} fixture, and at the same time the JSON body posted to both
 * {@code POST /api/user} (registration) and {@code POST /api/login}. Only the five serialized fields are part of those
 * request bodies; everything else is filled in by the test run and therefore excluded from serialization.
 *
 * <p>
 * The class is shared by every application built on {@code grafiosch-server-base}. It is deliberately a plain
 * field-access POJO rather than a JavaBean, because that is what {@code RestTestClient.body(...)} serializes and what
 * the existing test code expects.
 */
public class UserRegister {

  /** Login name and recipient of the verification mail. */
  public String email;

  /** Plain text password, sent on registration and on every login. */
  public String password;

  /** Unique display name; also the key by which tests look a user up. */
  public String nickname;

  /** Locale in {@code language-COUNTRY} form, e.g. {@code de-CH}. */
  public String localeStr;

  /** Offset in minutes as the browser would report it, e.g. {@code -60}. */
  public Integer timezoneOffset;

  /** JWT of this user, set by {@link RestTestHelperBase#inizializeUserTokens}. */
  @JsonIgnore
  public String authToken;

  /** Primary key of the created user, derived from {@link #authToken}. */
  @JsonIgnore
  public Integer idUser;

  /** Tenant currency used when the concrete application creates the tenant. */
  @JsonIgnore
  public String currency;

  /** Role name without the {@code ROLE_} prefix, e.g. {@code ADMIN}, {@code ALLEDIT}, {@code LIMITEDIT}. */
  @JsonIgnore
  public String role;

  /**
   * Consumer tag partitioning the fixture: {@code i} for the JUnit resource suite, {@code e} (and application specific
   * variants such as {@code ec} / {@code er}) for the browser suite.
   */
  @JsonIgnore
  public String e2e;

  /**
   * Creates a fixture user from one JSON object.
   *
   * @param email          login name and verification mail recipient
   * @param password       plain text password
   * @param nickname       unique display name, also the test lookup key
   * @param localeStr      locale in {@code language-COUNTRY} form
   * @param timezoneOffset browser style timezone offset in minutes
   * @param currency       tenant currency, may be null when the application has no tenant currency
   * @param role           role name without the {@code ROLE_} prefix
   * @param e2e            consumer tag, {@code i} for JUnit or {@code e}-family for the browser suite
   */
  public UserRegister(final String email, final String password, final String nickname, final String localeStr,
      final Integer timezoneOffset, final String currency, final String role, final String e2e) {
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
