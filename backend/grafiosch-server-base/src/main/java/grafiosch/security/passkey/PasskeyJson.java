package grafiosch.security.passkey;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey;
import org.springframework.stereotype.Component;

import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads and writes the WebAuthn JSON types with a mapper of their own that registers the Jackson 3 module of Spring
 * Security; the application's mapper does not know their mixins. A request body above 64 KiB is refused before it is
 * parsed.
 */
@Component
public class PasskeyJson {
  public static final int MAX_BODY_BYTES = 64 * 1024;

  private final JsonMapper mapper = JsonMapper.builder().addModule(new WebauthnJacksonModule()).build();
  private final JavaType assertionType = mapper.getTypeFactory().constructParametricType(PublicKeyCredential.class,
      AuthenticatorAssertionResponse.class);

  /** The answer to an options request: {@code { "challengeId": …, "publicKey": options }}. */
  public String options(String challengeId, Object options) {
    ObjectNode node = mapper.createObjectNode();
    node.put("challengeId", challengeId);
    node.set("publicKey", mapper.valueToTree(options));
    return mapper.writeValueAsString(node);
  }

  /** Reads a request body of at most {@link #MAX_BODY_BYTES}. */
  public JsonNode readBody(HttpServletRequest request) throws IOException {
    if (request.getContentLengthLong() > MAX_BODY_BYTES) {
      throw invalid();
    }
    try (InputStream in = request.getInputStream()) {
      byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
      if (body.length > MAX_BODY_BYTES) {
        throw invalid();
      }
      return mapper.readTree(body);
    } catch (JacksonException e) {
      throw invalid();
    }
  }

  /** The new credential of a registration together with its label. */
  public RelyingPartyPublicKey registration(JsonNode credential, String label) {
    ObjectNode node = mapper.createObjectNode();
    node.set("credential", credential);
    node.put("label", label);
    try {
      return mapper.treeToValue(node, RelyingPartyPublicKey.class);
    } catch (JacksonException | IllegalArgumentException e) {
      throw invalid();
    }
  }

  /** The assertion of a sign-in. */
  public PublicKeyCredential<AuthenticatorAssertionResponse> assertion(JsonNode credential) {
    try {
      return mapper.readerFor(assertionType).readValue(credential);
    } catch (JacksonException | IllegalArgumentException e) {
      throw invalid();
    }
  }

  static GeneralNotTranslatedWithArgumentsException invalid() {
    return new GeneralNotTranslatedWithArgumentsException("passkey.credential.invalid", null);
  }
}
