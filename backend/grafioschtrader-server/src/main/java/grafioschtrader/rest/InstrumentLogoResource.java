package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.BaseConstants;
import grafiosch.common.DataHelper;
import grafiosch.common.UserAccessHelper;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.exceptions.ResourceNotFoundException;
import grafiosch.rest.UpdateCreateDeleteAuditResource;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafiosch.types.OperationType;
import grafioschtrader.config.LimitKeyConfig;
import grafioschtrader.dto.InstrumentLogoRefDto;
import grafioschtrader.entities.InstrumentLogo;
import grafioschtrader.repository.InstrumentLogoJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.service.InstrumentLogoChangedEvent;
import grafioschtrader.service.InstrumentLogoService;
import grafioschtrader.types.LogoScope;
import grafioschtrader.types.SpecialInvestmentInstruments;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Shared company and issuer SVG logo catalog API. Supports catalog search, backend logo resolution, authenticated
 * delivery of DB-stored SVGs, read-only upload/edit pre-validation for Dialog (D), and standard Grafioschtrader
 * Auditable create/update/delete rights ({@code ROLE_ADMIN} / {@code ROLE_ALL_EDIT} may edit or delete all entries;
 * {@code ROLE_LIMIT_EDIT} only entries they created themselves).
 */
@RestController
@RequestMapping(RequestGTMappings.INSTRUMENT_LOGO_MAP)
@Tag(name = RequestGTMappings.INSTRUMENT_LOGO, description = "Controller for shared company and issuer SVG logos")
public class InstrumentLogoResource extends UpdateCreateDeleteAuditResource<InstrumentLogo> {

  private static final MediaType MEDIA_TYPE_SVG_UTF8 = MediaType.parseMediaType("image/svg+xml;charset=UTF-8");
  private static final String SVG_CSP = "default-src 'none'; style-src 'unsafe-inline'; sandbox";

  @Autowired
  private InstrumentLogoJpaRepository instrumentLogoJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private InstrumentLogoService instrumentLogoService;

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @Operation(summary = "Search the shared logo catalog by name, exact ISIN and optional initial security context", tags = {
      RequestGTMappings.INSTRUMENT_LOGO })
  @GetMapping(value = "/search", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<InstrumentLogoRefDto>> search(@RequestParam(required = false) String name,
      @RequestParam(required = false) String isin, @RequestParam(required = false) LogoScope scope,
      @RequestParam(required = false) Integer idInstrumentLogo, @RequestParam(required = false) String productLink,
      @RequestParam(required = false) SpecialInvestmentInstruments specialInvestmentInstrument) {
    return ResponseEntity.ok(
        instrumentLogoService.search(name, isin, scope, idInstrumentLogo, productLink, specialInvestmentInstrument));
  }

  @Operation(summary = "Resolve the effective logo for an unsaved security form state or proposal comparison", tags = {
      RequestGTMappings.INSTRUMENT_LOGO })
  @GetMapping(value = "/resolve", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<InstrumentLogoRefDto> resolve(@RequestParam(required = false) Integer idInstrumentLogo,
      @RequestParam(required = false) String isin, @RequestParam(required = false) String name,
      @RequestParam(required = false) String productLink,
      @RequestParam(required = false) SpecialInvestmentInstruments specialInvestmentInstrument) {
    InstrumentLogoRefDto resolved = instrumentLogoService.resolve(idInstrumentLogo, isin, name, productLink,
        specialInvestmentInstrument);
    return resolved == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(resolved);
  }

  @Operation(summary = "Deliver the sanitized SVG payload of a DB-stored logo", tags = {
      RequestGTMappings.INSTRUMENT_LOGO })
  @GetMapping(value = "/{id}/svg", produces = "image/svg+xml")
  public ResponseEntity<String> getSvg(@PathVariable Integer id, @RequestParam(required = false) String v) {
    return instrumentLogoJpaRepository.findById(id).filter(logo -> logo.getSvgContent() != null).map(logo -> {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MEDIA_TYPE_SVG_UTF8);
      headers.set("X-Content-Type-Options", "nosniff");
      headers.set("Content-Security-Policy", SVG_CSP);
      if (v != null && v.equals(logo.getContentSha256())) {
        headers.setCacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePrivate().immutable());
      } else {
        headers.setCacheControl(CacheControl.noCache().cachePrivate());
      }
      return new ResponseEntity<>(logo.getSvgContent(), headers, HttpStatus.OK);
    }).orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Operation(summary = "Validate a new or edited DB-SVG logo (sanitizer, uniqueness and limits) without persisting it", tags = {
      RequestGTMappings.INSTRUMENT_LOGO })
  @PostMapping(value = "/validate", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> validate(@Valid @RequestBody InstrumentLogo entity) throws Exception {
    DataHelper.setEmptyStringToNullOrRemoveTraillingSpaces(entity);
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    checkDailyLimitOnCRUDOperations(entity, user);
    if (entity.getId() != null) {
      InstrumentLogo existing = instrumentLogoJpaRepository.findById(entity.getId())
          .orElseThrow(() -> new ResourceNotFoundException(entity.getId()));
      if (!UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, existing)) {
        throw new SecurityException(BaseConstants.RIGHTS_SECURITY_BREACH);
      }
      instrumentLogoJpaRepository.validateForUpdate(entity, existing);
    } else {
      if (!entityLimitService.fitsWithinLimit(user, LimitKeyConfig.KEY_INSTRUMENT_LOGO, null, 1)) {
        throw new SecurityException(BaseConstants.LIMIT_SECURITY_BREACH);
      }
      instrumentLogoJpaRepository.validateForCreate(entity);
    }
    return ResponseEntity.noContent().build();
  }

  /**
   * Updates an existing catalog logo following standard Grafioschtrader Auditable rights: {@code ROLE_ADMIN} and
   * {@code ROLE_ALL_EDIT} may update any entry, whereas {@code ROLE_LIMIT_EDIT} may only update entries they created
   * themselves. Direct rejection ({@code RIGHTS_SECURITY_BREACH}) is used instead of a {@code ProposeChangeEntity}
   * because catalog logos have no separate proposal-review screen.
   */
  @Override
  protected ResponseEntity<InstrumentLogo> updateEntity(final InstrumentLogo entity) throws Exception {
    DataHelper.setEmptyStringToNullOrRemoveTraillingSpaces(entity);
    if (entity.getId() == null) {
      return create(entity);
    }
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    InstrumentLogo existing = instrumentLogoJpaRepository.findById(entity.getId())
        .orElseThrow(() -> new ResourceNotFoundException(entity.getId()));
    if (!UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, existing)) {
      throw new SecurityException(BaseConstants.RIGHTS_SECURITY_BREACH);
    }
    checkDailyLimitOnCRUDOperations(entity, user);
    ResponseEntity<InstrumentLogo> result = updateSaveEntity(entity, existing);
    logAddUpdDel(user.getIdUser(), entity, OperationType.UPDATE);
    return result;
  }

  /**
   * Deletes an unreferenced catalog logo following standard Auditable rights and invalidates the metadata cache.
   * Rejects deletion with a translated violation error when at least one security still references the logo.
   */
  @Override
  protected Optional<InstrumentLogo> deleteById(final Integer id) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Optional<InstrumentLogo> existingOpt = instrumentLogoJpaRepository.findById(id);
    if (existingOpt.isPresent() && hasRightsForDeleteEntity(user, existingOpt.get())
        && securityJpaRepository.existsByIdInstrumentLogo(id)) {
      throw new DataViolationException("id.instrument.logo", "gt.logo.in.use", null);
    }
    Optional<InstrumentLogo> deleted = super.deleteById(id);
    if (deleted.isPresent()) {
      eventPublisher.publishEvent(new InstrumentLogoChangedEvent(id));
    }
    return deleted;
  }

  @Override
  protected UpdateCreateJpaRepository<InstrumentLogo> getUpdateCreateJpaRepository() {
    return instrumentLogoJpaRepository;
  }
}
