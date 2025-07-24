package grafioschtrader.connector.yahoo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Data Transfer Objects for Yahoo Finance Visualization API
 * Used for various endpoints like earnings, splits, dividends, etc.
 * All classes use public fields instead of getters/setters for simplicity
 */
public class YahooFinanceDTO {
  public String sortType;
  public String entityIdType;
  public String sortField;
  public List<String> includeFields;
  public QueryOperand query;
  public String offset;
  public int size;

  public static class QueryOperand {
    public String operator;
    public List<Object> operands;
  }

  // Response DTOs
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class VisualizationResponse {
    public FinanceData finance;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class FinanceData {
    public List<VisualizationResult> result;
    public Object error;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class VisualizationResult {
    public List<VisualizationDocument> documents;
    public int total;
    public Object criteriaMeta;
    public String rawCriteria;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class VisualizationDocument {
    public String entityIdType;
    public List<VisualizationColumn> columns;
    public List<List<Object>> rows;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class VisualizationColumn {
    public String id;
    public String label;
    public String type;
  }
}
