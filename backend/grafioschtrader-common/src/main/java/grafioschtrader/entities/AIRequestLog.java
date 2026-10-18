package grafioschtrader.entities;

import java.time.LocalDateTime;

import grafioschtrader.types.AIKeySource;
import grafioschtrader.types.AIRequestPurpose;
import grafioschtrader.types.AIRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One round trip to a language model, reserved before the call. A reservation counts against the daily budget of the
 * user, and for INSTANCE keys also the budget of the instance, whatever happens afterwards, including a failed call or
 * a crash, because its billing state is unknown. The table is an operational log: it is never written over REST
 * directly, rows older than the retention are deleted before the budgets are counted, and it is not part of the data
 * export.
 */
@Schema(description = "One round trip to a language model, reserved before the call.")
@Entity
@Table(name = AIRequestLog.TABNAME)
public class AIRequestLog {

  public static final String TABNAME = "ai_request_log";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_ai_request_log")
  private Integer idAIRequestLog;

  @Column(name = "id_user")
  private Integer idUser;

  @Schema(description = "UTC time of the reservation")
  @Column(name = "request_time")
  private LocalDateTime requestTime;

  @Column(name = "purpose")
  private byte purpose;

  @Column(name = "key_source", nullable = false)
  private byte keySource;

  @Column(name = "provider")
  private String provider;

  @Schema(description = "The tenant key the request went over, null for an instance key; counts the tenant limit")
  @Column(name = "id_tenant_ai_provider")
  private Integer idTenantAIProvider;

  @Column(name = "model")
  private String model;

  @Column(name = "status")
  private byte status;

  @Column(name = "input_tokens")
  private Integer inputTokens;

  @Schema(description = "Output tokens as reported by the provider; Ollama often reports none")
  @Column(name = "output_tokens")
  private Integer outputTokens;

  public AIRequestLog() {
  }

  public AIRequestLog(Integer idUser, LocalDateTime requestTime, AIRequestPurpose purpose, AIKeySource keySource,
      String provider, Integer idTenantAIProvider, String model) {
    this.idUser = idUser;
    this.requestTime = requestTime;
    this.purpose = purpose.getValue();
    this.keySource = keySource.getValue();
    this.provider = provider;
    this.idTenantAIProvider = idTenantAIProvider;
    this.model = model;
    this.status = AIRequestStatus.RESERVED.getValue();
  }

  public Integer getIdAIRequestLog() {
    return idAIRequestLog;
  }

  public Integer getIdUser() {
    return idUser;
  }

  public Integer getIdTenantAIProvider() {
    return idTenantAIProvider;
  }

  public LocalDateTime getRequestTime() {
    return requestTime;
  }

  public AIRequestPurpose getPurpose() {
    return AIRequestPurpose.getByValue(purpose);
  }

  public String getProvider() {
    return provider;
  }

  public AIKeySource getKeySource() {
    return AIKeySource.getByValue(keySource);
  }

  public void setKeySource(AIKeySource keySource) {
    this.keySource = keySource.getValue();
  }

  public String getModel() {
    return model;
  }

  public AIRequestStatus getStatus() {
    return AIRequestStatus.getByValue(status);
  }

  public void setStatus(AIRequestStatus status) {
    this.status = status.getValue();
  }

  public Integer getInputTokens() {
    return inputTokens;
  }

  public void setInputTokens(Integer inputTokens) {
    this.inputTokens = inputTokens;
  }

  public Integer getOutputTokens() {
    return outputTokens;
  }

  public void setOutputTokens(Integer outputTokens) {
    this.outputTokens = outputTokens;
  }
}
