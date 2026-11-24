package grafioschtrader.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import grafiosch.dto.ProposeChangeEntityWithEntity;
import grafiosch.entities.ProposeChangeEntity;
import grafiosch.entities.User;
import grafiosch.repository.UserJpaRepository;
import grafiosch.rest.RequestMappings;
import grafiosch.types.ProposeDataChangeState;
import grafioschtrader.dto.InstrumentLogoRefDto;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.InstrumentLogo;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.service.InstrumentLogoService;
import grafioschtrader.types.DistributionFrequency;
import grafioschtrader.types.LogoScope;
import grafioschtrader.types.SpecialInvestmentInstruments;

@TestMethodOrder(OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class InstrumentLogoResourceTest extends BaseIntegrationTest {

  private static final String VALID_SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 64 24\">"
      + "<rect width=\"64\" height=\"24\" fill=\"#004488\"/></svg>";

  private static final String VALID_SVG_UPDATED = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 48 24\">"
      + "<rect width=\"48\" height=\"24\" fill=\"#008844\"/></svg>";

  private static final String UNSAFE_SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 64 24\">"
      + "<script>alert(1)</script></svg>";

  // Valid ISO 6166 test ISIN not present in the deployed catalog (Luhn check digit: 3328000000000 -> 9).
  private static final String FREE_TEST_ISIN = "XS0000000009";
  private static final String TEST_LOGO_NAME = "GT Integration Test Logo 1";
  private static final String TEST_LOGO_NAME_2 = "GT Integration Test Logo 2";

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private InstrumentLogoService instrumentLogoService;

  private final List<Integer> createdSecurityIds = new ArrayList<>();
  private final List<Integer> createdLogoIds = new ArrayList<>();
  private final List<Integer> createdProposalIds = new ArrayList<>();

  private Assetclass equityAssetclass;
  private Stockexchange stockexchange;
  private Integer uploadedLogoId;
  private String uploadedLogoSha256;
  private Security adminSharedSecurity;

  @BeforeAll
  void setUp() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);

    Assetclass[] assetclasses = authenticatedClient(RestTestHelper.LIMIT1).get().uri(RequestGTMappings.ASSETCLASS_MAP)
        .exchange().expectStatus().isOk().expectBody(Assetclass[].class).returnResult().getResponseBody();
    equityAssetclass = Arrays.stream(assetclasses)
        .filter(a -> a.getSpecialInvestmentInstrument() == SpecialInvestmentInstruments.DIRECT_INVESTMENT).findFirst()
        .orElseThrow();

    Stockexchange[] stockexchanges = authenticatedClient(RestTestHelper.LIMIT1).get()
        .uri(RequestGTMappings.STOCKEXCHANGE_MAP + "?includeNameOfCalendarIndex=false").exchange().expectStatus().isOk()
        .expectBody(Stockexchange[].class).returnResult().getResponseBody();
    stockexchange = Arrays.stream(stockexchanges).filter(se -> !se.isNoMarketValue()).findFirst().orElseThrow();
  }

  @AfterAll
  void cleanUp() {
    for (Integer proposalId : createdProposalIds) {
      jdbcTemplate.update("DELETE FROM propose_change_field WHERE id_propose_request = ?", proposalId);
      jdbcTemplate.update("DELETE FROM propose_change_entity WHERE id_propose_request = ?", proposalId);
      jdbcTemplate.update("DELETE FROM propose_request WHERE id_propose_request = ?", proposalId);
    }
    for (Integer securityId : createdSecurityIds) {
      jdbcTemplate.update("DELETE FROM historyquote_period WHERE id_securitycurrency = ?", securityId);
      jdbcTemplate.update("DELETE FROM security WHERE id_securitycurrency = ?", securityId);
      jdbcTemplate.update("DELETE FROM securitycurrency WHERE id_securitycurrency = ?", securityId);
    }
    for (Integer logoId : createdLogoIds) {
      jdbcTemplate.update("DELETE FROM instrument_logo WHERE id_instrument_logo = ?", logoId);
    }
    instrumentLogoService.invalidateCache();
  }

  @Test
  @Order(1)
  @DisplayName("Search supports partial name, issuer name_patterns, exact ISIN and productLink initial context")
  void searchCatalog() {
    // Partial name match
    InstrumentLogoRefDto[] appleHits = authenticatedClient(RestTestHelper.LIMIT1).get()
        .uri(RequestGTMappings.INSTRUMENT_LOGO_MAP + "/search?name={name}", "Apple").exchange().expectStatus().isOk()
        .expectBody(InstrumentLogoRefDto[].class).returnResult().getResponseBody();
    assertThat(appleHits).isNotEmpty();
    assertThat(appleHits).anyMatch(l -> "US0378331005".equals(l.isin()) && l.assetPath() != null);

    // Full ETF name finds short issuer name via name_patterns
    InstrumentLogoRefDto[] etfHits = authenticatedClient(RestTestHelper.LIMIT1).get()
        .uri(RequestGTMappings.INSTRUMENT_LOGO_MAP + "/search?name={name}&specialInvestmentInstrument=ETF",
            "iShares Core MSCI World UCITS ETF")
        .exchange().expectStatus().isOk().expectBody(InstrumentLogoRefDto[].class).returnResult().getResponseBody();
    assertThat(etfHits).isNotEmpty();
    assertThat(etfHits[0].name()).isEqualTo("iShares");
    assertThat(etfHits[0].logoScope()).isEqualTo(LogoScope.ISSUER);

    // ProductLink-only match on initial dialog open (ComStage ETF hosted on Lyxor)
    InstrumentLogoRefDto[] hostHits = authenticatedClient(RestTestHelper.LIMIT1).get()
        .uri(
            RequestGTMappings.INSTRUMENT_LOGO_MAP
                + "/search?name={name}&productLink={link}&specialInvestmentInstrument=ETF",
            "ComStage DAX UCITS ETF", "https://www.lyxoretf.ch/de/product")
        .exchange().expectStatus().isOk().expectBody(InstrumentLogoRefDto[].class).returnResult().getResponseBody();
    assertThat(hostHits).isNotEmpty();
    assertThat(hostHits[0].name()).isEqualTo("Lyxor");

    // Exact ISIN matches, partial ISIN returns empty
    InstrumentLogoRefDto[] exactIsin = authenticatedClient(RestTestHelper.LIMIT1).get()
        .uri(RequestGTMappings.INSTRUMENT_LOGO_MAP + "/search?isin={isin}", "US0378331005").exchange().expectStatus()
        .isOk().expectBody(InstrumentLogoRefDto[].class).returnResult().getResponseBody();
    assertThat(exactIsin).hasSize(1);
    assertThat(exactIsin[0].isin()).isEqualTo("US0378331005");

    InstrumentLogoRefDto[] partialIsin = authenticatedClient(RestTestHelper.LIMIT1).get()
        .uri(RequestGTMappings.INSTRUMENT_LOGO_MAP + "/search?isin={isin}", "US037833").exchange().expectStatus().isOk()
        .expectBody(InstrumentLogoRefDto[].class).returnResult().getResponseBody();
    assertThat(partialIsin).isEmpty();
  }

  @Test
  @Order(2)
  @DisplayName("Validate endpoint checks sanitizer and uniqueness without writing any row")
  void validateWithoutPersisting() {
    Integer countBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM instrument_logo", Integer.class);

    Map<String, Object> draft = newDraft(TEST_LOGO_NAME, FREE_TEST_ISIN, LogoScope.COMPANY, VALID_SVG);
    authenticatedClient(RestTestHelper.LIMIT1).post().uri(RequestGTMappings.INSTRUMENT_LOGO_MAP + "/validate")
        .body(draft).exchange().expectStatus().isNoContent();

    Integer countAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM instrument_logo", Integer.class);
    assertThat(countAfter).isEqualTo(countBefore);

    // Unsafe SVG rejected
    Map<String, Object> unsafeDraft = newDraft(TEST_LOGO_NAME, null, LogoScope.COMPANY, UNSAFE_SVG);
    authenticatedClient(RestTestHelper.LIMIT1).post().uri(RequestGTMappings.INSTRUMENT_LOGO_MAP + "/validate")
        .body(unsafeDraft).exchange().expectStatus().isBadRequest();

    // Duplicate deployed ISIN rejected
    Map<String, Object> dupIsinDraft = newDraft(TEST_LOGO_NAME, "US0378331005", LogoScope.COMPANY, VALID_SVG);
    authenticatedClient(RestTestHelper.LIMIT1).post().uri(RequestGTMappings.INSTRUMENT_LOGO_MAP + "/validate")
        .body(dupIsinDraft).exchange().expectStatus().isBadRequest();
  }

  @Test
  @Order(3)
  @DisplayName("POST creates DB-SVG, serves it with security headers, PUT updates follow Auditable rights, and DB-SVG does not hijack auto-resolve")
  void createServeUpdateWithRightsAndVerifyNoAutoResolveHijack() {
    Map<String, Object> toCreate = newDraft(TEST_LOGO_NAME, FREE_TEST_ISIN, LogoScope.COMPANY, VALID_SVG);
    InstrumentLogo created = authenticatedClient(RestTestHelper.LIMIT1).post()
        .uri(RequestGTMappings.INSTRUMENT_LOGO_MAP).body(toCreate).exchange().expectStatus().isOk()
        .expectBody(InstrumentLogo.class).returnResult().getResponseBody();

    assertThat(created).isNotNull();
    assertThat(created.getIdInstrumentLogo()).isNotNull();
    assertThat(created.getSvgContent()).isNull(); // WRITE_ONLY
    assertThat(created.getAssetPath()).isNull();
    assertThat(created.getContentSha256()).hasSize(64);
    uploadedLogoId = created.getIdInstrumentLogo();
    uploadedLogoSha256 = created.getContentSha256();
    createdLogoIds.add(uploadedLogoId);

    // Deliver SVG with security & immutable cache headers
    var svgResult = authenticatedClient(RestTestHelper.LIMIT1).get()
        .uri(RequestGTMappings.INSTRUMENT_LOGO_MAP + "/{id}/svg?v={v}", uploadedLogoId, uploadedLogoSha256).exchange()
        .expectStatus().isOk().expectHeader().contentTypeCompatibleWith(MediaType.parseMediaType("image/svg+xml"))
        .expectHeader().valueEquals("X-Content-Type-Options", "nosniff").expectHeader()
        .exists("Content-Security-Policy").expectHeader().valueMatches("Cache-Control", ".*immutable.*")
        .expectBody(String.class).returnResult();
    assertThat(svgResult.getResponseBody()).contains("<svg").contains("viewBox=\"0 0 64 24\"");

    // Deployed SVG returns 404 on /{id}/svg
    InstrumentLogoRefDto[] deployedApple = authenticatedClient(RestTestHelper.LIMIT1).get()
        .uri(RequestGTMappings.INSTRUMENT_LOGO_MAP + "/search?isin=US0378331005").exchange().expectStatus().isOk()
        .expectBody(InstrumentLogoRefDto[].class).returnResult().getResponseBody();
    authenticatedClient(RestTestHelper.LIMIT1).get()
        .uri(RequestGTMappings.INSTRUMENT_LOGO_MAP + "/{id}/svg", deployedApple[0].idInstrumentLogo()).exchange()
        .expectStatus().isNotFound();

    // Duplicate name and duplicate ISIN rejected
    authenticatedClient(RestTestHelper.LIMIT1).post().uri(RequestGTMappings.INSTRUMENT_LOGO_MAP)
        .body(newDraft(TEST_LOGO_NAME, null, LogoScope.COMPANY, VALID_SVG)).exchange().expectStatus().isBadRequest();
    authenticatedClient(RestTestHelper.LIMIT1).post().uri(RequestGTMappings.INSTRUMENT_LOGO_MAP)
        .body(newDraft(TEST_LOGO_NAME_2, FREE_TEST_ISIN, LogoScope.COMPANY, VALID_SVG)).exchange().expectStatus()
        .isBadRequest();

    // Another ROLE_LIMIT_EDIT user (LIMIT2) cannot update LIMIT1's logo, and LIMIT1 cannot update a deployed logo
    Map<String, Object> foreignUpdate = newUpdatePayload(uploadedLogoId, TEST_LOGO_NAME, FREE_TEST_ISIN,
        LogoScope.COMPANY, VALID_SVG_UPDATED);
    authenticatedClient(RestTestHelper.LIMIT2).put().uri(RequestGTMappings.INSTRUMENT_LOGO_MAP).body(foreignUpdate)
        .exchange().expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);

    Map<String, Object> deployedUpdate = newUpdatePayload(deployedApple[0].idInstrumentLogo(), deployedApple[0].name(),
        deployedApple[0].isin(), LogoScope.COMPANY, VALID_SVG_UPDATED);
    authenticatedClient(RestTestHelper.LIMIT1).put().uri(RequestGTMappings.INSTRUMENT_LOGO_MAP).body(deployedUpdate)
        .exchange().expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);

    // Owner (LIMIT1) can replace SVG in place while keeping the same name and ISIN
    InstrumentLogo updatedByOwner = authenticatedClient(RestTestHelper.LIMIT1).put()
        .uri(RequestGTMappings.INSTRUMENT_LOGO_MAP).body(foreignUpdate).exchange().expectStatus().isOk()
        .expectBody(InstrumentLogo.class).returnResult().getResponseBody();
    assertThat(updatedByOwner).isNotNull();
    assertThat(updatedByOwner.getIdInstrumentLogo()).isEqualTo(uploadedLogoId);
    assertThat(updatedByOwner.getContentSha256()).isNotEqualTo(uploadedLogoSha256);
    uploadedLogoSha256 = updatedByOwner.getContentSha256();

    var updatedSvgResult = authenticatedClient(RestTestHelper.LIMIT1).get()
        .uri(RequestGTMappings.INSTRUMENT_LOGO_MAP + "/{id}/svg?v={v}", uploadedLogoId, uploadedLogoSha256).exchange()
        .expectStatus().isOk().expectBody(String.class).returnResult();
    assertThat(updatedSvgResult.getResponseBody()).contains("viewBox=\"0 0 48 24\"");

    // ROLE_ALL_EDIT can update any logo (metadata-only update preserves existing SVG and hash)
    Map<String, Object> metaOnlyUpdate = newUpdatePayload(uploadedLogoId, TEST_LOGO_NAME + " Edited", FREE_TEST_ISIN,
        LogoScope.COMPANY, null);
    InstrumentLogo updatedByAllEdit = authenticatedClient(RestTestHelper.ALLEDIT).put()
        .uri(RequestGTMappings.INSTRUMENT_LOGO_MAP).body(metaOnlyUpdate).exchange().expectStatus().isOk()
        .expectBody(InstrumentLogo.class).returnResult().getResponseBody();
    assertThat(updatedByAllEdit).isNotNull();
    assertThat(updatedByAllEdit.getName()).isEqualTo(TEST_LOGO_NAME + " Edited");
    assertThat(updatedByAllEdit.getContentSha256()).isEqualTo(uploadedLogoSha256);

    // DB-SVG with free ISIN must NOT trigger automatic Stage-2 resolution without explicit assignment
    authenticatedClient(RestTestHelper.LIMIT1).get()
        .uri(RequestGTMappings.INSTRUMENT_LOGO_MAP + "/resolve?isin={isin}&name=ForeignCo", FREE_TEST_ISIN).exchange()
        .expectStatus().isNoContent();

    // Explicit resolution works immediately and carries createdBy for UI permission checks
    InstrumentLogoRefDto explicit = authenticatedClient(RestTestHelper.LIMIT1).get()
        .uri(RequestGTMappings.INSTRUMENT_LOGO_MAP + "/resolve?idInstrumentLogo={id}", uploadedLogoId).exchange()
        .expectStatus().isOk().expectBody(InstrumentLogoRefDto.class).returnResult().getResponseBody();
    assertThat(explicit).isNotNull();
    assertThat(explicit.idInstrumentLogo()).isEqualTo(uploadedLogoId);
    assertThat(explicit.explicit()).isTrue();
    assertThat(explicit.createdBy()).isNotNull();
  }

  @Test
  @Order(4)
  @DisplayName("ROLE_LIMIT_EDIT saves private ISIN-less securities directly with shared logo (1:N) and rejects unknown logo ID")
  void privateSecurityDirectSaveAndReuse() {
    User limit1User = userJpaRepository.findByNickname(RestTestHelper.LIMIT1).orElseThrow();
    assertThat(limit1User.getIdTenant()).isNotNull();

    // Unknown logo ID is rejected by SecurityJpaRepositoryImpl.beforeSave
    Security invalidLogoSec = newTestSecurity("GT Logo Invalid Ref", "CHF", limit1User.getIdTenant(), 999_999_999);
    authenticatedClient(RestTestHelper.LIMIT1).post().uri(RequestGTMappings.SECURITY_MAP).body(invalidLogoSec)
        .exchange().expectStatus().isBadRequest();

    // First private security (without ISIN) created directly with uploaded logo
    Security priv1 = newTestSecurity("GT Logo Private Sec 1", "CHF", limit1User.getIdTenant(), uploadedLogoId);
    Security saved1 = authenticatedClient(RestTestHelper.LIMIT1).post().uri(RequestGTMappings.SECURITY_MAP).body(priv1)
        .exchange().expectStatus().isOk().expectBody(Security.class).returnResult().getResponseBody();
    assertThat(saved1).isNotNull();
    createdSecurityIds.add(saved1.getIdSecuritycurrency());
    assertThat(saved1.getIsin()).isNull();
    assertThat(saved1.getIdInstrumentLogo()).isEqualTo(uploadedLogoId);

    // GET /api/security/{id} serializes resolvedLogo with explicit=true (READ_ONLY on Security, so assert JSON payload)
    authenticatedClient(RestTestHelper.LIMIT1).get()
        .uri(RequestGTMappings.SECURITY_MAP + "/{id}", saved1.getIdSecuritycurrency()).exchange().expectStatus().isOk()
        .expectBody().jsonPath("$.resolvedLogo.idInstrumentLogo").isEqualTo(uploadedLogoId)
        .jsonPath("$.resolvedLogo.explicit").isEqualTo(true);

    // Reuse the same logo on a second private security (1:N)
    Security priv2 = newTestSecurity("GT Logo Private Sec 2", "CHF", limit1User.getIdTenant(), uploadedLogoId);
    Security saved2 = authenticatedClient(RestTestHelper.LIMIT1).post().uri(RequestGTMappings.SECURITY_MAP).body(priv2)
        .exchange().expectStatus().isOk().expectBody(Security.class).returnResult().getResponseBody();
    assertThat(saved2).isNotNull();
    createdSecurityIds.add(saved2.getIdSecuritycurrency());
    assertThat(saved2.getIdInstrumentLogo()).isEqualTo(uploadedLogoId);
  }

  @Test
  @Order(5)
  @DisplayName("ROLE_LIMIT_EDIT editing or resetting logo on foreign shared security creates ProposeChangeEntity")
  void foreignSharedSecurityCreatesProposalForAssignAndReset() {
    // ADMIN creates a shared security without explicit logo
    Security shared = newTestSecurity("GT Logo Admin Shared Sec", "CHF", null, null);
    adminSharedSecurity = authenticatedClient(RestTestHelper.ADMIN).post().uri(RequestGTMappings.SECURITY_MAP)
        .body(shared).exchange().expectStatus().isOk().expectBody(Security.class).returnResult().getResponseBody();
    assertThat(adminSharedSecurity).isNotNull();
    createdSecurityIds.add(adminSharedSecurity.getIdSecuritycurrency());

    // LIMIT1 proposes assigning uploadedLogoId
    adminSharedSecurity.setIdInstrumentLogo(uploadedLogoId);
    adminSharedSecurity.setNoteRequestOrReject("Assign custom logo");
    authenticatedClient(RestTestHelper.LIMIT1).put().uri(RequestGTMappings.SECURITY_MAP).body(adminSharedSecurity)
        .exchange().expectStatus().isOk();

    // Security itself is still unchanged until ADMIN accepts
    Security beforeAccept = authenticatedClient(RestTestHelper.ADMIN).get()
        .uri(RequestGTMappings.SECURITY_MAP + "/{id}", adminSharedSecurity.getIdSecuritycurrency()).exchange()
        .expectStatus().isOk().expectBody(Security.class).returnResult().getResponseBody();
    assertThat(beforeAccept.getIdInstrumentLogo()).isNull();

    // ADMIN finds open proposal and accepts it
    ProposeChangeEntity assignProposal = findOpenProposalForSecurity(adminSharedSecurity.getIdSecuritycurrency());
    createdProposalIds.add(assignProposal.getIdProposeRequest());
    assertThat(assignProposal.getProposeChangeFieldList()).anyMatch(f -> "idInstrumentLogo".equals(f.getField()));

    beforeAccept.setIdInstrumentLogo(uploadedLogoId);
    beforeAccept.setIdProposeRequest(assignProposal.getIdProposeRequest());
    beforeAccept.setNoteRequestOrReject("Approved");
    Security afterAccept = authenticatedClient(RestTestHelper.ADMIN).put().uri(RequestGTMappings.SECURITY_MAP)
        .body(beforeAccept).exchange().expectStatus().isOk().expectBody(Security.class).returnResult()
        .getResponseBody();
    assertThat(afterAccept.getIdInstrumentLogo()).isEqualTo(uploadedLogoId);

    // LIMIT1 proposes resetting idInstrumentLogo back to null
    afterAccept.setIdInstrumentLogo(null);
    afterAccept.setIdProposeRequest(null);
    afterAccept.setNoteRequestOrReject("Reset to automatic");
    authenticatedClient(RestTestHelper.LIMIT1).put().uri(RequestGTMappings.SECURITY_MAP).body(afterAccept).exchange()
        .expectStatus().isOk();

    ProposeChangeEntity resetProposal = findOpenProposalForSecurity(adminSharedSecurity.getIdSecuritycurrency());
    createdProposalIds.add(resetProposal.getIdProposeRequest());
    assertThat(resetProposal.getProposeChangeFieldList()).anyMatch(f -> "idInstrumentLogo".equals(f.getField()));

    // ADMIN rejects the reset proposal -> logo stays assigned
    resetProposal.setDataChangeState(ProposeDataChangeState.REJECT);
    resetProposal.setNoteAcceptReject("Keep explicit logo");
    authenticatedClient(RestTestHelper.ADMIN).put().uri(RequestMappings.PROPOSECHANGEENTITY_MAP).body(resetProposal)
        .exchange().expectStatus().isOk();

    Security afterReject = authenticatedClient(RestTestHelper.ADMIN).get()
        .uri(RequestGTMappings.SECURITY_MAP + "/{id}", adminSharedSecurity.getIdSecuritycurrency()).exchange()
        .expectStatus().isOk().expectBody(Security.class).returnResult().getResponseBody();
    assertThat(afterReject.getIdInstrumentLogo()).isEqualTo(uploadedLogoId);
  }

  @Test
  @Order(6)
  @DisplayName("DELETE checks Auditable rights, blocks referenced logos, and deletes unreferenced logos cleanly")
  void deleteLogoChecksRightsAndReferences() {
    // Foreign ROLE_LIMIT_EDIT cannot delete LIMIT1's logo
    authenticatedClient(RestTestHelper.LIMIT2).delete()
        .uri(RequestGTMappings.INSTRUMENT_LOGO_MAP + "/{id}", uploadedLogoId).exchange().expectStatus()
        .isEqualTo(HttpStatus.UNAUTHORIZED);

    // Deleting a logo still referenced by securities returns 400 (gt.logo.in.use)
    authenticatedClient(RestTestHelper.LIMIT1).delete()
        .uri(RequestGTMappings.INSTRUMENT_LOGO_MAP + "/{id}", uploadedLogoId).exchange().expectStatus().isBadRequest();

    // Create a second unreferenced logo as LIMIT1, verify LIMIT2 gets 401, then delete as ALLEDIT -> 204
    InstrumentLogo disposable = authenticatedClient(RestTestHelper.LIMIT1).post()
        .uri(RequestGTMappings.INSTRUMENT_LOGO_MAP).body(newDraft(TEST_LOGO_NAME_2, null, LogoScope.COMPANY, VALID_SVG))
        .exchange().expectStatus().isOk().expectBody(InstrumentLogo.class).returnResult().getResponseBody();
    assertThat(disposable).isNotNull();
    Integer disposableId = disposable.getIdInstrumentLogo();
    createdLogoIds.add(disposableId);

    authenticatedClient(RestTestHelper.LIMIT2).delete()
        .uri(RequestGTMappings.INSTRUMENT_LOGO_MAP + "/{id}", disposableId).exchange().expectStatus()
        .isEqualTo(HttpStatus.UNAUTHORIZED);

    authenticatedClient(RestTestHelper.ALLEDIT).delete()
        .uri(RequestGTMappings.INSTRUMENT_LOGO_MAP + "/{id}", disposableId).exchange().expectStatus().isNoContent();

    // Metadata cache was invalidated -> resolve returns 204
    authenticatedClient(RestTestHelper.ADMIN).get()
        .uri(RequestGTMappings.INSTRUMENT_LOGO_MAP + "/resolve?idInstrumentLogo={id}", disposableId).exchange()
        .expectStatus().isNoContent();
  }

  private ProposeChangeEntity findOpenProposalForSecurity(Integer idSecuritycurrency) {
    ProposeChangeEntityWithEntity[] proposals = authenticatedClient(RestTestHelper.ADMIN).get()
        .uri(RequestMappings.PROPOSECHANGEENTITY_MAP + "/withentity").exchange().expectStatus().isOk()
        .expectBody(ProposeChangeEntityWithEntity[].class).returnResult().getResponseBody();
    assertThat(proposals).isNotNull();
    return Arrays.stream(proposals).map(p -> p.proposeChangeEntity)
        .filter(p -> Security.class.getSimpleName().equals(p.getEntity()) && idSecuritycurrency.equals(p.getIdEntity())
            && p.getDataChangeState() == ProposeDataChangeState.OPEN)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Expected open ProposeChangeEntity for security " + idSecuritycurrency));
  }

  private Map<String, Object> newDraft(String name, String isin, LogoScope scope, String svg) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", name);
    body.put("isin", isin);
    body.put("logoScope", scope.name());
    body.put("svgContent", svg);
    return body;
  }

  private Map<String, Object> newUpdatePayload(Integer id, String name, String isin, LogoScope scope, String svg) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("idInstrumentLogo", id);
    body.put("name", name);
    body.put("isin", isin);
    body.put("logoScope", scope.name());
    if (svg != null) {
      body.put("svgContent", svg);
    }
    return body;
  }

  private Security newTestSecurity(String name, String currency, Integer idTenantPrivate, Integer idInstrumentLogo) {
    Security security = new Security();
    security.setName(name);
    security.setCurrency(currency);
    security.setAssetClass(equityAssetclass);
    security.setStockexchange(stockexchange);
    security.setActiveFromDate(LocalDate.of(2020, 1, 1));
    security.setActiveToDate(LocalDate.of(2030, 12, 31));
    security.setDistributionFrequency(DistributionFrequency.DF_YEAR);
    security.setLeverageFactor(1f);
    security.setIdTenantPrivate(idTenantPrivate);
    security.setIdInstrumentLogo(idInstrumentLogo);
    return security;
  }
}
