package grafiosch.gtnet.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import grafiosch.entities.GTNet;
import grafiosch.gtnet.GTNetServerOnlineStatusTypes;
import grafiosch.gtnet.m2m.model.GTNetPublicDTO;
import grafiosch.repository.GTNetJpaRepository;
import tools.jackson.databind.ObjectMapper;

class GTNetServerListImporterTest {

  private final GTNetJpaRepository gtNetJpaRepository = mock(GTNetJpaRepository.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final GTNetServerListImporter importer = new GTNetServerListImporter();

  GTNetServerListImporterTest() {
    ReflectionTestUtils.setField(importer, "gtNetJpaRepository", gtNetJpaRepository);
  }

  @Test
  void addsUnknownServerEvenWhenOwnEntryForbidsServerCreation() {
    GTNet me = gtNet("https://me", null);
    me.setAllowServerCreation(false);

    var result = importer.importServerList(me, "https://peer",
        objectMapper.valueToTree(List.of(dto(gtNet("https://new", null)))), objectMapper);

    ArgumentCaptor<GTNet> saved = ArgumentCaptor.forClass(GTNet.class);
    verify(gtNetJpaRepository).save(saved.capture());
    assertThat(saved.getValue().getDomainRemoteName()).isEqualTo("https://new");
    assertThat(saved.getValue().isAllowServerCreation()).isFalse();
    assertThat(saved.getValue().getServerOnline()).isEqualTo(GTNetServerOnlineStatusTypes.SOS_UNKNOWN);
    assertThat(result).isEqualTo(new GTNetServerListImporter.ImportResult(1, 0, 0, 0));
  }

  @Test
  void skipsOwnEntryAndKeepsNewerLocalData() {
    GTNet me = gtNet("https://me", null);
    LocalDateTime now = LocalDateTime.now();
    GTNet known = gtNet("https://known", now);
    known.setTimeZone("Europe/Zurich");
    when(gtNetJpaRepository.findByDomainRemoteName("https://known")).thenReturn(known);
    GTNet remoteView = gtNet("https://known", now.minusDays(1));
    remoteView.setTimeZone("UTC");

    var result = importer.importServerList(me, "https://peer",
        objectMapper.valueToTree(List.of(dto(gtNet("https://me", null)), dto(remoteView))), objectMapper);

    verify(gtNetJpaRepository, never()).save(any());
    assertThat(known.getTimeZone()).isEqualTo("Europe/Zurich");
    assertThat(result).isEqualTo(new GTNetServerListImporter.ImportResult(0, 0, 1, 1));
  }

  @Test
  void missingPayloadImportsNothing() {
    var result = importer.importServerList(gtNet("https://me", null), "https://peer", null, objectMapper);

    verify(gtNetJpaRepository, never()).save(any());
    assertThat(result).isEqualTo(new GTNetServerListImporter.ImportResult(0, 0, 0, 0));
  }

  private GTNet gtNet(String domain, LocalDateTime lastModified) {
    GTNet gtNet = new GTNet();
    gtNet.setDomainRemoteName(domain);
    gtNet.setTimeZone("UTC");
    gtNet.setSpreadCapability(true);
    if (lastModified != null) {
      gtNet.setLastModifiedTime(lastModified);
    }
    return gtNet;
  }

  private GTNetPublicDTO dto(GTNet gtNet) {
    return new GTNetPublicDTO(gtNet);
  }
}
