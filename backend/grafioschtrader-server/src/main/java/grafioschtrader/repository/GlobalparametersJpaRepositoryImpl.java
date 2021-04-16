package grafioschtrader.repository;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.common.UserAccessHelper;
import grafioschtrader.dto.TenantLimit;
import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.Globalparameters;
import grafioschtrader.entities.User;

public class GlobalparametersJpaRepositoryImpl implements GlobalparametersJpaRepositoryCustom {

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Override
  public int getMaxValueByKey(String key) {
    return TenantLimitsHelper.getMaxValueByKey(globalparametersJpaRepository, key);
  }

  @Override
  public EntityManager getEntityManager() {
    return entityManager;
  }

  @Override
  public List<TenantLimit> getMaxTenantLimitsByMsgKeys(List<String> msgKeys) {
    return TenantLimitsHelper.getMaxTenantLimitsByMsgKeys(globalparametersJpaRepository, msgKeys);
  }

  @Override
  public int getSecurityCurrencyIntradayUpdateTimeout() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_SC_INTRA_UPDATE_TIMEOUT_SECONDS)
        .map(Globalparameters::getPropertyInt).orElse(Globalparameters.DEFAULT_SC_INTRA_UPDATE_TIMEOUT_SECONDS);
  }

  @Override
  public int getWatchlistIntradayUpdateTimeout() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_W_INTRA_UPDATE_TIMEOUT_SECONDS)
        .map(Globalparameters::getPropertyInt).orElse(Globalparameters.DEFAULT_W_INTRA_UPDATE_TIMEOUT_SECONDS);
  }

  @Override
  public int getMaxLimitExceededCount() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_MAX_LIMIT_EXCEEDED_COUNT)
        .map(Globalparameters::getPropertyInt).orElse(Globalparameters.DEFAULT_MAX_LIMIT_EXCEEDED_COUNT);
  }

  @Override
  public int getMaxSecurityBreachCount() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_MAX_SECURITY_BREACH_COUNT)
        .map(Globalparameters::getPropertyInt).orElse(Globalparameters.DEFAULT_MAX_SECURITY_BREACH_COUNT);
  }

  @Override
  public short getMaxHistoryRetry() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_HISTORY_RETRY)
        .map(g -> g.getPropertyInt().shortValue()).orElse(Globalparameters.DEFAULT_HISTORY_RETRY);
  }

  @Override
  public short getMaxIntraRetry() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_INTRA_RETRY)
        .map(g -> g.getPropertyInt().shortValue()).orElse(Globalparameters.DEFAULT_INTRA_RETRY);
  }

  @Override
  public int getMaxFillDaysCurrency() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_HISTORY_MAX_FILLDAYS_CURRENCY)
        .map(Globalparameters:: getPropertyInt).orElse(Globalparameters.DEFAULT_HISTORY_MAX_FILLDAYS_CURRENCY);
  }

  @Override
  public Date getStartFeedDate() throws ParseException {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_START_FEED_DATE)
        .map(g -> DateHelper.getDateFromLocalDate(g.getPropertyDate())).orElse(Globalparameters.DEFAULT_START_FEED_DATE);
  }

  @Override
  public List<ValueKeyHtmlSelectOptions> getAllZoneIds() {
    final List<ValueKeyHtmlSelectOptions> dropdownValues = new ArrayList<>();
    final LocalDateTime dt = LocalDateTime.now();

    for (final String zoneId : ZoneId.getAvailableZoneIds()) {
      final ZoneId zone = ZoneId.of(zoneId);
      final ZonedDateTime zdt = dt.atZone(zone);
      final ZoneOffset zos = zdt.getOffset();
      final String offset = zos.getId().replaceAll("Z", "+00:00");
      dropdownValues.add(new ValueKeyHtmlSelectOptions(zone.toString(), zone.toString() + " (UTC" + offset + ")"));
    }
    return dropdownValues.stream().sorted((zone1, zone2) -> zone1.value.compareTo(zone2.value))
        .collect(Collectors.toList());
  }

  @Override
  public List<ValueKeyHtmlSelectOptions> getSupportedLocales() {
    final List<ValueKeyHtmlSelectOptions> dropdownValues = new ArrayList<>();
    for (final Locale loc : Locale.getAvailableLocales()) {
      if (GlobalConstants.GT_LANGUAGE_CODES.contains(loc.getLanguage())) {
        final String localeString = loc.toString().replace("_", "-");
        dropdownValues.add(new ValueKeyHtmlSelectOptions(localeString, localeString));
      }
    }
    return dropdownValues.stream().sorted((x, y) -> x.value.compareTo(y.value)).collect(Collectors.toList());
  }
  
  @Override
  public Globalparameters saveOnlyAttributes(Globalparameters updGp) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (UserAccessHelper.isAdmin(user)) {
      Optional<Globalparameters> existingGpOpt = globalparametersJpaRepository.findById(updGp.getPropertyName());
      if(existingGpOpt.isPresent()) {
        Globalparameters existingGp = existingGpOpt.get();
        existingGp.replaceExistingPropertyValue(updGp);
        return globalparametersJpaRepository.save(existingGp);
      }
    }
    throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
  }
  
}
