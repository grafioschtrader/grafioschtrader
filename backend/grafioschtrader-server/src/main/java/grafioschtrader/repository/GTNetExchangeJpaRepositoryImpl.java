package grafioschtrader.repository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.BaseConstants;
import grafiosch.common.UserAccessHelper;
import grafiosch.entities.User;
import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.GTSecuritiyCurrencyExchange;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNetExchange;
import grafioschtrader.entities.GTNetSupplier;
import grafioschtrader.entities.GTNetSupplierDetail;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.gtnet.model.GTNetSupplierWithDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

/**
 * Implementation of custom repository methods for GTNetExchange.
 *
 * Provides methods for retrieving and managing GTNetExchange entries for securities and currency pairs, including batch
 * updates and supplier detail lookups.
 */
public class GTNetExchangeJpaRepositoryImpl implements GTNetExchangeJpaRepositoryCustom {

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  @Lazy
  private GTNetExchangeJpaRepository gtNetExchangeJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private GTNetSupplierJpaRepository gtNetSupplierJpaRepository;

  @Autowired
  private GTNetSupplierDetailJpaRepository gtNetSupplierDetailJpaRepository;

  @Override
  public GTSecuritiyCurrencyExchange<Security> getSecuritiesWithExchangeConfig(boolean activeOnly) {
    GTSecuritiyCurrencyExchange<Security> result = new GTSecuritiyCurrencyExchange<>();

    SimpleDateFormat sdf = new SimpleDateFormat(BaseConstants.STANDARD_DATE_FORMAT);
    Date date = new Date();
    try {
      if (!activeOnly) {
        date = sdf.parse(GlobalConstants.OLDEST_TRADING_DAY);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    result.securitiescurrenciesList = securityJpaRepository
        .findByActiveToDateAfterAndIdTenantPrivateIsNullAndStockexchange_secondaryMarketTrue(date);

    result.exchangeMap = gtNetExchangeJpaRepository.findAll().stream()
        .filter(e -> e.getSecuritycurrency() instanceof Security)
        .collect(Collectors.toMap(e -> e.getSecuritycurrency().getIdSecuritycurrency(), Function.identity()));

    result.idSecuritycurrenies = getIdSecuritycurrencyWithDetails(result.securitiescurrenciesList);

    return result;
  }

  @Override
  public GTSecuritiyCurrencyExchange<Currencypair> getCurrencypairsWithExchangeConfigFull() {
    GTSecuritiyCurrencyExchange<Currencypair> result = new GTSecuritiyCurrencyExchange<>();

    result.securitiescurrenciesList = currencypairJpaRepository.findAll();

    result.exchangeMap = gtNetExchangeJpaRepository.findAll().stream()
        .filter(e -> e.getSecuritycurrency() instanceof Currencypair)
        .collect(Collectors.toMap(e -> e.getSecuritycurrency().getIdSecuritycurrency(), Function.identity()));

    result.idSecuritycurrenies = getIdSecuritycurrencyWithDetails(result.securitiescurrenciesList);

    return result;
  }
  
  private <T extends Securitycurrency<T>> Set<Integer> getIdSecuritycurrencyWithDetails(
      List<T> securitiescurrenciesList) {
    List<Integer> idsToCheck = securitiescurrenciesList.stream().map(Securitycurrency::getIdSecuritycurrency)
        .collect(Collectors.toList());

    if (idsToCheck.isEmpty()) {
      return new HashSet<>();
    }
    return gtNetSupplierDetailJpaRepository.findIdSecuritycurrencyWithDetails(idsToCheck);
  }

  @Override
  @Transactional
  public List<GTNetExchange> batchUpdate(List<GTNetExchange> exchanges) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<GTNetExchange> resultList = new ArrayList<>();
    List<GTNetExchange> changedExchanges = new ArrayList<>();

    List<Integer> existingIds = exchanges.stream().filter(e -> e.getIdGtNetExchange() != null)
        .map(GTNetExchange::getIdGtNetExchange).collect(Collectors.toList());

    Map<Integer, GTNetExchange> existingMap = new HashMap<>();
    if (!existingIds.isEmpty()) {
      existingMap = gtNetExchangeJpaRepository.findAllById(existingIds).stream()
          .collect(Collectors.toMap(GTNetExchange::getIdGtNetExchange, Function.identity()));
    }

    List<Integer> newSecurityCurrencyIds = exchanges.stream()
        .filter(e -> e.getIdGtNetExchange() == null && e.getSecuritycurrency() != null)
        .map(e -> e.getSecuritycurrency().getIdSecuritycurrency()).collect(Collectors.toList());

    Map<Integer, Securitycurrency<?>> securityCurrencyMap = new HashMap<>();
    if (!newSecurityCurrencyIds.isEmpty()) {
      List<Securitycurrency> scList = entityManager
          .createQuery("SELECT sc FROM Securitycurrency sc WHERE sc.idSecuritycurrency IN :ids", Securitycurrency.class)
          .setParameter("ids", newSecurityCurrencyIds).getResultList();
      securityCurrencyMap = scList.stream()
          .collect(Collectors.toMap(Securitycurrency::getIdSecuritycurrency, sc -> sc));
    }

    for (GTNetExchange exchange : exchanges) {
      if (exchange.getIdGtNetExchange() != null) {
        // Update existing entry
        GTNetExchange existing = existingMap.get(exchange.getIdGtNetExchange());
        if (existing != null) {
          checkAccess(user, existing.getSecuritycurrency());
          boolean changed = false;

          if (existing.isLastpriceRecv() != exchange.isLastpriceRecv()) {
            existing.setLastpriceRecv(exchange.isLastpriceRecv());
            changed = true;
          }
          if (existing.isHistoricalRecv() != exchange.isHistoricalRecv()) {
            existing.setHistoricalRecv(exchange.isHistoricalRecv());
            changed = true;
          }
          if (existing.isLastpriceSend() != exchange.isLastpriceSend()) {
            existing.setLastpriceSend(exchange.isLastpriceSend());
            changed = true;
          }
          if (existing.isHistoricalSend() != exchange.isHistoricalSend()) {
            existing.setHistoricalSend(exchange.isHistoricalSend());
            changed = true;
          }

          if (changed) {
            changedExchanges.add(existing);
          }
          resultList.add(existing);
        }
      } else {
        // Create new entry (idGtNetExchange is null)
        Securitycurrency<?> sc = securityCurrencyMap.get(exchange.getSecuritycurrency().getIdSecuritycurrency());
        if (sc == null) {
          throw new IllegalArgumentException(
              "Securitycurrency not found for ID: " + exchange.getSecuritycurrency().getIdSecuritycurrency());
        }
        checkAccess(user, sc);

        GTNetExchange newExchange = new GTNetExchange();
        newExchange.setSecuritycurrency(sc);
        newExchange.setLastpriceRecv(exchange.isLastpriceRecv());
        newExchange.setHistoricalRecv(exchange.isHistoricalRecv());
        newExchange.setLastpriceSend(exchange.isLastpriceSend());
        newExchange.setHistoricalSend(exchange.isHistoricalSend());
        changedExchanges.add(newExchange);
        resultList.add(newExchange);
      }
    }

    if (!changedExchanges.isEmpty()) {
      gtNetExchangeJpaRepository.saveAll(changedExchanges);
    }
    return resultList;
  }

  private void checkAccess(User user, Securitycurrency<?> sc) {
    if (!UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, sc)) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
  }

  @Override
  @Transactional
  public GTNetExchange addSecurity(Integer idSecuritycurrency) {
    // Check if already exists
    Optional<GTNetExchange> existing = gtNetExchangeJpaRepository
        .findBySecuritycurrency_IdSecuritycurrency(idSecuritycurrency);
    if (existing.isPresent()) {
      throw new IllegalArgumentException("Security is already configured in GTNetExchange");
    }

    // Verify security exists
    Security security = securityJpaRepository.findByIdSecuritycurrency(idSecuritycurrency);
    if (security == null) {
      throw new IllegalArgumentException("Security not found: " + idSecuritycurrency);
    }

    GTNetExchange exchange = new GTNetExchange();
    exchange.setSecuritycurrency(security);
    exchange.setLastpriceRecv(false);
    exchange.setHistoricalRecv(false);
    exchange.setLastpriceSend(false);
    exchange.setHistoricalSend(false);

    return gtNetExchangeJpaRepository.save(exchange);
  }

  @Override
  @Transactional
  public GTNetExchange addCurrencypair(Integer idSecuritycurrency) {
    // Check if already exists
    Optional<GTNetExchange> existing = gtNetExchangeJpaRepository
        .findBySecuritycurrency_IdSecuritycurrency(idSecuritycurrency);
    if (existing.isPresent()) {
      throw new IllegalArgumentException("Currency pair is already configured in GTNetExchange");
    }

    // Verify currency pair exists
    Currencypair currencypair = currencypairJpaRepository.findByIdSecuritycurrency(idSecuritycurrency);
    if (currencypair == null) {
      throw new IllegalArgumentException("Currency pair not found: " + idSecuritycurrency);
    }

    GTNetExchange exchange = new GTNetExchange();
    exchange.setSecuritycurrency(currencypair);
    exchange.setLastpriceRecv(false);
    exchange.setHistoricalRecv(false);
    exchange.setLastpriceSend(false);
    exchange.setHistoricalSend(false);

    return gtNetExchangeJpaRepository.save(exchange);
  }

  @Override
  public List<GTNetSupplierWithDetails> getSupplierDetails(Integer idSecuritycurrency) {
    // Get all supplier details for this securitycurrency
    List<GTNetSupplierDetail> allDetails = gtNetSupplierDetailJpaRepository.findAll().stream()
        .filter(d -> d.getSecuritycurrency() != null
            && d.getSecuritycurrency().getIdSecuritycurrency().equals(idSecuritycurrency))
        .collect(Collectors.toList());

    if (allDetails.isEmpty()) {
      return List.of();
    }

    // Group by supplier ID
    Map<Integer, List<GTNetSupplierDetail>> detailsBySupplier = allDetails.stream()
        .collect(Collectors.groupingBy(GTNetSupplierDetail::getIdGtNetSupplier));

    // Build result list with supplier headers
    List<GTNetSupplierWithDetails> result = new ArrayList<>();
    for (Map.Entry<Integer, List<GTNetSupplierDetail>> entry : detailsBySupplier.entrySet()) {
      Optional<GTNetSupplier> supplierOpt = gtNetSupplierJpaRepository.findById(entry.getKey());
      if (supplierOpt.isPresent()) {
        result.add(new GTNetSupplierWithDetails(supplierOpt.get(), entry.getValue()));
      }
    }

    return result;
  }
}