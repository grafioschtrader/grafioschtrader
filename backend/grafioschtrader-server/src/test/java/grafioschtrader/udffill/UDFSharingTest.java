package grafioschtrader.udffill;

import static org.assertj.core.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.*;
import grafiosch.entities.UDFData.UDFDataKey;
import grafiosch.exceptions.DataViolationException;
import grafiosch.exportdelete.MySqlDeleteMyData;
import grafiosch.repository.*;
import grafiosch.security.UserAuthentication;
import grafiosch.types.*;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;
import grafioschtrader.rest.GTIntegrationTestContext;
import grafioschtrader.types.TenantKindType;
import grafioschtrader.types.UDFFillSource;
import jakarta.persistence.*;

/** Real database tests: the JSON type, foreign keys and account deletion cannot be proved with mocked repositories. */
@GTIntegrationTestContext
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
class UDFSharingTest {
  @PersistenceContext
  private EntityManager em;
  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private UDFMetadataSecurityJpaRepository fields;
  @Autowired
  private UDFMetadataSubscriptionJpaRepository subscriptions;
  @Autowired
  private UDFDataJpaRepository data;

  private User owner;
  private User reader;
  private User other;

  @BeforeEach
  void fixture() {
    owner = user("owner");
    reader = user("reader");
    other = user("other");
    as(owner);
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  private User user(String name) {
    Tenant tenant = new Tenant("UDF sharing " + name, "CHF", 0, TenantKindType.MAIN, false);
    em.persist(tenant);
    User user = new User("udf-sharing-" + name + "@test.local", "unused", "UDF sharing " + name, "en", 0);
    user.setIdTenant(tenant.getId());
    em.persist(user);
    return user;
  }

  private static void as(User user) {
    SecurityContextHolder.getContext().setAuthentication(new UserAuthentication(user));
  }

  private UDFMetadataSecurity field(UDFDataType type, byte order) {
    UDFMetadataSecurity field = new UDFMetadataSecurity(0, 0, owner.getIdUser(), null, "UDF sharing " + type.name(),
        null, type, type == UDFDataType.UDF_String ? "0,80" : type == UDFDataType.UDF_Numeric ? "12,5" : null, order);
    em.persist(field);
    em.flush();
    return field;
  }

  private UDFMetadataSecurity changeMode(Integer id, UDFShareMode mode) throws Exception {
    em.flush();
    em.clear();
    UDFMetadataSecurity existing = fields.findById(id).orElseThrow();
    UDFMetadataSecurity draft = new UDFMetadataSecurity(0, 0, existing.getIdUser(), null, existing.getDescription(),
        existing.getDescriptionHelp(), existing.getUdfDataType(), existing.getFieldSize(), existing.getUiOrder());
    draft.setShareMode(mode);
    return fields.saveOnlyAttributes(draft, existing, fields.getUpdatePropertyLevels(existing));
  }

  private UDFMetadataSubscription subscribe(Integer field, User user, byte order) throws Exception {
    as(user);
    UDFMetadataSubscription s = new UDFMetadataSubscription();
    s.setIdUser(user.getIdUser());
    s.setIdUDFMetadata(field);
    s.setUiOrder(order);
    return subscriptions.saveOnlyAttributes(s, null, subscriptions.getUpdatePropertyLevels(null));
  }

  @Test
  void sharingRoundTripPreservesBooleanTextAndNumber() throws Exception {
    var flag = field(UDFDataType.UDF_Boolean, (byte) 91);
    var text = field(UDFDataType.UDF_String, (byte) 92);
    var number = field(UDFDataType.UDF_Numeric, (byte) 93);
    Map<String, Object> original = Map.of("f" + flag.getId(), true, "f" + text.getId(), "literal \"text\"",
        "f" + number.getId(), "12.34500");
    em.persist(new UDFData(new UDFDataKey(owner.getIdUser(), "Security", 100), new HashMap<>(original)));
    for (var f : List.of(flag, text, number))
      changeMode(f.getId(), UDFShareMode.UDF_SHARE_READ);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM udf_data_shared WHERE id_udf_metadata IN (?, ?, ?)",
        Integer.class, flag.getId(), text.getId(), number.getId())).isEqualTo(3);
    assertThat(jdbc.queryForObject("SELECT field_value FROM udf_data_shared WHERE id_udf_metadata = ?", String.class,
        flag.getId())).isEqualTo("true");
    for (var f : List.of(flag, text, number))
      changeMode(f.getId(), UDFShareMode.UDF_SHARE_NONE);
    em.clear();
    assertThat(data.findById(new UDFDataKey(owner.getIdUser(), "Security", 100)).orElseThrow().getJsonValues())
        .isEqualTo(original);
  }

  @Test
  void emptyAndJsonNullDoNotBecomeSharedRows() throws Exception {
    var f = field(UDFDataType.UDF_String, (byte) 91);
    Map<String, Object> nil = new HashMap<>();
    nil.put("f" + f.getId(), null);
    em.persist(new UDFData(new UDFDataKey(owner.getIdUser(), "Security", 100), nil));
    em.persist(new UDFData(new UDFDataKey(owner.getIdUser(), "Security", 101), Map.of("f" + f.getId(), "")));
    changeMode(f.getId(), UDFShareMode.UDF_SHARE_READ);
    assertThat(
        jdbc.queryForObject("SELECT COUNT(*) FROM udf_data_shared WHERE id_udf_metadata = ?", Integer.class, f.getId()))
            .isZero();
  }

  @Test
  void deletionOrphansAndLastSubscriptionDeletesTheField() throws Exception {
    var f = field(UDFDataType.UDF_String, (byte) 91);
    changeMode(f.getId(), UDFShareMode.UDF_SHARE_READ);
    var s = subscribe(f.getId(), reader, (byte) 91);
    em.persist(new UDFDataShared(new UDFDataSharedKey(f.getId(), 100), "Security", "retained"));
    em.flush();
    assertThat(fields.delEntityWithUserId(f.getId(), owner.getIdUser())).isEqualTo(1);
    assertThat(fields.findById(f.getId()).orElseThrow().getIdUser()).isNull();
    assertThat(subscriptions.delEntityWithUserId(s.getId(), reader.getIdUser())).isEqualTo(1);
    assertThat(fields.findById(f.getId())).isEmpty();
    assertThat(
        jdbc.queryForObject("SELECT COUNT(*) FROM udf_data_shared WHERE id_udf_metadata = ?", Integer.class, f.getId()))
            .isZero();
  }

  @Test
  void oneSubscriberClaimsAndOtherCannotTakeTheField() throws Exception {
    var f = field(UDFDataType.UDF_String, (byte) 91);
    changeMode(f.getId(), UDFShareMode.UDF_SHARE_READ_WRITE);
    var first = subscribe(f.getId(), reader, (byte) 91);
    var second = subscribe(f.getId(), other, (byte) 92);
    fields.delEntityWithUserId(f.getId(), owner.getIdUser());
    subscriptions.claimOwnership(first.getId(), reader);
    assertThat(fields.findById(f.getId()).orElseThrow().getIdUser()).isEqualTo(reader.getIdUser());
    assertThatExceptionOfType(DataViolationException.class)
        .isThrownBy(() -> subscriptions.claimOwnership(second.getId(), other))
        .satisfies(error -> assertThat(error.getDataViolation()).extracting(v -> v.getMessageKey())
            .containsExactly("g.udf.claim.taken"));
  }

  @Test
  void accountDeletionKeepsSubscribersDataAndRemovesAnAbandonedOrphan() throws Exception {
    var f = field(UDFDataType.UDF_String, (byte) 91);
    changeMode(f.getId(), UDFShareMode.UDF_SHARE_READ);
    subscribe(f.getId(), reader, (byte) 91);
    em.persist(new UDFDataShared(new UDFDataSharedKey(f.getId(), 100), "Security", "retained"));
    em.flush();
    new MySqlDeleteMyData(jdbc, owner).deleteMyData();
    em.clear();
    assertThat(fields.findById(f.getId()).orElseThrow().getIdUser()).isNull();
    assertThat(jdbc.queryForObject("SELECT field_value FROM udf_data_shared WHERE id_udf_metadata = ?", String.class,
        f.getId())).isEqualTo("retained");
    new MySqlDeleteMyData(jdbc, reader).deleteMyData();
    em.clear();
    assertThat(fields.findById(f.getId())).isEmpty();
  }

  @Test
  void editingAnAutomaticRuleStillIncrementsItsVersion() throws Exception {
    var existing = field(UDFDataType.UDF_String, (byte) 91);
    existing.setFillSource(UDFFillSource.TEMPLATE);
    existing.setFillRule("https://example.com/{ticker}");
    em.flush();
    var draft = new UDFMetadataSecurity(0, 0, owner.getIdUser(), null, existing.getDescription(), null,
        UDFDataType.UDF_String, "0,80", (byte) 91);
    draft.setFillSource(UDFFillSource.TEMPLATE);
    draft.setFillRule("https://example.com/changed/{ticker}");
    var saved = fields.saveOnlyAttributes(draft, existing, fields.getUpdatePropertyLevels(existing));
    em.flush();
    em.clear();
    assertThat(fields.findById(saved.getId()).orElseThrow().getFillRuleVersion()).isEqualTo(1);
  }

  @Test
  void aLanguageModelFieldCanBeSharedAndASharedFieldCanSwitchToTheLanguageModel() throws Exception {
    var f = field(UDFDataType.UDF_Numeric, (byte) 91);
    f.setFillSource(UDFFillSource.LLM);
    f.setFillRule("Find the value on the official page");
    em.flush();
    UDFMetadataSecurity draft = new UDFMetadataSecurity(0, 0, owner.getIdUser(), null, f.getDescription(), null,
        UDFDataType.UDF_Numeric, "12,5", (byte) 91);
    draft.setFillSource(UDFFillSource.LLM);
    draft.setFillRule(f.getFillRule());
    draft.setShareMode(UDFShareMode.UDF_SHARE_READ);
    var saved = fields.saveOnlyAttributes(draft, f, fields.getUpdatePropertyLevels(f));
    assertThat(saved.getShareMode()).isEqualTo(UDFShareMode.UDF_SHARE_READ);
    assertThat(saved.getFillSource()).isEqualTo(UDFFillSource.LLM);

    UDFMetadataSecurity manual = new UDFMetadataSecurity(0, 0, owner.getIdUser(), null, "UDF sharing link", null,
        UDFDataType.UDF_URLString, null, (byte) 92);
    em.persist(manual);
    em.flush();
    var shared = changeMode(manual.getId(), UDFShareMode.UDF_SHARE_READ);
    UDFMetadataSecurity toModel = new UDFMetadataSecurity(0, 0, owner.getIdUser(), null, shared.getDescription(), null,
        UDFDataType.UDF_URLString, null, (byte) 92);
    toModel.setFillSource(UDFFillSource.LLM);
    toModel.setFillRule("Find the value on the official page");
    toModel.setShareMode(UDFShareMode.UDF_SHARE_READ);
    assertThat(fields.saveOnlyAttributes(toModel, shared, fields.getUpdatePropertyLevels(shared)).getFillSource())
        .isEqualTo(UDFFillSource.LLM);
  }

  private UDFMetadataSecurity toLanguageModel(Integer id, String context) throws Exception {
    em.flush();
    em.clear();
    UDFMetadataSecurity existing = fields.findById(id).orElseThrow();
    UDFMetadataSecurity draft = new UDFMetadataSecurity(0, 0, existing.getIdUser(), null, existing.getDescription(),
        existing.getDescriptionHelp(), existing.getUdfDataType(), existing.getFieldSize(), existing.getUiOrder());
    draft.setFillSource(UDFFillSource.LLM);
    draft.setFillRule(context);
    draft.setShareMode(existing.getShareMode());
    return fields.saveOnlyAttributes(draft, existing, fields.getUpdatePropertyLevels(existing));
  }

  @Test
  void aFieldOthersAdoptedSwitchesToTheLanguageModelOnlyAfterTheyLeft() throws Exception {
    UDFMetadataSecurity manual = new UDFMetadataSecurity(0, 0, owner.getIdUser(), null, "UDF sharing link", null,
        UDFDataType.UDF_URLString, null, (byte) 92);
    em.persist(manual);
    em.flush();
    changeMode(manual.getId(), UDFShareMode.UDF_SHARE_READ);
    var adopted = subscribe(manual.getId(), reader, (byte) 92);
    as(owner);

    assertThatExceptionOfType(DataViolationException.class)
        .isThrownBy(() -> toLanguageModel(manual.getId(), "Find the value on the official page"))
        .satisfies(error -> assertThat(error.getDataViolation()).extracting(v -> v.getMessageKey())
            .containsExactly("gt.udf.fill.llm.has.subscribers"));
    em.clear();
    assertThat(fields.findById(manual.getId()).orElseThrow().getFillSource()).isEqualTo(UDFFillSource.MANUAL);

    subscriptions.delEntityWithUserId(adopted.getId(), reader.getIdUser());
    assertThat(toLanguageModel(manual.getId(), "Find the value on the official page").getFillSource())
        .isEqualTo(UDFFillSource.LLM);

    subscribe(manual.getId(), reader, (byte) 92);
    as(owner);
    assertThat(toLanguageModel(manual.getId(), "Find the value on the issuer page").getFillRule())
        .isEqualTo("Find the value on the issuer page");
  }
}
