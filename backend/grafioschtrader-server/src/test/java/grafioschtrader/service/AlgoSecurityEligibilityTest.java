package grafioschtrader.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.AlgoAssetclassJpaRepository;
import grafioschtrader.repository.AlgoSecurityJpaRepository;
import grafioschtrader.repository.AlgoSecurityJpaRepositoryImpl;
import grafioschtrader.repository.AlgoTopJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.rest.SecurityResource;
import grafioschtrader.types.SpecialInvestmentInstruments;

/** Tests instrument and opening-date eligibility without a Spring context or database writes. */
class AlgoSecurityEligibilityTest {
  private final AlgoAssetclassJpaRepository assetclasses = mock(AlgoAssetclassJpaRepository.class);
  private final AlgoTopJpaRepository tops = mock(AlgoTopJpaRepository.class);
  private final SecurityJpaRepository securities = mock(SecurityJpaRepository.class);
  private final AlgoSecurityEligibility eligibility = new AlgoSecurityEligibility(assetclasses, tops, securities);
  private final AlgoTop top = new AlgoTop();

  @BeforeEach
  void setup() {
    AlgoAssetclass parent = new AlgoAssetclass();
    parent.setIdTenant(7);
    parent.setIdAlgoAssetclassParent(237);
    when(assetclasses.findById(238)).thenReturn(Optional.of(parent));
    top.setReferenceDate(LocalDate.of(2020, 2, 5));
    when(tops.findByIdTenantAndIdAlgoAssetclassSecurity(7, 237)).thenReturn(top);
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Candidates ending on opening day remain eligible, including later-starting securities")
  void candidateBoundaries() {
    Security ended = security(1, "2020-02-05");
    Security boundary = security(2, "2020-02-06");
    Security later = security(3, "2030-01-01");
    later.setActiveFromDate(LocalDate.of(2025, 1, 1));
    Security unbounded = security(4, null);
    assertEquals(List.of(boundary, later, unbounded),
        eligibility.filterCandidates(7, 238, List.of(ended, boundary, later, unbounded)));
  }

  @Test
  @DisplayName("Both ordinary and watchlist candidate endpoints enforce the same eligibility")
  void bothCandidateEndpoints() {
    User user = mock(User.class);
    when(user.getIdTenant()).thenReturn(7);
    var authentication = new UsernamePasswordAuthenticationToken("user", "unused");
    authentication.setDetails(user);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    SecurityResource resource = new SecurityResource();
    ReflectionTestUtils.setField(resource, "securityJpaRepository", securities);
    ReflectionTestUtils.setField(resource, "securityEligibility", eligibility);
    Security ended = security(1, "2020-02-05");
    Security eligible = security(2, "2020-02-06");
    Security cfd = security(3, "2099-12-31");
    cfd.getAssetClass().setSpecialInvestmentInstrument(SpecialInvestmentInstruments.CFD);
    Security forex = security(4, "2099-12-31");
    forex.getAssetClass().setSpecialInvestmentInstrument(SpecialInvestmentInstruments.FOREX);
    Security leveraged = security(5, "2099-12-31");
    leveraged.setLeverageFactor(2);
    var candidates = List.of(ended, cfd, eligible, forex, leveraged);
    when(securities.getUnusedSecurityForAlgo(7, 238)).thenReturn(candidates);
    when(securities.getUnusedSecurityForAlgoCustom(9, 7, 238)).thenReturn(candidates);
    assertEquals(List.of(eligible), resource.getSecuritiesByIdAssetclass(238).getBody());
    assertEquals(List.of(eligible), resource.getUnusedSecurityForAlgoCustom(9, 238).getBody());
  }

  @Test
  @DisplayName("A strategy without a reference date retains expired candidates and accepts assignments")
  void noReferenceDate() {
    top.setReferenceDate(null);
    Security ended = security(1, "2010-01-01");
    when(securities.findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(1, 7)).thenReturn(ended);
    assertEquals(List.of(ended), eligibility.filterCandidates(7, 238, List.of(ended)));
    assertDoesNotThrow(() -> eligibility.validateAssignment(assignment(null, 238, ended), null));
  }

  @ParameterizedTest
  @CsvSource({ "CFD, 1", "FOREX, 1", "ETF, 2", "ETF, -1", "DIRECT_INVESTMENT, 0", "ISSUER_RISK_PRODUCT, 0.5",
      "ETF, 1.0001" })
  @DisplayName("Excluded instruments are hidden and rejected using persisted data, with or without a reference date")
  void excludedInstruments(SpecialInvestmentInstruments instrument, float leverage) {
    Security excluded = security(1, "2099-12-31");
    excluded.getAssetClass().setSpecialInvestmentInstrument(instrument);
    excluded.setLeverageFactor(leverage);
    when(securities.findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(1, 7)).thenReturn(excluded);
    Security eligible = security(2, "2099-12-31");
    for (int iteration = 0; iteration < 2; iteration++) {
      assertEquals(List.of(eligible), eligibility.filterCandidates(7, 238, List.of(excluded, eligible)));
      // A client presenting an ordinary unleveraged instrument cannot override the persisted instrument or leverage.
      AlgoSecurity posted = assignment(null, 238, security(1, "2099-12-31"));
      assertThrows(DataViolationException.class, () -> eligibility.validateAssignment(posted, posted));
      assertThrows(DataViolationException.class, () -> eligibility
          .validateAssignment(assignment(240, 238, security(1, "2099-12-31")), assignment(240, 238, eligible)));
      assertThrows(DataViolationException.class, () -> eligibility
          .validateAssignment(assignment(240, 238, security(1, "2099-12-31")), assignment(240, 239, excluded)));
      top.setReferenceDate(null);
    }
    AlgoSecurity existing = assignment(240, 238, excluded);
    assertDoesNotThrow(() -> eligibility.validateAssignment(assignment(240, 238, excluded), existing));
    assertDoesNotThrow(() -> eligibility.validateAssignment(assignment(null, null, excluded), null));
  }

  @Test
  @DisplayName("The save path rejects forged dates before persisting or changing alert scope")
  void saveRejectsForgedDates() {
    AlgoSecurityJpaRepositoryImpl repository = new AlgoSecurityJpaRepositoryImpl();
    AlgoSecurityJpaRepository persistence = mock(AlgoSecurityJpaRepository.class);
    AlgoAlertScopeLifecycle lifecycle = mock(AlgoAlertScopeLifecycle.class);
    ReflectionTestUtils.setField(repository, "securityEligibility", eligibility);
    ReflectionTestUtils.setField(repository, "hierarchyWriteGuard", mock(AlgoHierarchyWriteGuard.class));
    ReflectionTestUtils.setField(repository, "algoSecurityJpaRepository", persistence);
    ReflectionTestUtils.setField(repository, "alertScopeLifecycle", lifecycle);
    when(securities.findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(1, 7))
        .thenReturn(security(1, "2020-02-05"));
    AlgoSecurity created = assignment(null, 238, security(1, "2099-12-31"));
    // The create resource passes the new object as existingEntity too; it must not be grandfathered.
    assertThrows(DataViolationException.class, () -> repository.saveOnlyAttributes(created, created, Set.of()));
    verifyNoInteractions(persistence, lifecycle);
  }

  @Test
  @DisplayName("A new assignment may end exactly on opening day and uses the persisted security")
  void saveBoundary() {
    Security persisted = security(1, "2020-02-06");
    when(securities.findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(1, 7)).thenReturn(persisted);
    AlgoSecurity created = assignment(null, 238, security(1, "2099-12-31"));
    eligibility.validateAssignment(created, null);
    assertSame(persisted, created.getSecurity());
  }

  @Test
  @DisplayName("Unchanged assignments remain editable but replacements and moves must qualify")
  void existingAssignments() {
    Security ended = security(1, "2020-02-05");
    AlgoSecurity existing = assignment(240, 238, ended);
    assertDoesNotThrow(() -> eligibility.validateAssignment(assignment(240, 238, ended), existing));
    verifyNoInteractions(securities);
    when(securities.findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(1, 7)).thenReturn(ended);
    assertThrows(DataViolationException.class,
        () -> eligibility.validateAssignment(assignment(240, 238, ended), assignment(240, 239, ended)));
    when(securities.findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(2, 7))
        .thenReturn(security(2, "2019-12-31"));
    assertThrows(DataViolationException.class,
        () -> eligibility.validateAssignment(assignment(240, 238, security(2, "2099-12-31")), existing));
  }

  @Test
  @DisplayName("Standalone alerts have no strategy opening date")
  void standaloneAlert() {
    assertDoesNotThrow(() -> eligibility.validateAssignment(assignment(null, null, security(1, "2010-01-01")), null));
    verifyNoInteractions(securities);
  }

  @Test
  @DisplayName("Foreign parent tenants and inaccessible securities cannot be used")
  void ownership() {
    assertThrows(SecurityException.class, () -> eligibility.filterCandidates(8, 238, List.of()));
    assertThrows(SecurityException.class,
        () -> eligibility.validateAssignment(assignment(null, 238, security(1, "2099-12-31")), null));
  }

  @Test
  @DisplayName("Opening date is the next calendar day, including weekends and year boundaries")
  void calendarBoundaries() {
    top.setReferenceDate(LocalDate.of(2020, 12, 31));
    Security yearEnd = security(1, "2020-12-31");
    Security newYear = security(2, "2021-01-01");
    assertEquals(List.of(newYear), eligibility.filterCandidates(7, 238, List.of(yearEnd, newYear)));
    top.setReferenceDate(LocalDate.of(2020, 2, 7));
    Security saturday = security(3, "2020-02-08");
    assertEquals(List.of(saturday), eligibility.filterCandidates(7, 238, List.of(saturday)));
  }

  private Security security(int id, String end) {
    Security security = new Security();
    security.setIdSecuritycurrency(id);
    security.setActiveToDate(end == null ? null : LocalDate.parse(end));
    security.setLeverageFactor(1);
    Assetclass assetclass = new Assetclass();
    assetclass.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    security.setAssetClass(assetclass);
    return security;
  }

  private AlgoSecurity assignment(Integer id, Integer parent, Security security) {
    AlgoSecurity assignment = new AlgoSecurity();
    assignment.setIdAlgoAssetclassSecurity(id);
    assignment.setIdTenant(7);
    assignment.setIdAlgoSecurityParent(parent);
    assignment.setSecurity(security);
    return assignment;
  }
}
