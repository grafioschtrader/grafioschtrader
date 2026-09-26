package grafioschtrader.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.MessageSource;

import grafioschtrader.dto.AlgoHierarchyDto;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.AlgoAssetclassJpaRepository;
import grafioschtrader.repository.AlgoStrategyJpaRepository;
import grafioschtrader.repository.AlgoTopJpaRepository;
import grafioschtrader.repository.WatchlistJpaRepository;
import grafioschtrader.types.SpecialInvestmentInstruments;

/** Exercises overview warnings and refreshes without starting Spring or accessing a database. */
class AlgoHierarchyViewServiceTest {
  private final AlgoTopJpaRepository tops = mock(AlgoTopJpaRepository.class);
  private final AlgoAssetclassJpaRepository assetclasses = mock(AlgoAssetclassJpaRepository.class);
  private final AlgoHierarchyViewService service = new AlgoHierarchyViewService(tops, assetclasses,
      new AlgoTopReadinessService(assetclasses, mock(AlgoStrategyJpaRepository.class),
          mock(WatchlistJpaRepository.class), mock(MessageSource.class)));
  private final AlgoTop top = new AlgoTop();
  private final AlgoAssetclass bucket = new AlgoAssetclass();

  @BeforeEach
  void setup() {
    org.springframework.test.util.ReflectionTestUtils.setField(service, "monitoring",
        mock(AlgoMonitoringService.class));
    top.setIdAlgoAssetclassSecurity(1);
    top.setPercentage(100f);
    top.setReferenceDate(LocalDate.of(2020, 2, 5));
    bucket.setIdAlgoAssetclassSecurity(2);
    bucket.setPercentage(100f);
    bucket.setAlgoSecurityList(List.of(member(3, 100f)));
    when(tops.findByIdTenantAndIdAlgoAssetclassSecurity(7, 1)).thenReturn(top);
    when(assetclasses.findByIdTenantAndIdAlgoAssetclassParent(7, 1)).thenReturn(List.of(bucket));
  }

  @Test
  @DisplayName("A valid hierarchy delivers totals and no warnings; later-starting instruments remain eligible")
  void validHierarchy() {
    bucket.getAlgoSecurityList().getFirst().getSecurity().setActiveFromDate(LocalDate.of(2025, 1, 1));
    AlgoHierarchyDto result = service.getHierarchy(7, 1);
    assertEquals(100f, result.algoTop().addedPercentage);
    assertEquals(100f, result.algoAssetclassList().getFirst().getAddedPercentage());
    assertTrue(result.invalidFields().isEmpty());
  }

  @ParameterizedTest
  @CsvSource({ "99.994, true", "99.996, false", "100, false", "100.004, false", "100.006, true", "0, true" })
  @DisplayName("The same backend tolerance applies to root and asset-class totals")
  void percentageTolerance(float percentage, boolean invalid) {
    bucket.setPercentage(percentage);
    bucket.getAlgoSecurityList().getFirst().setPercentage(percentage);
    var fields = service.getHierarchy(7, 1).invalidFields();
    assertEquals(invalid, fields.getOrDefault(1, Set.of()).contains("addedPercentage"));
    assertEquals(invalid, fields.getOrDefault(2, Set.of()).contains("addedPercentage"));
  }

  @Test
  @DisplayName("Positive empty classes are red; zero-weight empty classes only warn about their total")
  void emptyClass() {
    bucket.setAlgoSecurityList(List.of());
    assertEquals(Set.of("name", "addedPercentage"), service.getHierarchy(7, 1).invalidFields().get(2));
    bucket.setPercentage(0f);
    assertEquals(Set.of("addedPercentage"), service.getHierarchy(7, 1).invalidFields().get(2));
    when(assetclasses.findByIdTenantAndIdAlgoAssetclassParent(7, 1)).thenReturn(List.of());
    AlgoHierarchyDto empty = service.getHierarchy(7, 1);
    assertEquals(0f, empty.algoTop().addedPercentage);
    assertEquals(Set.of("addedPercentage"), empty.invalidFields().get(1));
  }

  @Test
  @DisplayName("Excluded and expired instruments warn, and cannot make a positive class valid")
  void ineligibleInstruments() {
    AlgoSecurity excluded = member(3, 50f);
    excluded.getSecurity().setLeverageFactor(2);
    AlgoSecurity expired = member(4, 50f);
    expired.getSecurity().setActiveToDate(top.getReferenceDate());
    bucket.setAlgoSecurityList(List.of(excluded, expired));
    var fields = service.getHierarchy(7, 1).invalidFields();
    for (int id : List.of(2, 3, 4)) {
      assertEquals(Set.of("name"), fields.get(id));
    }
    // Ending on opening day is inclusive; a refresh must clear the class warning as well.
    expired.getSecurity().setActiveToDate(top.getReferenceDate().plusDays(1));
    fields = service.getHierarchy(7, 1).invalidFields();
    assertFalse(fields.containsKey(2));
    assertFalse(fields.containsKey(4));
    assertEquals(Set.of("name"), fields.get(3));
  }

  @Test
  @DisplayName("Zero-weight members do not cover a positive asset-class allocation")
  void unallocatedMembers() {
    AlgoSecurity member = bucket.getAlgoSecurityList().getFirst();
    member.setPercentage(0f);
    assertTrue(service.getHierarchy(7, 1).invalidFields().get(2).contains("name"));
    member.setPercentage(null);
    assertTrue(service.getHierarchy(7, 1).invalidFields().get(2).contains("name"));
    member.setPercentage(100f);
    member.setSecurity(null);
    assertEquals(Set.of("name"), service.getHierarchy(7, 1).invalidFields().get(3));
  }

  @Test
  @DisplayName("A hierarchy without a reference date uses the existing date-unrestricted eligibility policy")
  void noReferenceDate() {
    top.setReferenceDate(null);
    bucket.getAlgoSecurityList().getFirst().getSecurity().setActiveToDate(LocalDate.of(2000, 1, 1));
    assertTrue(service.getHierarchy(7, 1).invalidFields().isEmpty());
  }

  @Test
  @DisplayName("A foreign or missing root is rejected before loading children")
  void ownership() {
    assertThrows(SecurityException.class, () -> service.getHierarchy(8, 1));
    verifyNoInteractions(assetclasses);
  }

  @Test
  @DisplayName("Only trading end dates before today receive a yellow warning, independently of strategy opening")
  void expiredDateWarnings() {
    LocalDate today = LocalDate.now();
    AlgoSecurity expired = member(3, 25f);
    expired.getSecurity().setActiveToDate(today.minusDays(1));
    AlgoSecurity endsToday = member(4, 25f);
    endsToday.getSecurity().setActiveToDate(today);
    AlgoSecurity future = member(5, 25f);
    future.getSecurity().setActiveToDate(today.plusDays(1));
    AlgoSecurity unbounded = member(6, 25f);
    bucket.setAlgoSecurityList(List.of(expired, endsToday, future, unbounded));

    for (LocalDate referenceDate : List.of(today.minusYears(1), today.plusYears(1))) {
      top.setReferenceDate(referenceDate);
      var warnings = service.getHierarchy(7, 1).warningFields();
      assertEquals(1, warnings.size());
      assertEquals(Set.of("security.activeToDate"), warnings.get(3));
    }
    expired.getSecurity().setActiveToDate(today);
    assertTrue(service.getHierarchy(7, 1).warningFields().isEmpty());
  }

  private AlgoSecurity member(int id, float percentage) {
    Security security = new Security();
    security.setLeverageFactor(1);
    Assetclass instrument = new Assetclass();
    instrument.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    security.setAssetClass(instrument);
    AlgoSecurity member = new AlgoSecurity();
    member.setIdAlgoAssetclassSecurity(id);
    member.setSecurity(security);
    member.setPercentage(percentage);
    return member;
  }
}
