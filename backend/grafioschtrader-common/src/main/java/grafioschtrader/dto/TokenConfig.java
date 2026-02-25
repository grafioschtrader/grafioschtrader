package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * YAML-deserialized configuration for automatic token acquisition. Supports a seed + login + optional refresh flow,
 * e.g. SAML SSO where a seed page is scraped for a ticket, which is then POSTed to obtain a JWT.
 */
@Schema(description = """
    Configures automatic JWT token acquisition for APIs requiring SAML/SSO or similar auth flows. \
    When set on a GenericConnectorDef, the connector auto-acquires and refreshes tokens instead of \
    requiring a static API key.""")
public class TokenConfig {

  @Schema(description = "Seed step: GET a page and extract a value (e.g. SAML ticket) via regex")
  private SeedConfig seed;

  @Schema(description = "Login step: POST the extracted seed value to obtain a JWT and optional session ID")
  private LoginConfig login;

  @Schema(description = "Refresh step (optional): POST with cached session ID to obtain a fresh JWT without re-seeding")
  private RefreshConfig refresh;

  @Schema(description = "Token lifetime in seconds. Use a value slightly less than actual expiry to avoid races.")
  private int ttlSeconds = 300;

  public SeedConfig getSeed() {
    return seed;
  }

  public void setSeed(SeedConfig seed) {
    this.seed = seed;
  }

  public LoginConfig getLogin() {
    return login;
  }

  public void setLogin(LoginConfig login) {
    this.login = login;
  }

  public RefreshConfig getRefresh() {
    return refresh;
  }

  public void setRefresh(RefreshConfig refresh) {
    this.refresh = refresh;
  }

  public int getTtlSeconds() {
    return ttlSeconds;
  }

  public void setTtlSeconds(int ttlSeconds) {
    this.ttlSeconds = ttlSeconds;
  }

  public static class SeedConfig {
    @Schema(description = "URL to GET for the seed HTML page")
    private String url;

    @Schema(description = "Regex with capture group(1) to extract a value (e.g. SAML ticket) from the HTML")
    private String regex;

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getRegex() {
      return regex;
    }

    public void setRegex(String regex) {
      this.regex = regex;
    }
  }

  public static class LoginConfig {
    @Schema(description = "Full URL to POST for initial login")
    private String url;

    @Schema(description = "POST body template. The placeholder {seedValue} is replaced with the extracted seed value.")
    private String body;

    @Schema(description = """
        Content-Type for the login POST request. Defaults to 'application/json'. \
        Set to 'application/x-www-form-urlencoded' for form-encoded bodies (e.g. SAML SSO flows).""")
    private String contentType = "application/json";

    @Schema(description = """
        When true, the extracted seed value is Base64-encoded before template substitution. \
        Required for SAML flows where the seed is raw XML that must be Base64-encoded for transport.""")
    private boolean base64EncodeSeed;

    @Schema(description = "JSON dot-path to the JWT token in the login response")
    private String jwtPath;

    @Schema(description = "JSON dot-path to the session ID in the login response (needed for refresh)")
    private String sessionPath;

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getBody() {
      return body;
    }

    public void setBody(String body) {
      this.body = body;
    }

    public String getContentType() {
      return contentType;
    }

    public void setContentType(String contentType) {
      this.contentType = contentType;
    }

    public boolean isBase64EncodeSeed() {
      return base64EncodeSeed;
    }

    public void setBase64EncodeSeed(boolean base64EncodeSeed) {
      this.base64EncodeSeed = base64EncodeSeed;
    }

    public String getJwtPath() {
      return jwtPath;
    }

    public void setJwtPath(String jwtPath) {
      this.jwtPath = jwtPath;
    }

    public String getSessionPath() {
      return sessionPath;
    }

    public void setSessionPath(String sessionPath) {
      this.sessionPath = sessionPath;
    }
  }

  public static class RefreshConfig {
    @Schema(description = "URL to POST for token refresh")
    private String url;

    @Schema(description = "Header name for the session ID in the refresh request")
    private String sidHeader;

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getSidHeader() {
      return sidHeader;
    }

    public void setSidHeader(String sidHeader) {
      this.sidHeader = sidHeader;
    }
  }
}
