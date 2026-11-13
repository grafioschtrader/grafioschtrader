package grafioschtrader.tax.germany;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;

/**
 * The boundary of the German tax help: only a tenant that reports in euro and has Germany as its country is served, and
 * only for a tax year in the supported range. The checks come before any transaction is read.
 */
@DisplayName("German tax help, who may use it")
class GermanTaxReportServiceTest {

  private TenantJpaRepository tenantJpaRepository;
  private TransactionJpaRepository transactionJpaRepository;
  private GermanTaxReportService service;

  @BeforeEach
  void setUp() {
    tenantJpaRepository = mock(TenantJpaRepository.class);
    transactionJpaRepository = mock(TransactionJpaRepository.class);
    service = new GermanTaxReportService();
    ReflectionTestUtils.setField(service, "tenantJpaRepository", tenantJpaRepository);
    ReflectionTestUtils.setField(service, "transactionJpaRepository", transactionJpaRepository);
  }

  private Tenant tenant(String currency, String country) {
    Tenant tenant = new Tenant();
    tenant.setCurrency(currency);
    tenant.setCountry(country);
    when(tenantJpaRepository.getReferenceById(anyInt())).thenReturn(tenant);
    return tenant;
  }

  @Test
  @DisplayName("A tenant in euro with the country Germany is eligible, every other combination is not")
  void eligibility() {
    Tenant german = new Tenant();
    german.setCurrency("EUR");
    german.setCountry("DE");
    Tenant austrian = new Tenant();
    austrian.setCurrency("EUR");
    austrian.setCountry("AT");
    Tenant germanInFrancs = new Tenant();
    germanInFrancs.setCurrency("CHF");
    germanInFrancs.setCountry("DE");
    Tenant withoutCountry = new Tenant();
    withoutCountry.setCurrency("EUR");

    assertThat(GermanTaxReportService.isAvailable(german)).isTrue();
    assertThat(GermanTaxReportService.isAvailable(austrian)).isFalse();
    assertThat(GermanTaxReportService.isAvailable(germanInFrancs)).isFalse();
    assertThat(GermanTaxReportService.isAvailable(withoutCountry)).isFalse();
  }

  @Test
  @DisplayName("A call by a tenant that is not eligible is refused before any transaction is read")
  void refusesAnIneligibleTenant() {
    tenant("EUR", "AT");

    assertThatThrownBy(() -> service.getReport(1, 2025)).isInstanceOf(DataViolationException.class);
    verify(transactionJpaRepository, never()).findByIdTenantAndTransactionDateBetweenForReport(any(), any(), any());
  }

  @Test
  @DisplayName("A tax year before 2018 or after the current year is refused")
  void refusesAYearOutsideTheRange() {
    tenant("EUR", "DE");

    assertThatThrownBy(() -> service.getReport(1, 2017)).isInstanceOf(DataViolationException.class);
    assertThatThrownBy(() -> service.getReport(1, LocalDate.now().getYear() + 2))
        .isInstanceOf(DataViolationException.class);
    verify(transactionJpaRepository, never()).findByIdTenantAndTransactionDateBetweenForReport(any(), any(), any());
  }
}
