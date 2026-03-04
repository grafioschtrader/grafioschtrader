package grafiosch.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

/**
 * Configuration for Spring Security OAuth2 JWT encoding and decoding.
 *
 * <p>
 * Provides {@link SecretKey}, {@link JwtEncoder} and {@link JwtDecoder} beans backed by Nimbus JOSE, using the
 * application's HMAC-SHA256 secret key configured via {@code gt.jwt.secret}. These beans replace the former JJWT
 * library after the Spring Boot 4 migration.
 * </p>
 */
@Configuration
public class JwtConfig {

  @Bean
  public SecretKey jwtSecretKey(@Value("${gt.jwt.secret}") String secret) {
    return new SecretKeySpec(secret.getBytes(), "HmacSHA256");
  }

  @Bean
  public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
    OctetSequenceKey jwk = new OctetSequenceKey.Builder(jwtSecretKey)
        .algorithm(JWSAlgorithm.HS256).build();
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
  }

  @Bean
  public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
    return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
        .macAlgorithm(MacAlgorithm.HS256).build();
  }
}
