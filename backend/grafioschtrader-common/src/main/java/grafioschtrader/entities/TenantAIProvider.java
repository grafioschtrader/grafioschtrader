package grafioschtrader.entities;

import org.jasypt.encryption.StringEncryptor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import grafiosch.common.DynamicFormField;
import grafiosch.dynamic.model.DynamicFormPropertyHelps;
import grafiosch.entities.ConnectorApiKey;
import grafiosch.entities.TenantBaseID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

/** A home tenant's language-model configuration. Secrets are write-only and excluded from data exports. */
@Entity
@Table(name = TenantAIProvider.TABNAME)
@Schema(description = "Language-model provider paid by the user's home tenant. The API key is write-only.")
public class TenantAIProvider extends TenantBaseID {
  public static final String TABNAME = "tenant_ai_provider";
  private static final StringEncryptor ENCRYPTOR = ConnectorApiKey.stringEncryptor();

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_tenant_ai_provider")
  private Integer idTenantAIProvider;

  @Column(name = "id_tenant", nullable = false)
  private Integer idTenant;

  @DynamicFormField(uiOrder = "1.1", helps = DynamicFormPropertyHelps.SELECT_OPTIONS)
  @NotBlank
  @Size(max = 20)
  @Column(name = "provider", nullable = false)
  private String provider;

  @DynamicFormField(uiOrder = "1.2")
  @Size(max = 64)
  @Column(name = "model")
  private String model;

  @DynamicFormField(uiOrder = "1.3")
  @Min(1)
  @Max(127)
  @Column(name = "priority", nullable = false)
  private byte priority = 1;

  @DynamicFormField(uiOrder = "1.4")
  @Column(name = "active", nullable = false)
  private boolean active = true;

  @JsonIgnore
  @Column(name = "api_key", length = 512)
  private String encryptedApiKey;

  @Schema(description = """
      Requests per UTC day the tenant allows over this key, the cost limit of the tenant. When it is reached, the next
      provider in the order of priority is tried. Null sets no limit of the tenant; the daily limit per user of the
      instance applies in any case.""")
  @DynamicFormField(uiOrder = "1.5")
  @Min(1)
  @Max(100000)
  @Column(name = "daily_request_limit")
  private Integer dailyRequestLimit;

  @Transient
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  @DynamicFormField(uiOrder = "1.6", helps = DynamicFormPropertyHelps.PASSWORD)
  @Size(max = 351)
  private String apiKey;

  @Schema(description = "Requests over this key in the current UTC day; set when the keys are listed")
  @Transient
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private Long requestsToday;

  public Integer getIdTenantAIProvider() {
    return idTenantAIProvider;
  }

  public void setIdTenantAIProvider(Integer id) {
    idTenantAIProvider = id;
  }

  @Override
  public Integer getId() {
    return idTenantAIProvider;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public byte getPriority() {
    return priority;
  }

  public void setPriority(byte priority) {
    this.priority = priority;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Integer getDailyRequestLimit() {
    return dailyRequestLimit;
  }

  public void setDailyRequestLimit(Integer dailyRequestLimit) {
    this.dailyRequestLimit = dailyRequestLimit;
  }

  public Long getRequestsToday() {
    return requestsToday;
  }

  public void setRequestsToday(Long requestsToday) {
    this.requestsToday = requestsToday;
  }

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  /** Returns the stored secret only to the model client, never to JSON serialization. */
  @JsonIgnore
  public String decryptApiKey() {
    return encryptedApiKey == null ? null : ENCRYPTOR.decrypt(encryptedApiKey);
  }

  /** Called after validation; the request's plaintext is discarded. */
  public void storeApiKey(String plainKey) {
    encryptedApiKey = plainKey == null ? null : ENCRYPTOR.encrypt(plainKey);
    apiKey = null;
  }

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public String getApiKeyHint() {
    String key = decryptApiKey();
    return key == null ? null : "…" + key.substring(Math.max(0, key.length() - 4));
  }
}
