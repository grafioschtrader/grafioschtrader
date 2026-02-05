package grafioschtrader.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import grafiosch.entities.GTNet;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.m2m.model.InstrumentPriceDTO;
import grafioschtrader.gtnet.model.msg.LastpriceExchangeMsg;

/**
 * Tracks PUSH_OPEN servers contacted during a GTNet lastprice exchange and records
 * the timestamps of prices each server originally sent. This context is used to determine
 * which prices should be pushed back to each server after the exchange completes.
 *
 * The push-back logic ensures that only prices that are newer than what each server
 * originally sent are included in the push, avoiding unnecessary network traffic
 * and potential timestamp conflicts.
 */
public class PushOpenServerContext {

  /**
   * Map of GTNet ID to a map of (instrument key -> received timestamp).
   * The instrument key format is "ISIN:CURRENCY" for securities or "FROM:TO" for currency pairs.
   */
  private final Map<Integer, Map<String, Date>> serverReceivedTimestamps = new HashMap<>();

  /** List of PUSH_OPEN servers that were contacted during the exchange. */
  private final List<GTNet> contactedServers = new ArrayList<>();

  /**
   * Records the response from a PUSH_OPEN server, storing the timestamps of prices received.
   *
   * @param server the GTNet server that sent the response
   * @param securities list of security prices received (may be null)
   * @param currencypairs list of currency pair prices received (may be null)
   */
  public void recordServerResponse(GTNet server, List<InstrumentPriceDTO> securities,
      List<InstrumentPriceDTO> currencypairs) {
    if (server == null) {
      return;
    }

    // Add to contacted servers if not already present
    if (contactedServers.stream().noneMatch(s -> s.getIdGtNet().equals(server.getIdGtNet()))) {
      contactedServers.add(server);
    }

    // Initialize or get the timestamp map for this server
    Map<String, Date> timestamps = serverReceivedTimestamps.computeIfAbsent(
        server.getIdGtNet(), k -> new HashMap<>());

    // Record security timestamps
    if (securities != null) {
      for (InstrumentPriceDTO dto : securities) {
        if (dto.getTimestamp() != null) {
          timestamps.put(dto.getKey(), dto.getTimestamp());
        }
      }
    }

    // Record currency pair timestamps
    if (currencypairs != null) {
      for (InstrumentPriceDTO dto : currencypairs) {
        if (dto.getTimestamp() != null) {
          timestamps.put(dto.getKey(), dto.getTimestamp());
        }
      }
    }
  }

  /**
   * Builds a LastpriceExchangeMsg containing only prices that are newer than what the
   * specified server originally sent. This ensures we only push updated prices.
   *
   * @param server the target PUSH_OPEN server
   * @param allSecurities all securities with final prices after the exchange
   * @param allCurrencypairs all currency pairs with final prices after the exchange
   * @return a message with prices to push, or an empty message if nothing is newer
   */
  public LastpriceExchangeMsg getPricesToPushForServer(GTNet server,
      List<Security> allSecurities, List<Currencypair> allCurrencypairs) {

    Map<String, Date> serverBaseline = serverReceivedTimestamps.get(server.getIdGtNet());

    List<InstrumentPriceDTO> securitiesToPush = new ArrayList<>();
    List<InstrumentPriceDTO> currencypairsToPush = new ArrayList<>();

    // Filter securities - push if newer than baseline or baseline is null
    if (allSecurities != null) {
      for (Security security : allSecurities) {
        if (security.getSTimestamp() == null || security.getSLast() == null) {
          continue;
        }

        InstrumentPriceDTO dto = InstrumentPriceDTO.fromSecurity(security);
        String key = dto.getKey();

        Date baseline = serverBaseline != null ? serverBaseline.get(key) : null;
        if (shouldPush(security.getSTimestamp(), baseline)) {
          securitiesToPush.add(dto);
        }
      }
    }

    // Filter currency pairs - push if newer than baseline or baseline is null
    if (allCurrencypairs != null) {
      for (Currencypair currencypair : allCurrencypairs) {
        if (currencypair.getSTimestamp() == null || currencypair.getSLast() == null) {
          continue;
        }

        InstrumentPriceDTO dto = InstrumentPriceDTO.fromCurrencypair(currencypair);
        String key = dto.getKey();

        Date baseline = serverBaseline != null ? serverBaseline.get(key) : null;
        if (shouldPush(currencypair.getSTimestamp(), baseline)) {
          currencypairsToPush.add(dto);
        }
      }
    }

    return LastpriceExchangeMsg.forRequest(securitiesToPush, currencypairsToPush);
  }

  /**
   * Determines if a price should be pushed based on comparing the current timestamp
   * against the server's baseline timestamp.
   *
   * @param current the current/final timestamp of the price
   * @param baseline the timestamp the server originally sent (null if server didn't have it)
   * @return true if the price should be pushed (newer or server didn't have it)
   */
  private boolean shouldPush(Date current, Date baseline) {
    // Push if server didn't have this instrument at all
    if (baseline == null) {
      return true;
    }
    // Push if our price is strictly newer
    return current.after(baseline);
  }

  /**
   * Checks if there are any PUSH_OPEN servers that were contacted and might need updates.
   *
   * @return true if at least one PUSH_OPEN server was contacted
   */
  public boolean hasServersToUpdate() {
    return !contactedServers.isEmpty();
  }

  /**
   * Returns the list of PUSH_OPEN servers that were contacted during the exchange.
   *
   * @return list of contacted GTNet servers
   */
  public List<GTNet> getContactedServers() {
    return contactedServers;
  }
}
