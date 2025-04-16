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

@Schema(description="""
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

  @Schema(description = "Speicherung der Zugangs- bzw. Leistungsstufen.")
  @Basic(optional = false)
  @Column(name = "subscription_type")
  private Short subscriptionType;

  @Transient
  private String apiKeyDecrypt;

  public String getIdProvider() {
    return idProvider;
  }

  public void setIdProvider(String idProvider) {
    this.idProvider = idProvider;
  }

  public String getApiKey() {
    if (apiKeyDecrypt == null) {
      apiKeyDecrypt = stringEncryptor.decrypt(apiKey);
    }
    return apiKeyDecrypt;
  }

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

  // Change the setter to accept a String from the JSON payload.
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
