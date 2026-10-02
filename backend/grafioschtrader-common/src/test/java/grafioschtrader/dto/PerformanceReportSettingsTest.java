package grafioschtrader.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.types.PerformanceReportPreset;
import grafioschtrader.types.PerformanceReportSection;
import io.hypersistence.utils.hibernate.type.json.JsonType;

@DisplayName("Report settings persisted as tenant JSON")
class PerformanceReportSettingsTest {

  @Test
  void hibernateCanCopyDefaultSettings() {
    assertIndependentCopy(new PerformanceReportSettings());
  }

  @Test
  void hibernateSnapshotPreservesAllSettingsAndIsolatesMutableSections() {
    var settings = new PerformanceReportSettings();
    settings.preset = PerformanceReportPreset.CUSTOM;
    settings.sections = new HashSet<>(Set.of(PerformanceReportSection.COVER, PerformanceReportSection.HOLDINGS,
        PerformanceReportSection.ALLOCATION, PerformanceReportSection.GLOSSARY));
    settings.title = "Custom statement";
    settings.recipient = "Recipient\nAddress";
    settings.sender = "Sender\nAddress";
    settings.additionalNotes = "Additional notes";
    settings.language = "de";
    settings.numberFormat = "de-CH";
    settings.annualYears = 5;
    settings.detailColumns = true;

    var snapshot = assertIndependentCopy(settings);
    assertThat(snapshot.sections).isNotSameAs(settings.sections);
    settings.sections.remove(PerformanceReportSection.ALLOCATION);
    assertThat(snapshot.sections).contains(PerformanceReportSection.ALLOCATION);
  }

  private PerformanceReportSettings assertIndependentCopy(PerformanceReportSettings settings) {
    // Exercise the same deep-copy path Hibernate uses when flushing Tenant.reportSettings.
    var jsonType = new JsonType(PerformanceReportSettings.class);
    var copy = (PerformanceReportSettings) jsonType.deepCopy(settings);
    assertThat(copy).isNotSameAs(settings);
    assertThat(copy).usingRecursiveComparison().isEqualTo(settings);
    return copy;
  }
}
