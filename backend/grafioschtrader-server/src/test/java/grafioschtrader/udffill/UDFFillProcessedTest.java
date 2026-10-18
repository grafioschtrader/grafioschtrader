package grafioschtrader.udffill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafiosch.repository.UserJpaRepository;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.repository.UDFDataFillJpaRepository;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.UDFFillSource;

/**
 * The column "Active" of the additional fields tells the user whether anything fills a field. A wrong answer hides that
 * a language model field without a key of the tenant is never processed, or marks a working field as idle.
 */
class UDFFillProcessedTest {

  @Test
  @DisplayName("Manual fields are active without a model; language model fields require one")
  void manualFieldsAreActiveAndLanguageModelFieldsRequireAModel() {
    UDFFillService service = new UDFFillService(mock(UDFMetadataSecurityJpaRepository.class),
        mock(UDFDataFillJpaRepository.class), mock(UserJpaRepository.class), mock(UDFFillScopeService.class),
        mock(UDFFillWriter.class), mock(UDFFillTaskQueue.class), mock(GlobalparametersService.class));

    assertThat(service.isProcessed(field(UDFFillSource.MANUAL), false)).isTrue();

    assertThat(service.isProcessed(field(UDFFillSource.LLM), false)).isFalse();
    assertThat(service.isProcessed(field(UDFFillSource.LLM), true)).isTrue();
  }

  private static UDFMetadataSecurity field(UDFFillSource fillSource) {
    UDFMetadataSecurity field = new UDFMetadataSecurity();
    field.setFillSource(fillSource);
    return field;
  }
}
