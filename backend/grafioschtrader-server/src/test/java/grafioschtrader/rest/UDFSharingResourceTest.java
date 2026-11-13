package grafioschtrader.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import grafiosch.entities.UDFMetadataSubscription;
import grafiosch.rest.RequestMappings;
import grafiosch.types.UDFShareMode;
import grafioschtrader.entities.UDFMetadataSecurity;

/** Uses the users and instruments created before this class in ResourceTestSuite_25. */
class UDFSharingResourceTest extends BaseIntegrationTest {
  @Autowired
  private JdbcTemplate jdbc;

  @BeforeEach
  void tokens() {
    RestTestHelper.inizializeUserTokens(restTestClient, jwtTokenHandler);
  }

  @Test
  void subscriptionPermissionsAndMissingKeysProtectSharedValues() {
    var owner = authenticatedClient(RestTestHelper.ADMIN);
    var reader = authenticatedClient(RestTestHelper.ALLEDIT);
    int idSecurity = jdbc
        .queryForObject("SELECT MIN(id_securitycurrency) FROM security WHERE id_tenant_private IS NULL", Integer.class);
    Map<Integer, String> previousDocuments = new HashMap<>();
    for (String nickname : List.of(RestTestHelper.ADMIN, RestTestHelper.ALLEDIT)) {
      Integer idUser = RestTestHelper.getUserByNickname(nickname).idUser;
      var documents = jdbc.queryForList(
          "SELECT json_values FROM udf_data WHERE id_user = ? AND entity = 'Security' AND id_entity = ?", String.class,
          idUser, idSecurity);
      previousDocuments.put(idUser, documents.isEmpty() ? null : documents.getFirst());
    }
    List<Integer> used = jdbc.queryForList(
        "SELECT ui_order FROM udf_metadata WHERE id_user IN (0, ?, ?) UNION SELECT ui_order FROM udf_metadata_subscription WHERE id_user IN (?, ?)",
        Integer.class, RestTestHelper.getUserByNickname(RestTestHelper.ADMIN).idUser,
        RestTestHelper.getUserByNickname(RestTestHelper.ALLEDIT).idUser,
        RestTestHelper.getUserByNickname(RestTestHelper.ADMIN).idUser,
        RestTestHelper.getUserByNickname(RestTestHelper.ALLEDIT).idUser);
    int order = java.util.stream.IntStream.range(1, 100).filter(i -> !used.contains(i)).findFirst().orElseThrow();
    Map<String, Object> definition = new HashMap<>(Map.of("description", "REST UDF sharing", "udfDataType",
        "UDF_String", "fieldSize", "0,80", "uiOrder", order, "shareMode", "UDF_SHARE_READ"));
    // Use this instrument's actual scope instead of assuming an asset-class fixture.
    definition.put("categoryTypeEnums",
        List.of(grafioschtrader.types.AssetclassType.getAssetClassTypeByValue(jdbc.queryForObject(
            "SELECT a.category_type FROM security s JOIN assetclass a ON a.id_asset_class = s.id_asset_class WHERE s.id_securitycurrency = ?",
            Byte.class, idSecurity)).name()));
    definition.put("specialInvestmentInstrumentEnums", List.of(
        grafioschtrader.types.SpecialInvestmentInstruments.getSpecialInvestmentInstrumentsByValue(jdbc.queryForObject(
            "SELECT a.spec_invest_instrument FROM security s JOIN assetclass a ON a.id_asset_class = s.id_asset_class WHERE s.id_securitycurrency = ?",
            Byte.class, idSecurity)).name()));
    UDFMetadataSecurity field = owner.post().uri(RequestGTMappings.UDF_METADATA_SECURITY_MAP).body(definition)
        .exchange().expectStatus().isOk().expectBody(UDFMetadataSecurity.class).returnResult().getResponseBody();
    assertThat(field).isNotNull();
    Integer subscriptionId = null;
    try {
      String key = "f" + field.getId();
      Map<String, Object> dataKey = Map.of("entity", "Security", "idEntity", idSecurity);
      owner.put().uri(RequestMappings.UDF_DATA_MAP)
          .body(Map.of("uDFDataKey", dataKey, "jsonValues", Map.of(key, "owner value"))).exchange().expectStatus()
          .isOk();
      UDFMetadataSubscription subscription = reader.post().uri(RequestMappings.UDF_METADATA_SUBSCRIPTION_MAP)
          .body(Map.of("idUDFMetadata", field.getId(), "uiOrder", order)).exchange().expectStatus().isOk()
          .expectBody(UDFMetadataSubscription.class).returnResult().getResponseBody();
      assertThat(subscription).isNotNull();
      subscriptionId = subscription.getId();
      assertValue(RestTestHelper.ALLEDIT, idSecurity, key, "owner value");
      reader.put().uri(RequestMappings.UDF_DATA_MAP)
          .body(Map.of("uDFDataKey", dataKey, "jsonValues", Map.of(key, "ignored"))).exchange().expectStatus().isOk();
      assertValue(RestTestHelper.ADMIN, idSecurity, key, "owner value");
      field.setShareMode(UDFShareMode.UDF_SHARE_READ_WRITE);
      owner.put().uri(RequestGTMappings.UDF_METADATA_SECURITY_MAP).body(field).exchange().expectStatus().isOk();
      reader.put().uri(RequestMappings.UDF_DATA_MAP)
          .body(Map.of("uDFDataKey", dataKey, "jsonValues", Map.of(key, "reader value"))).exchange().expectStatus()
          .isOk();
      reader.put().uri(RequestMappings.UDF_DATA_MAP).body(Map.of("uDFDataKey", dataKey, "jsonValues", Map.of()))
          .exchange().expectStatus().isOk();
      assertValue(RestTestHelper.ADMIN, idSecurity, key, "reader value");
      reader.put().uri(RequestGTMappings.UDF_METADATA_SECURITY_MAP).body(field).exchange().expectStatus()
          .is4xxClientError();
      field.setShareMode(UDFShareMode.UDF_SHARE_NONE);
      owner.put().uri(RequestGTMappings.UDF_METADATA_SECURITY_MAP).body(field).exchange().expectStatus()
          .is4xxClientError();
    } finally {
      if (subscriptionId != null)
        reader.delete().uri(RequestMappings.UDF_METADATA_SUBSCRIPTION_MAP + "/" + subscriptionId).exchange()
            .expectStatus().isNoContent();
      owner.delete().uri(RequestGTMappings.UDF_METADATA_SECURITY_MAP + "/" + field.getId()).exchange().expectStatus()
          .isNoContent();
      previousDocuments.forEach((idUser, json) -> {
        if (json == null) {
          jdbc.update("DELETE FROM udf_data WHERE id_user = ? AND entity = 'Security' AND id_entity = ?", idUser,
              idSecurity);
        } else {
          jdbc.update(
              "INSERT INTO udf_data (id_user, entity, id_entity, json_values) VALUES (?, 'Security', ?, ?) ON DUPLICATE KEY UPDATE json_values = VALUES(json_values)",
              idUser, idSecurity, json);
        }
      });
    }
  }

  private void assertValue(String nickname, int idSecurity, String key, String value) {
    authenticatedClient(nickname).get().uri(RequestMappings.UDF_DATA_MAP + "/Security/" + idSecurity).exchange()
        .expectStatus().isOk().expectBody().jsonPath("$.jsonValues." + key).isEqualTo(value);
  }
}
