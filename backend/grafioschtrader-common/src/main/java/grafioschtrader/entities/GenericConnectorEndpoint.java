package grafioschtrader.entities;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.common.LockedWhenUsed;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.entities.BaseID;
import grafioschtrader.types.DateFormatType;
import grafioschtrader.types.EndpointOption;
import grafioschtrader.types.HtmlExtractMode;
import grafioschtrader.types.JsonDataStructure;
import grafioschtrader.types.NumberFormatType;
import grafioschtrader.types.ResponseFormatType;
import grafioschtrader.types.TickerBuildStrategy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Defines a single endpoint within a generic connector, specifying the URL template, response parsing configuration,
 * and field mapping strategy for a specific combination of feed type (HISTORY/INTRA) and instrument type
 * (SECURITY/CURRENCY). Each generic connector can have up to 4 endpoints.
 */
@Entity
@Table(name = GenericConnectorEndpoint.TABNAME)
@Schema(description = """
    URL template and parsing configuration for one feed type + instrument type combination of a generic connector.""")
public class GenericConnectorEndpoint extends BaseID<Integer> implements Serializable {

  public static final String TABNAME = "generic_connector_endpoint";
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_endpoint")
  private Integer idEndpoint;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "id_generic_connector")
  @JsonIgnore
  private GenericConnectorDef genericConnectorDef;

  @NotNull
  @Size(max = 10)
  @Column(name = "feed_support")
  @Schema(description = "Feed type: 'FS_HISTORY' for EOD data or 'FS_INTRA' for intraday data")
  @LockedWhenUsed
  private String feedSupport;

  @NotNull
  @Size(max = 10)
  @Column(name = "instrument_type")
  @Schema(description = "Instrument type: 'SECURITY' or 'CURRENCY'")
  @LockedWhenUsed
  private String instrumentType;

  @NotNull
  @Size(max = 1000)
  @Column(name = "url_template")
  @Schema(description = """
      URL path and query template with placeholders like {ticker}, {fromDate}, {toDate}, {apiKey}, etc. \
      Appended to the connector's domainUrl.""")
  @LockedWhenUsed
  private String urlTemplate;

  @NotNull
  @Size(max = 6)
  @Column(name = "http_method")
  @LockedWhenUsed
  private String httpMethod = "GET";

  @Column(name = "response_format")
  @Schema(description = "Response format: JSON(1), CSV(2), or HTML(3)")
  @LockedWhenUsed
  private byte responseFormat;

  @Column(name = "number_format")
  @Schema(description = "Number format for parsing: US(1), GERMAN(2), SWISS(3), PLAIN(4)")
  @LockedWhenUsed
  private byte numberFormat = 4;

  @Column(name = "date_format_type")
  @Schema(description = "Date format in responses: UNIX_SECONDS(1), UNIX_MILLIS(2), PATTERN(3), ISO_DATE(4), ISO_DATE_TIME(5)")
  @LockedWhenUsed
  private byte dateFormatType = 4;

  @Size(max = 64)
  @Column(name = "date_format_pattern")
  @Schema(description = "Java date format pattern when dateFormatType=PATTERN, e.g. 'MM/dd/yyyy'")
  @LockedWhenUsed
  private String dateFormatPattern;

  @Column(name = "json_data_structure")
  @Schema(description = "JSON structure: ARRAY_OF_OBJECTS(1), PARALLEL_ARRAYS(2), SINGLE_OBJECT(3)")
  @LockedWhenUsed
  private Byte jsonDataStructure;

  @Size(max = 255)
  @Column(name = "json_data_path")
  @Schema(description = "Dot-separated path to the data array/object in JSON response, e.g. 'chart.result.0.indicators.quote.0'")
  @LockedWhenUsed
  private String jsonDataPath;

  @Size(max = 255)
  @Column(name = "json_column_names_path")
  @Schema(description = "Dot-separated path to the column names array in JSON response, used with COLUMN_ROW_ARRAYS structure")
  @LockedWhenUsed
  private String jsonColumnNamesPath;

  @Size(max = 128)
  @Column(name = "json_status_path")
  @Schema(description = "Dot-separated path to status field in JSON response for error detection")
  @LockedWhenUsed
  private String jsonStatusPath;

  @Size(max = 64)
  @Column(name = "json_status_ok_value")
  @Schema(description = "Expected value at jsonStatusPath that indicates success, e.g. 'ok'")
  @LockedWhenUsed
  private String jsonStatusOkValue;

  @Size(max = 4)
  @Column(name = "csv_delimiter")
  @Schema(description = "CSV field delimiter character, e.g. ',' or ';' or '\\t'")
  @LockedWhenUsed
  private String csvDelimiter;

  @Column(name = "csv_skip_header_lines")
  @Schema(description = "Number of header lines to skip in CSV responses")
  @LockedWhenUsed
  private Byte csvSkipHeaderLines = 1;

  @Size(max = 255)
  @Column(name = "html_css_selector")
  @Schema(description = "JSoup CSS selector for the main HTML element containing price data")
  @LockedWhenUsed
  private String htmlCssSelector;

  @Column(name = "html_extract_mode")
  @Schema(description = "HTML extraction mode: REGEX_GROUPS(1), SPLIT_POSITIONS(2), MULTI_SELECTOR(3)")
  @LockedWhenUsed
  private Byte htmlExtractMode;

  @Size(max = 255)
  @Column(name = "html_text_cleanup")
  @Schema(description = "Regex pattern for cleaning extracted text before parsing, e.g. removing currency codes or percent signs")
  @LockedWhenUsed
  private String htmlTextCleanup;

  @Size(max = 512)
  @Column(name = "html_extract_regex")
  @Schema(description = "Regex with capture groups for REGEX_GROUPS extraction mode")
  @LockedWhenUsed
  private String htmlExtractRegex;

  @Size(max = 16)
  @Column(name = "html_split_delimiter")
  @Schema(description = "Delimiter for SPLIT_POSITIONS extraction mode, e.g. '\\s+'")
  @LockedWhenUsed
  private String htmlSplitDelimiter;

  @Column(name = "ticker_build_strategy")
  @Schema(description = "How to construct the ticker: URL_EXTEND(1) uses urlExtend directly, CURRENCY_PAIR(2) builds from currency codes")
  @LockedWhenUsed
  private byte tickerBuildStrategy = 1;

  @Size(max = 4)
  @Column(name = "currency_pair_separator")
  @Schema(description = "Separator between currency codes for CURRENCY_PAIR strategy, e.g. '/' or '-'")
  @LockedWhenUsed
  private String currencyPairSeparator;

  @Size(max = 20)
  @Column(name = "currency_pair_suffix")
  @Schema(description = "Suffix appended to currency pair ticker, e.g. '.FOREX' or '=X'")
  @LockedWhenUsed
  private String currencyPairSuffix;

  @Column(name = "ticker_uppercase")
  @LockedWhenUsed
  private boolean tickerUppercase = true;

  @Column(name = "max_data_points")
  @Schema(description = "Maximum data points per request, used with pagination")
  @LockedWhenUsed
  private Integer maxDataPoints;

  @Column(name = "pagination_enabled")
  @LockedWhenUsed
  private boolean paginationEnabled;

  @Column(name = "endpoint_options")
  @Schema(description = "Bitmask of EndpointOption values for feed-type-specific processing options")
  private Long endpointOptions;

  @Column(name = "ever_used_successfully")
  @Schema(description = "One-way flag set to true after the first successful data fetch through this endpoint")
  private boolean everUsedSuccessfully;

  @OneToMany(mappedBy = "endpoint", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("targetField ASC")
  @PropertyAlwaysUpdatable
  private List<GenericConnectorFieldMapping> fieldMappings;

  @Override
  public Integer getId() {
    return idEndpoint;
  }

  public Integer getIdEndpoint() {
    return idEndpoint;
  }

  public void setIdEndpoint(Integer idEndpoint) {
    this.idEndpoint = idEndpoint;
  }

  public GenericConnectorDef getGenericConnectorDef() {
    return genericConnectorDef;
  }

  public void setGenericConnectorDef(GenericConnectorDef genericConnectorDef) {
    this.genericConnectorDef = genericConnectorDef;
  }

  public String getFeedSupport() {
    return feedSupport;
  }

  public void setFeedSupport(String feedSupport) {
    this.feedSupport = feedSupport;
  }

  public String getInstrumentType() {
    return instrumentType;
  }

  public void setInstrumentType(String instrumentType) {
    this.instrumentType = instrumentType;
  }

  public String getUrlTemplate() {
    return urlTemplate;
  }

  public void setUrlTemplate(String urlTemplate) {
    this.urlTemplate = urlTemplate;
  }

  public String getHttpMethod() {
    return httpMethod;
  }

  public void setHttpMethod(String httpMethod) {
    this.httpMethod = httpMethod;
  }

  public ResponseFormatType getResponseFormat() {
    return ResponseFormatType.getByValue(this.responseFormat);
  }

  public void setResponseFormat(ResponseFormatType responseFormat) {
    this.responseFormat = responseFormat.getValue();
  }

  public NumberFormatType getNumberFormat() {
    return NumberFormatType.getByValue(this.numberFormat);
  }

  public void setNumberFormat(NumberFormatType numberFormat) {
    this.numberFormat = numberFormat.getValue();
  }

  public DateFormatType getDateFormatType() {
    return DateFormatType.getByValue(this.dateFormatType);
  }

  public void setDateFormatType(DateFormatType dateFormatType) {
    this.dateFormatType = dateFormatType.getValue();
  }

  public String getDateFormatPattern() {
    return dateFormatPattern;
  }

  public void setDateFormatPattern(String dateFormatPattern) {
    this.dateFormatPattern = dateFormatPattern;
  }

  public JsonDataStructure getJsonDataStructure() {
    return jsonDataStructure == null ? null : JsonDataStructure.getByValue(jsonDataStructure);
  }

  public void setJsonDataStructure(JsonDataStructure jsonDataStructure) {
    this.jsonDataStructure = jsonDataStructure == null ? null : jsonDataStructure.getValue();
  }

  public String getJsonDataPath() {
    return jsonDataPath;
  }

  public void setJsonDataPath(String jsonDataPath) {
    this.jsonDataPath = jsonDataPath;
  }

  public String getJsonColumnNamesPath() {
    return jsonColumnNamesPath;
  }

  public void setJsonColumnNamesPath(String jsonColumnNamesPath) {
    this.jsonColumnNamesPath = jsonColumnNamesPath;
  }

  public String getJsonStatusPath() {
    return jsonStatusPath;
  }

  public void setJsonStatusPath(String jsonStatusPath) {
    this.jsonStatusPath = jsonStatusPath;
  }

  public String getJsonStatusOkValue() {
    return jsonStatusOkValue;
  }

  public void setJsonStatusOkValue(String jsonStatusOkValue) {
    this.jsonStatusOkValue = jsonStatusOkValue;
  }

  public String getCsvDelimiter() {
    return csvDelimiter;
  }

  public void setCsvDelimiter(String csvDelimiter) {
    this.csvDelimiter = csvDelimiter;
  }

  public Byte getCsvSkipHeaderLines() {
    return csvSkipHeaderLines;
  }

  public void setCsvSkipHeaderLines(Byte csvSkipHeaderLines) {
    this.csvSkipHeaderLines = csvSkipHeaderLines;
  }

  public String getHtmlCssSelector() {
    return htmlCssSelector;
  }

  public void setHtmlCssSelector(String htmlCssSelector) {
    this.htmlCssSelector = htmlCssSelector;
  }

  public HtmlExtractMode getHtmlExtractMode() {
    return htmlExtractMode == null ? null : HtmlExtractMode.getByValue(htmlExtractMode);
  }

  public void setHtmlExtractMode(HtmlExtractMode htmlExtractMode) {
    this.htmlExtractMode = htmlExtractMode == null ? null : htmlExtractMode.getValue();
  }

  public String getHtmlTextCleanup() {
    return htmlTextCleanup;
  }

  public void setHtmlTextCleanup(String htmlTextCleanup) {
    this.htmlTextCleanup = htmlTextCleanup;
  }

  public String getHtmlExtractRegex() {
    return htmlExtractRegex;
  }

  public void setHtmlExtractRegex(String htmlExtractRegex) {
    this.htmlExtractRegex = htmlExtractRegex;
  }

  public String getHtmlSplitDelimiter() {
    return htmlSplitDelimiter;
  }

  public void setHtmlSplitDelimiter(String htmlSplitDelimiter) {
    this.htmlSplitDelimiter = htmlSplitDelimiter;
  }

  public TickerBuildStrategy getTickerBuildStrategy() {
    return TickerBuildStrategy.getByValue(this.tickerBuildStrategy);
  }

  public void setTickerBuildStrategy(TickerBuildStrategy tickerBuildStrategy) {
    this.tickerBuildStrategy = tickerBuildStrategy.getValue();
  }

  public String getCurrencyPairSeparator() {
    return currencyPairSeparator;
  }

  public void setCurrencyPairSeparator(String currencyPairSeparator) {
    this.currencyPairSeparator = currencyPairSeparator;
  }

  public String getCurrencyPairSuffix() {
    return currencyPairSuffix;
  }

  public void setCurrencyPairSuffix(String currencyPairSuffix) {
    this.currencyPairSuffix = currencyPairSuffix;
  }

  public boolean isTickerUppercase() {
    return tickerUppercase;
  }

  public void setTickerUppercase(boolean tickerUppercase) {
    this.tickerUppercase = tickerUppercase;
  }

  public Integer getMaxDataPoints() {
    return maxDataPoints;
  }

  public void setMaxDataPoints(Integer maxDataPoints) {
    this.maxDataPoints = maxDataPoints;
  }

  public boolean isPaginationEnabled() {
    return paginationEnabled;
  }

  public void setPaginationEnabled(boolean paginationEnabled) {
    this.paginationEnabled = paginationEnabled;
  }

  public EnumSet<EndpointOption> getEndpointOptions() {
    return endpointOptions == null ? EnumSet.noneOf(EndpointOption.class)
        : EndpointOption.decode(endpointOptions);
  }

  public void setEndpointOptions(EnumSet<EndpointOption> options) {
    this.endpointOptions = options == null || options.isEmpty() ? null : EndpointOption.encode(options);
  }

  public boolean isEverUsedSuccessfully() {
    return everUsedSuccessfully;
  }

  public void setEverUsedSuccessfully(boolean everUsedSuccessfully) {
    this.everUsedSuccessfully = everUsedSuccessfully;
  }

  public List<GenericConnectorFieldMapping> getFieldMappings() {
    return fieldMappings;
  }

  public void setFieldMappings(List<GenericConnectorFieldMapping> fieldMappings) {
    this.fieldMappings = fieldMappings;
  }
}
