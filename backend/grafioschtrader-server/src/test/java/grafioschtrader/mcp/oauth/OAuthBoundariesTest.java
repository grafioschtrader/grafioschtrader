package grafioschtrader.mcp.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafiosch.types.AccessTokenScope;
import grafioschtrader.mcp.oauth.OAuthRequestStore.PendingRequest;
import grafioschtrader.udffill.http.UDFFillHttp;

/**
 * Boundaries of the OAuth authorization that the HTTP flow does not reach: the bounded request store, the refusal of
 * private addresses for metadata documents, the acceptance rules of a document and PKCE.
 */
class OAuthBoundariesTest {

  @Test
  @DisplayName("A full request store refuses new requests instead of evicting pending consents")
  void fullStoreRefuses() {
    OAuthRequestStore store = new OAuthRequestStore();
    String first = null;
    for (int i = 0; i < OAuthRequestStore.MAX_PENDING_REQUESTS; i++) {
      String id = store.putRequest(pending());
      first = first == null ? id : first;
      assertThat(id).isNotNull();
    }
    assertThat(store.putRequest(pending())).isNull();
    assertThat(store.peekRequest(first)).isNotNull();
    assertThat(store.pendingCount()).isEqualTo(OAuthRequestStore.MAX_PENDING_REQUESTS);
    assertThat(store.takeRequest(first)).isNotNull();
    assertThat(store.takeRequest(first)).as("a consent can be given once").isNull();
  }

  @Test
  @DisplayName("A metadata document on a private or loopback address is never fetched")
  void privateAddressRefused() {
    ClientMetadataFetcher fetcher = new ClientMetadataFetcher(new UDFFillHttp(Optional.empty()));
    for (String url : new String[] { "https://127.0.0.1/meta.json", "https://10.1.2.3/meta.json",
        "https://[::1]/meta.json", "http://client.example/meta.json" }) {
      assertThatThrownBy(() -> fetcher.fetch(url)).as(url).isInstanceOf(Exception.class);
    }
  }

  @Test
  @DisplayName("A document must name its own URL, ask for no secret and list only https or loopback redirects")
  void documentRules() {
    String id = "https://client.example/meta.json";
    assertThat(ClientMetadataService.parse(id, """
        {"client_id":"%s","redirect_uris":["https://client.example/cb","http://127.0.0.1:8765/cb"]}""".formatted(id))
        .clientName()).isEqualTo("client.example");
    assertThatThrownBy(() -> ClientMetadataService.parse(id, """
        {"client_id":"https://other.example/meta.json","redirect_uris":["https://client.example/cb"]}"""))
        .hasMessageContaining("client_id");
    assertThatThrownBy(() -> ClientMetadataService.parse(id,
        """
            {"client_id":"%s","token_endpoint_auth_method":"client_secret_basic","redirect_uris":["https://c.example/cb"]}"""
            .formatted(id))).hasMessageContaining("public clients");
    assertThatThrownBy(() -> ClientMetadataService.parse(id, """
        {"client_id":"%s","redirect_uris":["http://client.example/cb"]}""".formatted(id)))
        .hasMessageContaining("redirect");
    assertThatThrownBy(() -> ClientMetadataService.parse(id, """
        {"client_id":"%s","token_endpoint_auth_methods_supported":["private_key_jwt"],
        "redirect_uris":["https://c.example/cb"]}""".formatted(id))).hasMessageContaining("public clients");
    assertThat(ClientMetadataService.isAcceptableClientIdUrl("https://client.example/")).isFalse();
    assertThat(ClientMetadataService.isAcceptableClientIdUrl("http://client.example/meta.json")).isFalse();
  }

  @Test
  @DisplayName("ChatGPT's document is accepted: it prefers private_key_jwt but also offers none")
  void chatGptDocument() {
    // Copied from https://chatgpt.com/oauth/client.json on 2026-10-20
    String id = "https://chatgpt.com/oauth/client.json";
    ClientMetadataService.ClientMetadata client = ClientMetadataService.parse(id,
        """
            {"client_id":"https://chatgpt.com/oauth/client.json","client_uri":"https://chatgpt.com/",
            "redirect_uris":["https://chatgpt.com/connector_platform_oauth_redirect"],
            "token_endpoint_auth_method":"private_key_jwt",
            "token_endpoint_auth_methods_supported":["none","private_key_jwt"],
            "grant_types":["authorization_code","refresh_token"],"response_types":["code"],"client_name":"ChatGPT",
            "logo_uri":"https://persistent.oaistatic.com/sonic/misc/openai-logo.png",
            "token_endpoint_auth_signing_alg":"RS256",
            "jwks_uri":"https://chatgpt.com/oauth/jwks.json"}""");
    assertThat(client.clientName()).isEqualTo("ChatGPT");
    assertThat(client.redirectUris()).containsExactly("https://chatgpt.com/connector_platform_oauth_redirect");
  }

  @Test
  @DisplayName("PKCE S256 accepts only the matching verifier")
  void pkce() {
    String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    // Example of RFC 7636, appendix B
    String challenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    assertThat(Pkce.isValidChallenge(challenge)).isTrue();
    assertThat(Pkce.verify(verifier, challenge)).isTrue();
    assertThat(Pkce.verify(verifier + "x", challenge)).isFalse();
    assertThat(Pkce.verify("short", challenge)).isFalse();
  }

  private static PendingRequest pending() {
    return new PendingRequest("https://client.example/meta.json", "Client", "client.example",
        "https://client.example/cb", "s", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", AccessTokenScope.READ,
        "https://host.example/api/mcp", System.nanoTime());
  }
}
