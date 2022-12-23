package grafioschtrader.entities;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

import grafioschtrader.types.SubscriptionType;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


@Entity
@Table(name = ConnectorApiKey.TABNAME)
public class ConnectorApiKey {

  public static final String TABNAME = "connector_apikey";
  private static StringEncryptor stringEncryptor =  stringEncryptor();
    
  
  @Id
  @Basic(optional = false)
  @Column(name = "id_provider")
  private String idProvider;

  @NotBlank
  @Basic(optional = false)
  @Size(min = 10, max = 255)
  @Column(name = "api_key")
  private String apiKey;
  
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
    if(apiKeyDecrypt == null) {
      apiKeyDecrypt = stringEncryptor.decrypt(apiKey);
    }
    return apiKeyDecrypt;
  }

  public void setApiKey(String apiKey) {
    apiKeyDecrypt = null;
    this.apiKey = stringEncryptor.encrypt(apiKey);
  }

  public SubscriptionType getSubscriptionType() {
    return SubscriptionType.getTaskTypeByValue(subscriptionType);
  }

  public void setSubscriptionType(SubscriptionType subscriptionType) {
    this.subscriptionType = subscriptionType.getValue();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((idProvider == null) ? 0 : idProvider.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ConnectorApiKey other = (ConnectorApiKey) obj;
    if (idProvider == null) {
      if (other.idProvider != null)
        return false;
    } else if (!idProvider.equals(other.idProvider))
      return false;
    return true;
  }
  
  
  @Override
  public String toString() {
    return "ConnectorApiKey [idProvider=" + idProvider + ", apiKey=" + this.getApiKey() + ", subscriptionType=" + subscriptionType
        + "]";
  }
 
  
  public static  StringEncryptor stringEncryptor() {
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
  
  
}
