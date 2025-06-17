package grafiosch.entities;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

import com.fasterxml.jackson.annotation.JsonSetter;

import grafiosch.types.ISubscriptionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = """
    This allows API keys to be saved for each provider. Most providers offer different access and performance levels.
    Of course, the application must know the corresponding API and therefore also have an implementation to use it.
    """)
@Entity
@Table(name = ConnectorApiKey.TABNAME)
public class ConnectorApiKey extends BaseID<String> {

  public static final String TABNAME = "connector_apikey";
  public static final EnumRegistry<Short, ISubscriptionType> SUBSCRIPTION_REGISTRY = new EnumRegistry<>();

  private static StringEncryptor stringEncryptor = stringEncryptor();

  @Schema(description = "The name of the provider can be used as a key. The possible providers are specified by the implementation in the application.")
  @Id
  @Basic(optional = false)
  @Column(name = "id_provider")
  private String idProvider;

  @Schema(description = "The API key is stored in encrypted form in the persistence.")
  @NotBlank
  @Basic(optional = false)
  @Size(min = 10, max = 255)
  @Column(name = "api_key")
  private String apiKey;

  @Schema(description = "Storage of the access and power levels.")
  @Basic(optional = false)
  @Column(name = "subscription_type")
  private Short subscriptionType;

  /**
   * Transient field for caching decrypted API key.
   * 
   * <p>
   * This field temporarily stores the decrypted API key to avoid repeated decryption operations. It is not persisted to
   * the database and is cleared when the API key is updated.
   * </p>
   */
  @Transient
  private String apiKeyDecrypt;

  public String getIdProvider() {
    return idProvider;
  }

  public void setIdProvider(String idProvider) {
    this.idProvider = idProvider;
  }

  /**
   * Gets the decrypted API key for use by connector services.
   * 
   * <p>
   * This method automatically decrypts the stored API key on first access and caches the result for subsequent calls.
   * The decryption is performed using the configured string encryptor with the application's encryption key.
   * </p>
   * 
   * <p>
   * <strong>Performance Optimization:</strong>
   * </p>
   * <p>
   * The decrypted key is cached in the apiKeyDecrypt field to avoid repeated decryption operations during the entity's
   * lifecycle.
   * </p>
   * 
   * @return the decrypted API key ready for use in external service calls
   */
  public String getApiKey() {
    if (apiKeyDecrypt == null) {
      apiKeyDecrypt = stringEncryptor.decrypt(apiKey);
    }
    return apiKeyDecrypt;
  }

  /**
   * Sets the API key with automatic encryption.
   * 
   * <p>
   * This method automatically encrypts the provided API key before storing it in the database. The encryption ensures
   * that sensitive credentials are never stored in plain text, protecting against data breaches.
   * </p>
   * 
   * <p>
   * <strong>Security Behavior:</strong>
   * </p>
   * <ul>
   * <li>Clears any cached decrypted key to force re-decryption</li>
   * <li>Encrypts the new API key using the configured encryptor</li>
   * <li>Stores only the encrypted form in the database field</li>
   * </ul>
   * 
   * @param apiKey the plain text API key to encrypt and store
   */
  public void setApiKey(String apiKey) {
    apiKeyDecrypt = null;
    this.apiKey = stringEncryptor.encrypt(apiKey);
  }

  public ISubscriptionType getSubscriptionType() {
    return SUBSCRIPTION_REGISTRY.getTypeByValue(subscriptionType);
  }

  public void setSubscriptionType(ISubscriptionType subscriptionType) {
    this.subscriptionType = subscriptionType.getValue();
  }

  /**
   * Sets the subscription type from a string name for JSON deserialization.
   * 
   * <p>
   * This method enables JSON-based configuration by accepting subscription type names as strings and converting them to
   * the appropriate enum values. It uses the subscription registry to perform name-to-enum resolution.
   * </p>
   * 
   * <p>
   * <strong>Validation:</strong>
   * </p>
   * <p>
   * The method validates that the provided subscription type name exists in the registry and throws an
   * IllegalArgumentException for unknown types.
   * </p>
   * 
   * @param subscriptionTypeName the string name of the subscription type
   * @throws IllegalArgumentException if the subscription type name is not recognized
   */
  @JsonSetter("subscriptionType")
  public void setSubscriptionType(String subscriptionTypeName) {
    ISubscriptionType subscriptionType = SUBSCRIPTION_REGISTRY.getTypeByName(subscriptionTypeName);
    if (subscriptionType == null) {
      throw new IllegalArgumentException("Unknown subscription type: " + subscriptionTypeName);
    }
    this.subscriptionType = subscriptionType.getValue();
  }

  @Override
  public String toString() {
    return "ConnectorApiKey [idProvider=" + idProvider + ", apiKey=" + this.getApiKey() + ", subscriptionType="
        + subscriptionType + "]";
  }

  /**
   * Creates and configures the string encryptor for API key encryption.
   * 
   * <p>
   * This method initializes a pooled PBE (Password-Based Encryption) string encryptor with secure configuration
   * settings. The encryptor is configured with industry-standard algorithms and security parameters.
   * </p>
   * 
   * <p>
   * <strong>Encryption Configuration:</strong>
   * </p>
   * <ul>
   * <li><strong>Algorithm:</strong> PBEWITHHMACSHA512ANDAES_256 for strong encryption</li>
   * <li><strong>Key Derivation:</strong> 1000 iterations for enhanced security</li>
   * <li><strong>Salt Generation:</strong> Random salt generator for unique encryption</li>
   * <li><strong>IV Generation:</strong> Random initialization vector for each encryption</li>
   * <li><strong>Output Format:</strong> Base64 encoding for database compatibility</li>
   * </ul>
   * 
   * <p>
   * <strong>Environment Requirements:</strong>
   * </p>
   * <p>
   * The encryption password must be provided via the JASYPT_ENCRYPTOR_PASSWORD environment variable. This ensures that
   * the encryption key is not hardcoded in the application and can be managed securely in deployment environments.
   * </p>
   * 
   * @return configured StringEncryptor instance ready for encryption operations
   */
  public static StringEncryptor stringEncryptor() {
    PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
    SimpleStringPBEConfig config = new SimpleStringPBEConfig();
    config.setPassword(System.getenv("JASYPT_ENCRYPTOR_PASSWORD"));
    config.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
    config.setKeyObtentionIterations("1000");
    config.setPoolSize("1");
    config.setProviderName("SunJCE");
    config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
    config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator");
    config.setStringOutputType("base64");
    encryptor.setConfig(config);
    return encryptor;
  }

  @Override
  public String getId() {
    return idProvider;
  }

}
