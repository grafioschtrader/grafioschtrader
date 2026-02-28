package grafioschtrader.connector.instrument.generic;

import java.util.List;
import java.util.Map;

/**
 * Result DTO for a generic connector endpoint test. Contains the HTTP request URL (with API key masked), HTTP status
 * code, raw response snippet, parsed data rows, execution time, and any error message. Returned by the test endpoint
 * to help users verify their connector configuration.
 */
public class GenericConnectorTestResult {

  private boolean success;
  private String errorMessage;
  private String requestUrl;
  private int httpStatus;
  private String rawResponseSnippet;
  private List<Map<String, String>> parsedRows;
  private long executionTimeMs;

  public static GenericConnectorTestResult success(String requestUrl, int httpStatus, String rawResponseSnippet,
      List<Map<String, String>> parsedRows, long executionTimeMs) {
    GenericConnectorTestResult r = new GenericConnectorTestResult();
    r.success = true;
    r.requestUrl = requestUrl;
    r.httpStatus = httpStatus;
    r.rawResponseSnippet = rawResponseSnippet;
    r.parsedRows = parsedRows;
    r.executionTimeMs = executionTimeMs;
    return r;
  }

  public static GenericConnectorTestResult error(String requestUrl, int httpStatus, String rawResponseSnippet,
      String errorMessage, long executionTimeMs) {
    GenericConnectorTestResult r = new GenericConnectorTestResult();
    r.success = false;
    r.requestUrl = requestUrl;
    r.httpStatus = httpStatus;
    r.rawResponseSnippet = rawResponseSnippet;
    r.errorMessage = errorMessage;
    r.executionTimeMs = executionTimeMs;
    return r;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getRequestUrl() {
    return requestUrl;
  }

  public void setRequestUrl(String requestUrl) {
    this.requestUrl = requestUrl;
  }

  public int getHttpStatus() {
    return httpStatus;
  }

  public void setHttpStatus(int httpStatus) {
    this.httpStatus = httpStatus;
  }

  public String getRawResponseSnippet() {
    return rawResponseSnippet;
  }

  public void setRawResponseSnippet(String rawResponseSnippet) {
    this.rawResponseSnippet = rawResponseSnippet;
  }

  public List<Map<String, String>> getParsedRows() {
    return parsedRows;
  }

  public void setParsedRows(List<Map<String, String>> parsedRows) {
    this.parsedRows = parsedRows;
  }

  public long getExecutionTimeMs() {
    return executionTimeMs;
  }

  public void setExecutionTimeMs(long executionTimeMs) {
    this.executionTimeMs = executionTimeMs;
  }
}
