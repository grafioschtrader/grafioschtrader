package grafioschtrader.connector.yahoo;


import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.connector.instrument.FeedConnectorHelper;
import grafioschtrader.connector.yahoo.YahooFinanceDTO.QueryOperand;

/**
 * Abstract base class for Yahoo Finance connectors that provides common functionality for making HTTP requests to the
 * Yahoo Finance Visualization API.
 * 
 * This class implements the Template Method pattern, providing a standard workflow for creating requests, executing
 * HTTP calls, and parsing responses while allowing subclasses to customize specific aspects of the process.
 */
public abstract class AbstractYahooFinanceConnector {

  protected final Logger log = LoggerFactory.getLogger(this.getClass());
  protected final HttpClient httpClient;
  protected final ObjectMapper objectMapper;
  protected static final String YAHOO_FINANCE_URL = "https://query1.finance.yahoo.com/v1/finance/visualization?lang=en-US&region=US";

  /**
   * Constructor initializing common components
   * 
   * @param connectTimeoutSeconds HTTP connection timeout in seconds
   */
  protected AbstractYahooFinanceConnector(int connectTimeoutSeconds) {
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectTimeoutSeconds)).build();
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Template method for executing Yahoo Finance API requests. Subclasses implement specific request creation and
   * response parsing.
   * 
   * @param <T>           The type of result returned by the subclass
   * @param requestParams Parameters needed to create the request
   * @return Parsed response data of type T
   * @throws Exception if HTTP request fails or response parsing fails
   */
  protected <T> T executeYahooRequest(Object requestParams) throws Exception {
    try {
      // Step 1: Create request (implemented by subclass)
      YahooFinanceDTO request = createRequest(requestParams);
      String requestBody = objectMapper.writeValueAsString(request);

      // Step 2: Build and execute HTTP request
      HttpRequest httpRequest = buildHttpRequest(requestBody);
      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      // Step 3: Check response status and parse
      if (response.statusCode() == HttpURLConnection.HTTP_OK) {
        return parseResponse(response.body(), requestParams);
      } else {
        handleHttpError(response);
        return null;
      }

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Request was interrupted", e);
    } catch (Exception e) {
      if (e instanceof IOException) {
        throw e;
      }
      throw new IOException("Error executing Yahoo Finance request", e);
    }
  }

  /**
   * Builds the HTTP request with standard headers and authentication Uses the exact same pattern as the working
   * implementations
   * 
   * @param requestBody JSON request body
   * @return Configured HttpRequest
   */
  protected HttpRequest buildHttpRequest(String requestBody) {
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(YAHOO_FINANCE_URL))
        .header("Content-Type", "application/json").header("User-Agent", FeedConnectorHelper.getHttpAgentAsString(true))
        .header("Accept", "application/json").header("Accept-Language", "en-US,en;q=0.9")
        .header("Accept-Encoding", "deflate, br").header("Cookie", CrumbManager.getCookie())
        .header("x-crumb", CrumbManager.getCrumb()).timeout(Duration.ofSeconds(30))
        .POST(HttpRequest.BodyPublishers.ofString(requestBody));

    return requestBuilder.build();
  }

  /**
   * Handles HTTP error responses exactly like the working implementations
   * 
   * @param response The HTTP response with error status
   * @throws IOException if the error requires exception handling
   */
  protected void handleHttpError(HttpResponse<String> response) throws IOException {
    log.warn("Could not fetch data from Yahoo Finance: {} (Status: {})", YAHOO_FINANCE_URL, response.statusCode());

    if (response.statusCode() >= 400) {
      throw new IOException(
          "HTTP request failed with status code: " + response.statusCode() + " - Response: " + response.body());
    }
  }

  // Helper methods for creating common query operands

  /**
   * Creates a QueryOperand for equality comparison
   */
  protected QueryOperand createEqualsOperand(String field, String value) {
    QueryOperand operand = new QueryOperand();
    operand.operator = "eq";
    operand.operands = Arrays.asList(field, value);
    return operand;
  }

  /**
   * Creates a QueryOperand for greater-than-or-equal comparison
   */
  protected QueryOperand createGteOperand(String field, String value) {
    QueryOperand operand = new QueryOperand();
    operand.operator = "gte";
    operand.operands = Arrays.asList(field, value);
    return operand;
  }

  /**
   * Creates a QueryOperand for less-than comparison
   */
  protected QueryOperand createLtOperand(String field, String value) {
    QueryOperand operand = new QueryOperand();
    operand.operator = "lt";
    operand.operands = Arrays.asList(field, value);
    return operand;
  }

  /**
   * Creates a basic YahooFinanceDTO with common fields
   */
  protected YahooFinanceDTO createBaseRequest(String sortType, String entityIdType, String sortField,
      List<String> includeFields, int size) {
    YahooFinanceDTO request = new YahooFinanceDTO();
    request.sortType = sortType;
    request.entityIdType = entityIdType;
    request.sortField = sortField;
    request.includeFields = includeFields;
    request.offset = "0";
    request.size = size;
    return request;
  }

  // Abstract methods to be implemented by subclasses

  /**
   * Creates the specific request for the Yahoo Finance API
   * 
   * @param requestParams Parameters needed to build the request
   * @return Configured YahooFinanceDTO request
   */
  protected abstract YahooFinanceDTO createRequest(Object requestParams);

  /**
   * Parses the response from Yahoo Finance API
   * 
   * @param responseBody  JSON response body
   * @param requestParams Original request parameters for context
   * @return Parsed response data
   * @throws IOException if parsing fails
   */
  protected abstract <T> T parseResponse(String responseBody, Object requestParams) throws IOException;
}
