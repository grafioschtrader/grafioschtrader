package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.repository.RepositoryHelper;
import grafioschtrader.entities.InstrumentLogo;
import grafioschtrader.service.InstrumentLogoChangedEvent;
import grafioschtrader.service.LogoSvgSanitizer;
import grafioschtrader.service.LogoSvgSanitizer.SanitizedSvg;
import grafioschtrader.types.LogoScope;

public class InstrumentLogoJpaRepositoryImpl extends BaseRepositoryImpl<InstrumentLogo>
    implements InstrumentLogoJpaRepositoryCustom {

  @Autowired
  private InstrumentLogoJpaRepository instrumentLogoJpaRepository;

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @Override
  public void validateForCreate(InstrumentLogo instrumentLogo) {
    normalizeFields(instrumentLogo);
    stripSystemFields(instrumentLogo);
    if (instrumentLogo.getName() == null || instrumentLogo.getName().isEmpty()) {
      throw new DataViolationException("name", "NotNull", null);
    }
    SanitizedSvg sanitized = LogoSvgSanitizer.sanitize(instrumentLogo.getSvgContent());
    instrumentLogo.setSvgContent(sanitized.svgContent());
    instrumentLogo.setContentSha256(sanitized.contentSha256());

    if (instrumentLogoJpaRepository.existsByNameIgnoreCase(instrumentLogo.getName())) {
      throw new DataViolationException("name", "gt.logo.name.duplicate", null);
    }
    if (instrumentLogo.getIsin() != null
        && instrumentLogoJpaRepository.existsByIsinIgnoreCase(instrumentLogo.getIsin())) {
      throw new DataViolationException("isin", "gt.logo.isin.duplicate", null);
    }
  }

  @Override
  public void validateForUpdate(InstrumentLogo instrumentLogo, InstrumentLogo existingEntity) {
    normalizeFields(instrumentLogo);
    if (instrumentLogo.getName() == null || instrumentLogo.getName().isEmpty()) {
      throw new DataViolationException("name", "NotNull", null);
    }
    Integer existingId = existingEntity.getIdInstrumentLogo();
    if (instrumentLogoJpaRepository.existsByNameIgnoreCaseAndIdInstrumentLogoNot(instrumentLogo.getName(),
        existingId)) {
      throw new DataViolationException("name", "gt.logo.name.duplicate", null);
    }
    if (instrumentLogo.getIsin() != null && instrumentLogoJpaRepository
        .existsByIsinIgnoreCaseAndIdInstrumentLogoNot(instrumentLogo.getIsin(), existingId)) {
      throw new DataViolationException("isin", "gt.logo.isin.duplicate", null);
    }

    if (instrumentLogo.getSvgContent() != null && !instrumentLogo.getSvgContent().isBlank()) {
      SanitizedSvg sanitized = LogoSvgSanitizer.sanitize(instrumentLogo.getSvgContent());
      instrumentLogo.setSvgContent(sanitized.svgContent());
      instrumentLogo.setContentSha256(sanitized.contentSha256());
    } else {
      instrumentLogo.setSvgContent(existingEntity.getSvgContent());
      instrumentLogo.setContentSha256(existingEntity.getContentSha256());
    }
    // Preserve migration-managed fields from the stored entry so client payloads cannot tamper with them.
    instrumentLogo.setAssetPath(existingEntity.getAssetPath());
    instrumentLogo.setNamePatterns(existingEntity.getNamePatterns());
    instrumentLogo.setHosts(existingEntity.getHosts());
    instrumentLogo.setHostSuffixes(existingEntity.getHostSuffixes());
  }

  @Override
  public InstrumentLogo saveOnlyAttributes(InstrumentLogo newEntity, InstrumentLogo existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    if (existingEntity == null) {
      validateForCreate(newEntity);
    } else {
      validateForUpdate(newEntity, existingEntity);
    }
    try {
      InstrumentLogo saved;
      if (existingEntity == null) {
        saved = RepositoryHelper.saveOnlyAttributes(instrumentLogoJpaRepository, newEntity, null,
            updatePropertyLevelClasses);
      } else {
        existingEntity.setName(newEntity.getName());
        existingEntity.setIsin(newEntity.getIsin());
        existingEntity.setLogoScope(newEntity.getLogoScope());
        existingEntity.setSvgContent(newEntity.getSvgContent());
        existingEntity.setContentSha256(newEntity.getContentSha256());
        saved = instrumentLogoJpaRepository.save(existingEntity);
      }
      instrumentLogoJpaRepository.flush();
      eventPublisher.publishEvent(new InstrumentLogoChangedEvent(saved.getIdInstrumentLogo()));
      return saved;
    } catch (DataIntegrityViolationException e) {
      throw translateUniqueViolation(newEntity, existingEntity, e);
    }
  }

  private void normalizeFields(InstrumentLogo logo) {
    String rawName = logo.getName();
    logo.setName(rawName == null ? null : rawName.trim());

    String rawIsin = logo.getIsin();
    if (rawIsin == null || rawIsin.isBlank()) {
      logo.setIsin(null);
    } else {
      logo.setIsin(rawIsin.trim().toUpperCase(Locale.ROOT));
    }

    if (logo.getLogoScope() == null) {
      logo.setLogoScope(LogoScope.COMPANY);
    }
  }

  private void stripSystemFields(InstrumentLogo logo) {
    // Deployed asset paths and automatic issuer matching rules are exclusively populated by migrations.
    logo.setAssetPath(null);
    logo.setNamePatterns(null);
    logo.setHosts(null);
    logo.setHostSuffixes(null);
  }

  private RuntimeException translateUniqueViolation(InstrumentLogo logo, InstrumentLogo existingEntity,
      DataIntegrityViolationException cause) {
    String message = cause.getMostSpecificCause() != null ? cause.getMostSpecificCause().getMessage()
        : cause.getMessage();
    if (message != null) {
      String lower = message.toLowerCase(Locale.ROOT);
      if (lower.contains("uk_instrument_logo_isin")) {
        return new DataViolationException("isin", "gt.logo.isin.duplicate", null);
      }
      if (lower.contains("uk_instrument_logo_name")) {
        return new DataViolationException("name", "gt.logo.name.duplicate", null);
      }
    }
    Integer existingId = existingEntity != null ? existingEntity.getIdInstrumentLogo() : null;
    if (logo.getIsin() != null
        && (existingId == null ? instrumentLogoJpaRepository.existsByIsinIgnoreCase(logo.getIsin())
            : instrumentLogoJpaRepository.existsByIsinIgnoreCaseAndIdInstrumentLogoNot(logo.getIsin(), existingId))) {
      return new DataViolationException("isin", "gt.logo.isin.duplicate", null);
    }
    if (logo.getName() != null
        && (existingId == null ? instrumentLogoJpaRepository.existsByNameIgnoreCase(logo.getName())
            : instrumentLogoJpaRepository.existsByNameIgnoreCaseAndIdInstrumentLogoNot(logo.getName(), existingId))) {
      return new DataViolationException("name", "gt.logo.name.duplicate", null);
    }
    return cause;
  }
}
