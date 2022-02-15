

package grafioschtrader.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.core.env.StandardEnvironment;

import com.ulisesbocchio.jasyptspringboot.encryptor.DefaultLazyEncryptor;

import grafioschtrader.types.SubscriptionType;


@Entity
@Table(name = ConnectorApiKey.TABNAME)
public class ConnectorApiKey {

  

  public static final String TABNAME = "connector_apikey";
  private static StringEncryptor stringEncryptor = new DefaultLazyEncryptor(new StandardEnvironment());
  
  @Id
  @Column(name = "id_provider")
  private String idProvider;

 
  @Column(name = "api_key")
  private String apiKey;
  
  @Column(name = "subscription_type")
  private Short subscriptionType;
 
  public String getIdProvider() {
    return idProvider;
  }

  public void setIdProvider(String idProvider) {
    this.idProvider = idProvider;
  }

  public String getApiKey() {
    return stringEncryptor.decrypt(apiKey);
  }

  public void setApiKey(String apiKey) {
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
  
}
