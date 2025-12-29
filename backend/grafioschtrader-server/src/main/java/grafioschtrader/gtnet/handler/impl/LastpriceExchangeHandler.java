package grafioschtrader.gtnet.handler.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetEntity;
import grafioschtrader.entities.GTNetLastpriceCurrencypair;
import grafioschtrader.entities.GTNetLastpriceSecurity;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.AcceptRequestTypes;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.gtnet.MessageCategory;
import grafioschtrader.gtnet.handler.AbstractGTNetMessageHandler;
import grafioschtrader.gtnet.handler.GTNetMessageContext;
import grafioschtrader.gtnet.handler.HandlerResult;
import grafioschtrader.gtnet.m2m.model.InstrumentPriceDTO;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;
import grafioschtrader.gtnet.model.msg.LastpriceExchangeMsg;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.GTNetExchangeJpaRepository;
import grafioschtrader.repository.GTNetLastpriceCurrencypairJpaRepository;
import grafioschtrader.repository.GTNetLastpriceSecurityJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Handler for GT_NET_LASTPRICE_EXCHANGE_SEL_C requests from remote instances.
 *
 * Processes intraday price data requests and returns prices that are newer than the requester's timestamps. The handler
 * operates differently based on the local server's acceptRequest mode:
 * <ul>
 *   <li>AC_PUSH_OPEN: Queries GTNetLastprice* tables (shared price pool)</li>
 *   <li>AC_OPEN: Queries Security/Currencypair entities directly (local data only)</li>
 * </ul>
 *
 * @see GTNetMessageCodeType#GT_NET_LASTPRICE_EXCHANGE_SEL_C
 */
@Component
public class LastpriceExchangeHandler extends AbstractGTNetMessageHandler {

  private static final Logger log = LoggerFactory.getLogger(LastpriceExchangeHandler.class);

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private GTNetLastpriceSecurityJpaRepository gtNetLastpriceSecurityJpaRepository;

  @Autowired
  private GTNetLastpriceCurrencypairJpaRepository gtNetLastpriceCurrencypairJpaRepository;

  @Autowired
  private GTNetExchangeJpaRepository gtNetExchangeJpaRepository;

  @Override
  public GTNetMessageCodeType getSupportedMessageCode() {
    return GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_SEL_C;
  }

  @Override
  public MessageCategory getCategory() {
    return MessageCategory.REQUEST;
  }

  @Override
  public HandlerResult handle(GTNetMessageContext context) throws Exception {
    // Validate request
    GTNet myGTNet = context.getMyGTNet();
    if (myGTNet == null) {
      return new HandlerResult.ProcessingError("NO_LOCAL_GTNET", "Local GTNet configuration not found");
    }

    // Check if this server accepts lastprice requests
    Optional<GTNetEntity> lastpriceEntity = myGTNet.getEntity(GTNetExchangeKindType.LAST_PRICE);
    if (lastpriceEntity.isEmpty() || !lastpriceEntity.get().isAccepting()) {
      log.debug("Server not accepting lastprice requests");
      return new HandlerResult.ProcessingError("NOT_ACCEPTING", "This server is not accepting lastprice requests");
    }

    // Store incoming message for logging
    GTNetMessage storedRequest = storeIncomingMessage(context);

    // Parse request payload
    if (!context.hasPayload()) {
      return createEmptyResponse(context, storedRequest);
    }

    LastpriceExchangeMsg request = context.getPayloadAs(LastpriceExchangeMsg.class);
    if (request.isEmpty()) {
      return createEmptyResponse(context, storedRequest);
    }

    log.debug("Received lastprice request for {} securities and {} currencypairs",
        request.securities != null ? request.securities.size() : 0,
        request.currencypairs != null ? request.currencypairs.size() : 0);

    // Get IDs of instruments we're allowed to send
    Set<Integer> sendableIds = gtNetExchangeJpaRepository.findIdsWithLastpriceSend();

    // Determine data source based on accept mode
    AcceptRequestTypes acceptMode = lastpriceEntity.get().getAcceptRequest();
    LastpriceExchangeMsg response = new LastpriceExchangeMsg();

    if (acceptMode == AcceptRequestTypes.AC_PUSH_OPEN) {
      // Query from GTNetLastprice* tables (shared pool)
      response.securities = querySecuritiesFromPushPool(request.securities, sendableIds);
      response.currencypairs = queryCurrencypairsFromPushPool(request.currencypairs, sendableIds);
    } else {
      // Query from Security/Currencypair entities directly
      response.securities = querySecuritiesFromLocal(request.securities, sendableIds);
      response.currencypairs = queryCurrencypairsFromLocal(request.currencypairs, sendableIds);
    }

    log.info("Responding with {} securities and {} currencypairs to {}",
        response.securities.size(), response.currencypairs.size(),
        context.getRemoteGTNet() != null ? context.getRemoteGTNet().getDomainRemoteName() : "unknown");

    // Store response message
    GTNetMessage responseMsg = storeResponseMessage(context, GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_RESPONSE_S,
        null, null, storedRequest);

    // Create response envelope with payload
    MessageEnvelope envelope = createResponseEnvelopeWithPayload(context, responseMsg, response);
    return new HandlerResult.ImmediateResponse(envelope);
  }

  /**
   * Queries securities from the push-open pool (GTNetLastpriceSecurity table).
   */
  private List<InstrumentPriceDTO> querySecuritiesFromPushPool(List<InstrumentPriceDTO> requested,
      Set<Integer> sendableIds) {
    List<InstrumentPriceDTO> result = new ArrayList<>();
    if (requested == null || requested.isEmpty()) {
      return result;
    }

    for (InstrumentPriceDTO req : requested) {
      if (req.getIsin() == null || req.getCurrency() == null) {
        continue;
      }

      // Query from GTNetLastpriceSecurity - need to add a method that finds by single ISIN+currency
      List<String> isins = List.of(req.getIsin());
      List<String> currencies = List.of(req.getCurrency());
      List<GTNetLastpriceSecurity> prices =
          gtNetLastpriceSecurityJpaRepository.getLastpricesByListByIsinsAndCurrencies(isins, currencies);

      for (GTNetLastpriceSecurity price : prices) {
        if (isNewer(price.getTimestamp(), req.getTimestamp())) {
          result.add(fromGTNetLastpriceSecurity(price));
        }
      }
    }
    return result;
  }

  /**
   * Queries currency pairs from the push-open pool (GTNetLastpriceCurrencypair table).
   */
  private List<InstrumentPriceDTO> queryCurrencypairsFromPushPool(List<InstrumentPriceDTO> requested,
      Set<Integer> sendableIds) {
    List<InstrumentPriceDTO> result = new ArrayList<>();
    if (requested == null || requested.isEmpty()) {
      return result;
    }

    for (InstrumentPriceDTO req : requested) {
      if (req.getCurrency() == null || req.getToCurrency() == null) {
        continue;
      }

      List<String> fromCurrencies = List.of(req.getCurrency());
      List<String> toCurrencies = List.of(req.getToCurrency());
      List<GTNetLastpriceCurrencypair> prices =
          gtNetLastpriceCurrencypairJpaRepository.getLastpricesByListByFromAndToCurrencies(fromCurrencies, toCurrencies);

      for (GTNetLastpriceCurrencypair price : prices) {
        if (isNewer(price.getTimestamp(), req.getTimestamp())) {
          result.add(fromGTNetLastpriceCurrencypair(price));
        }
      }
    }
    return result;
  }

  /**
   * Queries securities from local Security entities.
   */
  private List<InstrumentPriceDTO> querySecuritiesFromLocal(List<InstrumentPriceDTO> requested,
      Set<Integer> sendableIds) {
    List<InstrumentPriceDTO> result = new ArrayList<>();
    if (requested == null || requested.isEmpty()) {
      return result;
    }

    for (InstrumentPriceDTO req : requested) {
      if (req.getIsin() == null || req.getCurrency() == null) {
        continue;
      }

      Security security = securityJpaRepository.findByIsinAndCurrency(req.getIsin(), req.getCurrency());
      if (security == null) {
        continue;
      }

      // Check if we're allowed to send this instrument
      if (!sendableIds.isEmpty() && !sendableIds.contains(security.getIdSecuritycurrency())) {
        continue;
      }

      if (isNewer(security.getSTimestamp(), req.getTimestamp())) {
        result.add(InstrumentPriceDTO.fromSecurity(security));
      }
    }
    return result;
  }

  /**
   * Queries currency pairs from local Currencypair entities.
   */
  private List<InstrumentPriceDTO> queryCurrencypairsFromLocal(List<InstrumentPriceDTO> requested,
      Set<Integer> sendableIds) {
    List<InstrumentPriceDTO> result = new ArrayList<>();
    if (requested == null || requested.isEmpty()) {
      return result;
    }

    for (InstrumentPriceDTO req : requested) {
      if (req.getCurrency() == null || req.getToCurrency() == null) {
        continue;
      }

      Currencypair currencypair = currencypairJpaRepository.findByFromCurrencyAndToCurrency(
          req.getCurrency(), req.getToCurrency());
      if (currencypair == null) {
        continue;
      }

      // Check if we're allowed to send this instrument
      if (!sendableIds.isEmpty() && !sendableIds.contains(currencypair.getIdSecuritycurrency())) {
        continue;
      }

      if (isNewer(currencypair.getSTimestamp(), req.getTimestamp())) {
        result.add(InstrumentPriceDTO.fromCurrencypair(currencypair));
      }
    }
    return result;
  }

  /**
   * Checks if the local timestamp is newer than the requested timestamp.
   */
  private boolean isNewer(Date local, Date requested) {
    if (local == null) {
      return false;
    }
    if (requested == null) {
      return true; // Requester has no data, any local data is newer
    }
    return local.after(requested);
  }

  /**
   * Creates an InstrumentPriceDTO from a GTNetLastpriceSecurity entity.
   */
  private InstrumentPriceDTO fromGTNetLastpriceSecurity(GTNetLastpriceSecurity price) {
    InstrumentPriceDTO dto = new InstrumentPriceDTO();
    dto.setIsin(price.getIsin());
    dto.setCurrency(price.getCurrency());
    dto.setToCurrency(null);
    dto.setTimestamp(price.getTimestamp());
    dto.setOpen(price.getOpen());
    dto.setHigh(price.getHigh());
    dto.setLow(price.getLow());
    dto.setLast(price.getLast());
    dto.setVolume(price.getVolume());
    return dto;
  }

  /**
   * Creates an InstrumentPriceDTO from a GTNetLastpriceCurrencypair entity.
   */
  private InstrumentPriceDTO fromGTNetLastpriceCurrencypair(GTNetLastpriceCurrencypair price) {
    InstrumentPriceDTO dto = new InstrumentPriceDTO();
    dto.setIsin(null);
    dto.setCurrency(price.getFromCurrency());
    dto.setToCurrency(price.getToCurrency());
    dto.setTimestamp(price.getTimestamp());
    dto.setOpen(price.getOpen());
    dto.setHigh(price.getHigh());
    dto.setLow(price.getLow());
    dto.setLast(price.getLast());
    dto.setVolume(price.getVolume());
    return dto;
  }

  /**
   * Creates an empty response when no instruments were requested.
   */
  private HandlerResult createEmptyResponse(GTNetMessageContext context, GTNetMessage storedRequest) {
    GTNetMessage responseMsg = storeResponseMessage(context, GTNetMessageCodeType.GT_NET_LASTPRICE_EXCHANGE_RESPONSE_S,
        null, null, storedRequest);
    MessageEnvelope envelope = createResponseEnvelopeWithPayload(context, responseMsg, new LastpriceExchangeMsg());
    return new HandlerResult.ImmediateResponse(envelope);
  }
}
