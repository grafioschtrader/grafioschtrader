package grafioschtrader.connector.instrument.generic;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import grafiosch.common.UserAccessHelper;
import grafiosch.entities.ConnectorApiKey;
import grafiosch.entities.User;
import grafiosch.repository.ConnectorApiKeyJpaRepository;
import grafioschtrader.entities.GenericConnectorDef;
import grafioschtrader.entities.GenericConnectorEndpoint;
import grafioschtrader.repository.GenericConnectorDefJpaRepository;
import grafioschtrader.types.TickerBuildStrategy;

/**
 * Orchestrates a test request against a persisted generic connector endpoint. Loads the connector definition from
 * the database, verifies ownership or admin access, builds the ticker string, creates a temporary GenericFeedConnector
 * instance, and delegates to its testEndpoint() method. No side effects on activation or endpoint usage state.
 */
@Service
public class GenericConnectorTestService {

  @Autowired
  private GenericConnectorDefJpaRepository genericConnectorDefJpaRepository;

  @Autowired
  private ConnectorApiKeyJpaRepository connectorApiKeyJpaRepository;

  /**
   * Executes a test request for the specified connector endpoint.
   *
   * @param request contains the connector ID, endpoint type, ticker/currency inputs, and optional date range
   * @return test result with request URL, HTTP status, raw response, parsed data, and timing
   */
  public GenericConnectorTestResult testEndpoint(GenericConnectorTestRequest request) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();

    GenericConnectorDef def = genericConnectorDefJpaRepository.findById(request.getIdGenericConnector())
        .orElseThrow(() -> new SecurityException("Connector not found"));

    if (!UserAccessHelper.hasHigherPrivileges(user) && !UserAccessHelper.hasRightsForEditingOrDeleteOnEntity(user, def)) {
      throw new SecurityException("No access to test this connector");
    }

    GenericConnectorEndpoint endpoint = findMatchingEndpoint(def, request.getFeedSupport(),
        request.getInstrumentType());
    if (endpoint == null) {
      return GenericConnectorTestResult.error(null, 0, null,
          "No endpoint for " + request.getFeedSupport() + " + " + request.getInstrumentType(), 0);
    }

    String ticker = buildTestTicker(endpoint, request);

    String apiKey = null;
    if (def.isNeedsApiKey() && !def.hasAutoToken()) {
      Optional<ConnectorApiKey> keyOpt = connectorApiKeyJpaRepository.findById(def.getShortId());
      if (keyOpt.isPresent()) {
        apiKey = keyOpt.get().getApiKey();
      }
    }

    GenericFeedConnector tempConnector = new GenericFeedConnector(def, apiKey);
    return tempConnector.testEndpoint(request.getFeedSupport(), request.getInstrumentType(), ticker,
        request.getFromDate(), request.getToDate(), request.getFromCurrency(), request.getToCurrency());
  }

  private GenericConnectorEndpoint findMatchingEndpoint(GenericConnectorDef def, String feedSupport,
      String instrumentType) {
    if (def.getEndpoints() != null) {
      for (GenericConnectorEndpoint ep : def.getEndpoints()) {
        if (feedSupport.equals(ep.getFeedSupport()) && instrumentType.equals(ep.getInstrumentType())) {
          return ep;
        }
      }
    }
    return null;
  }

  private String buildTestTicker(GenericConnectorEndpoint endpoint, GenericConnectorTestRequest request) {
    String ticker;
    if (endpoint.getTickerBuildStrategy() == TickerBuildStrategy.CURRENCY_PAIR
        && "CURRENCY".equals(request.getInstrumentType())
        && request.getFromCurrency() != null && request.getToCurrency() != null) {
      String sep = endpoint.getCurrencyPairSeparator() != null ? endpoint.getCurrencyPairSeparator() : "";
      String suffix = endpoint.getCurrencyPairSuffix() != null ? endpoint.getCurrencyPairSuffix() : "";
      ticker = request.getFromCurrency() + sep + request.getToCurrency() + suffix;
    } else {
      ticker = request.getTicker() != null ? request.getTicker() : "";
    }
    if (endpoint.isTickerUppercase()) {
      ticker = ticker.toUpperCase();
    }
    return ticker;
  }
}
