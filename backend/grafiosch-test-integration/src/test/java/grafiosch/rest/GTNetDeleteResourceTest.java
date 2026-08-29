package grafiosch.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetEntity;
import grafiosch.gtnet.AcceptRequestTypes;
import grafiosch.gtnet.GTNetServerOnlineStatusTypes;
import grafiosch.gtnet.GTNetServerStateTypes;
import grafiosch.integration.gtnet.IntegrationExchangeKindType;
import grafiosch.repository.GTNetConfigEntityJpaRepository;
import grafiosch.repository.GTNetJpaRepository;

/**
 * Deletes a GTNet peer that carries an exchange kind with its configuration row.
 *
 * <p>
 * That is the shape in which the delete used to fail: {@code gt_net_config_entity} shares its primary key with
 * {@code gt_net_entity} and the database removes it through {@code ON DELETE CASCADE}, so Hibernate must issue its own
 * delete for the configuration <em>before</em> the one for the entity. It only does so when the association states that
 * the foreign key sits on the configuration side, which is what {@code mappedBy} plus {@code @MapsId} express. With the
 * direction reversed the commit ended in {@code ObjectOptimisticLockingFailureException: Unexpected row count
 * (expected row count 1 but was 0)} and no peer could ever be deleted.
 * </p>
 *
 * <p>
 * The peer is created through the repository rather than through {@code POST /api/gtnet}, because
 * {@code GTNetJpaRepositoryImpl.saveOnlyAttributes} probes the remote domain over HTTP while no GTNet row exists yet.
 * The test writes and removes its own row and touches nothing else, so it leaves the database as it found it.
 * </p>
 */
@TestInstance(Lifecycle.PER_CLASS)
class GTNetDeleteResourceTest extends BaseIntegrationTest {

  /** Reserved TLD (RFC 2606), so nothing here can resolve to a real host or to this machine. */
  private static final String PEER_DOMAIN = "https://gtnet-delete-test.invalid";

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GTNetConfigEntityJpaRepository gtNetConfigEntityJpaRepository;

  @BeforeAll
  void setUpUserToken() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  @Test
  @DisplayName("Admin deletes a peer whose exchange kind carries a configuration row")
  void deletePeerWithConfigEntity() {
    GTNet leftOver = gtNetJpaRepository.findByDomainRemoteName(PEER_DOMAIN);
    if (leftOver != null) {
      gtNetJpaRepository.delete(leftOver);
    }

    GTNet peer = createPeerWithConfiguredEntity();
    Integer idGtNet = peer.getIdGtNet();
    Integer idGtNetEntity = peer.getGtNetEntities().get(0).getIdGtNetEntity();
    assertThat(gtNetConfigEntityJpaRepository.findById(idGtNetEntity)).isPresent();

    authenticatedClient(RestTestHelper.ADMIN).delete().uri(RequestMappings.GTNET_MAP + "/" + idGtNet).exchange()
        .expectStatus().isNoContent();

    assertThat(gtNetJpaRepository.findById(idGtNet)).isEmpty();
    assertThat(gtNetConfigEntityJpaRepository.findById(idGtNetEntity)).isEmpty();
  }

  /**
   * Persists a peer with one open exchange kind and the configuration row hanging off it. Both children are written by
   * the cascade, which also proves the insert order the corrected association produces: parent first, configuration
   * last.
   *
   * @return the persisted peer, with the generated identifiers of its children
   */
  private GTNet createPeerWithConfiguredEntity() {
    GTNet peer = new GTNet();
    peer.setDomainRemoteName(PEER_DOMAIN);
    peer.setTimeZone("UTC");
    peer.setServerOnline(GTNetServerOnlineStatusTypes.SOS_UNKNOWN);

    GTNetEntity entity = peer.getOrCreateEntityByKind(IntegrationExchangeKindType.INTEGRATION_STREAM.getValue());
    entity.setAcceptRequest(AcceptRequestTypes.AC_OPEN);
    entity.setServerState(GTNetServerStateTypes.SS_OPEN);
    entity.getOrCreateConfigEntity();

    return gtNetJpaRepository.save(peer);
  }
}
