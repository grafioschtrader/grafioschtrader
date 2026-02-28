package grafioschtrader.entities;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.common.LockedWhenUsed;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertySelectiveUpdatableOrWhenNull;
import grafiosch.entities.Auditable;
import grafiosch.entities.MultilanguageString;
import grafioschtrader.types.RateLimitType;
import grafioschtrader.validation.ValidMultilanguage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Main definition entity for a generic configurable feed connector. Each row represents one data provider that can be
 * configured entirely through the database without writing Java code. Contains the domain URL, rate limiting settings,
 * feature flags, and references to endpoints, field mappings, and custom HTTP headers.
 */
@Entity
@Table(name = GenericConnectorDef.TABNAME)
@Schema(description = """
    Defines a generic feed connector for a financial data provider. Supports JSON, CSV, and HTML response formats \
    with configurable URL templates, rate limiting, and field mappings. Any user can create connectors (with daily \
    limits), but only admins can activate them.""")
public class GenericConnectorDef extends Auditable implements Serializable {

  public static final String TABNAME = "generic_connector_def";
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_generic_connector")
  private Integer idGenericConnector;

  @NotNull
  @Size(min = 2, max = 32)
  @Column(name = "short_id")
  @Schema(description = "Unique short identifier, becomes gt.datafeed.<shortId> as the connector ID")
  @PropertySelectiveUpdatableOrWhenNull
  @LockedWhenUsed
  private String shortId;

  @NotNull
  @Size(min = 1, max = 100)
  @Column(name = "readable_name")
  @Schema(description = "Display name shown in the UI connector dropdowns")
  @PropertyAlwaysUpdatable
  @LockedWhenUsed
  private String readableName;

  @NotNull
  @Size(min = 1, max = 255)
  @Column(name = "domain_url")
  @Schema(description = "Base domain URL including trailing slash, e.g. https://api.twelvedata.com/")
  @PropertySelectiveUpdatableOrWhenNull
  @LockedWhenUsed
  private String domainUrl;

  @Column(name = "needs_api_key")
  @Schema(description = "Whether this connector requires an API key from the connector_apikey table")
  @PropertySelectiveUpdatableOrWhenNull
  @LockedWhenUsed
  private boolean needsApiKey;

  @Column(name = "rate_limit_type")
  @Schema(description = "Rate limiting strategy: NONE, TOKEN_BUCKET, or SEMAPHORE")
  @PropertySelectiveUpdatableOrWhenNull
  @LockedWhenUsed
  private byte rateLimitType;

  @Column(name = "rate_limit_requests")
  @Schema(description = "Maximum requests per period for TOKEN_BUCKET rate limiting")
  @PropertySelectiveUpdatableOrWhenNull
  @LockedWhenUsed
  private Short rateLimitRequests;

  @Column(name = "rate_limit_period_sec")
  @Schema(description = "Period in seconds for TOKEN_BUCKET rate limiting")
  @PropertySelectiveUpdatableOrWhenNull
  @LockedWhenUsed
  private Short rateLimitPeriodSec;

  @Column(name = "rate_limit_concurrent")
  @Schema(description = "Maximum concurrent requests for SEMAPHORE rate limiting")
  @PropertySelectiveUpdatableOrWhenNull
  @LockedWhenUsed
  private Short rateLimitConcurrent;

  @Column(name = "intraday_delay_seconds")
  @Schema(description = "Delay in seconds for intraday data. 0 = real-time, 900 = 15min delay")
  @PropertyAlwaysUpdatable
  private int intradayDelaySeconds = 900;

  @Size(max = 255)
  @Column(name = "regex_url_pattern")
  @Schema(description = "Regex pattern to validate urlExtend values for this connector")
  @PropertyAlwaysUpdatable
  @LockedWhenUsed
  private String regexUrlPattern;

  @Column(name = "supports_security")
  @PropertySelectiveUpdatableOrWhenNull
  @LockedWhenUsed
  private boolean supportsSecurity = true;

  @Column(name = "supports_currency")
  @PropertySelectiveUpdatableOrWhenNull
  @LockedWhenUsed
  private boolean supportsCurrency;

  @Column(name = "need_history_gap_filler")
  @Schema(description = "Whether historical data needs gap filling for non-trading days")
  @PropertyAlwaysUpdatable
  private boolean needHistoryGapFiller;

  @Column(name = "gbx_divider_enabled")
  @Schema(description = "Whether to divide prices by 100 for London Stock Exchange GBX-denominated securities")
  @PropertyAlwaysUpdatable
  private boolean gbxDividerEnabled;

  @Schema(description = """
      YAML configuration for automatic token acquisition (SAML SSO, etc.).
      When set, the connector auto-acquires and refreshes JWT tokens instead of
      requiring a static API key from the connector_apikey table.""")
  @Column(name = "token_config_yaml", columnDefinition = "TEXT")
  @PropertyAlwaysUpdatable
  private String tokenConfigYaml;

  @OneToOne(fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
  @JoinColumn(name = "description_nls")
  @ValidMultilanguage
  @PropertyAlwaysUpdatable
  private MultilanguageString descriptionNLS;

  @Column(name = "activated")
  private boolean activated;

  @OneToMany(mappedBy = "genericConnectorDef", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("feedSupport ASC, instrumentType ASC")
  private List<GenericConnectorEndpoint> endpoints;

  @OneToMany(mappedBy = "genericConnectorDef", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
  private List<GenericConnectorHttpHeader> httpHeaders;

  @Transient
  @Schema(description = "Number of securities and currency pairs currently referencing this connector")
  private long instrumentCount;

  @Override
  public Integer getId() {
    return idGenericConnector;
  }

  public void setId(Integer id) {
    this.idGenericConnector = id;
  }

  public Integer getIdGenericConnector() {
    return idGenericConnector;
  }

  public void setIdGenericConnector(Integer idGenericConnector) {
    this.idGenericConnector = idGenericConnector;
  }

  public String getShortId() {
    return shortId;
  }

  public void setShortId(String shortId) {
    this.shortId = shortId;
  }

  public String getReadableName() {
    return readableName;
  }

  public void setReadableName(String readableName) {
    this.readableName = readableName;
  }

  public String getDomainUrl() {
    return domainUrl;
  }

  public void setDomainUrl(String domainUrl) {
    this.domainUrl = domainUrl;
  }

  public boolean isNeedsApiKey() {
    return needsApiKey;
  }

  public void setNeedsApiKey(boolean needsApiKey) {
    this.needsApiKey = needsApiKey;
  }

  public RateLimitType getRateLimitType() {
    return RateLimitType.getByValue(this.rateLimitType);
  }

  public void setRateLimitType(RateLimitType rateLimitType) {
    this.rateLimitType = rateLimitType.getValue();
  }

  public Short getRateLimitRequests() {
    return rateLimitRequests;
  }

  public void setRateLimitRequests(Short rateLimitRequests) {
    this.rateLimitRequests = rateLimitRequests;
  }

  public Short getRateLimitPeriodSec() {
    return rateLimitPeriodSec;
  }

  public void setRateLimitPeriodSec(Short rateLimitPeriodSec) {
    this.rateLimitPeriodSec = rateLimitPeriodSec;
  }

  public Short getRateLimitConcurrent() {
    return rateLimitConcurrent;
  }

  public void setRateLimitConcurrent(Short rateLimitConcurrent) {
    this.rateLimitConcurrent = rateLimitConcurrent;
  }

  public int getIntradayDelaySeconds() {
    return intradayDelaySeconds;
  }

  public void setIntradayDelaySeconds(int intradayDelaySeconds) {
    this.intradayDelaySeconds = intradayDelaySeconds;
  }

  public String getRegexUrlPattern() {
    return regexUrlPattern;
  }

  public void setRegexUrlPattern(String regexUrlPattern) {
    this.regexUrlPattern = regexUrlPattern;
  }

  public boolean isSupportsSecurity() {
    return supportsSecurity;
  }

  public void setSupportsSecurity(boolean supportsSecurity) {
    this.supportsSecurity = supportsSecurity;
  }

  public boolean isSupportsCurrency() {
    return supportsCurrency;
  }

  public void setSupportsCurrency(boolean supportsCurrency) {
    this.supportsCurrency = supportsCurrency;
  }

  public boolean isNeedHistoryGapFiller() {
    return needHistoryGapFiller;
  }

  public void setNeedHistoryGapFiller(boolean needHistoryGapFiller) {
    this.needHistoryGapFiller = needHistoryGapFiller;
  }

  public boolean isGbxDividerEnabled() {
    return gbxDividerEnabled;
  }

  public void setGbxDividerEnabled(boolean gbxDividerEnabled) {
    this.gbxDividerEnabled = gbxDividerEnabled;
  }

  public String getTokenConfigYaml() {
    return tokenConfigYaml;
  }

  public void setTokenConfigYaml(String tokenConfigYaml) {
    this.tokenConfigYaml = tokenConfigYaml;
  }

  @JsonIgnore
  public boolean hasAutoToken() {
    return tokenConfigYaml != null && !tokenConfigYaml.isBlank();
  }

  public MultilanguageString getDescriptionNLS() {
    return descriptionNLS;
  }

  public void setDescriptionNLS(MultilanguageString descriptionNLS) {
    this.descriptionNLS = descriptionNLS;
  }

  public boolean isActivated() {
    return activated;
  }

  public void setActivated(boolean activated) {
    this.activated = activated;
  }

  public List<GenericConnectorEndpoint> getEndpoints() {
    return endpoints;
  }

  public void setEndpoints(List<GenericConnectorEndpoint> endpoints) {
    this.endpoints = endpoints;
  }

  public List<GenericConnectorHttpHeader> getHttpHeaders() {
    return httpHeaders;
  }

  public void setHttpHeaders(List<GenericConnectorHttpHeader> httpHeaders) {
    this.httpHeaders = httpHeaders;
  }

  public long getInstrumentCount() {
    return instrumentCount;
  }

  public void setInstrumentCount(long instrumentCount) {
    this.instrumentCount = instrumentCount;
  }
}
