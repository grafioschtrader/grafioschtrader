package grafioschtrader.connector.securitysearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.dto.SecurityBaseDataDto;
import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.SecuritySearchStatus;

/** BIVA against the issuer answer for Grupo México (MXP370841019) recorded on 2026-10-12. */
class BivaSecuritySearchTest {

  private static final String GMEXICO = "MXP370841019";
  private static final String PATH = "/emisoras/empresas/isin/" + GMEXICO;

  private FixtureHttpServer server;

  @BeforeEach
  void startServer() {
    server = new FixtureHttpServer();
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  private BivaSecuritySearch biva() {
    return new BivaSecuritySearch(server.baseUrl(), Duration.ofSeconds(5));
  }

  @Test
  @DisplayName("Issuer page as stock exchange hyperlink; no ticker, the issuer code lacks the series")
  void issuer() {
    server.fixture(PATH, "biva/issuer-gmexico.json");
    SecuritySearchResult result = biva().search(SecuritySearchQuery.of(GMEXICO, "MXN", "BIVA", null));
    assertEquals(SecuritySearchStatus.OK, result.status());
    SecuritySearchCandidateDto candidate = result.candidates().getFirst();
    assertEquals("BIVA", candidate.mic());
    assertNull(candidate.providerSymbol(), "a symbol would become the ticker when the master data fails");
    assertEquals("1693", candidate.providerInstrumentId());

    SecurityBaseDataDto data = biva().getSecurityBaseData(candidate, Locale.GERMAN).data();
    assertNull(data.tickerSymbol());
    assertNull(data.currency());
    assertEquals("GRUPO MEXICO, S.A.B. DE C.V.", data.name());
    assertEquals("https://www.biva.mx/empresas/emisoras_inscritas/emisoras_inscritas?instrumento=null&tipo_valor=null"
        + "&sector=null&sub_sector=null&ramo=null&sub_ramo=null&emisora_id=1693&clave=GMEXICO&canceladas=null"
        + "&biva=null&page=1", data.stockexchangeLink());
    assertEquals(LocalDate.of(2018, 7, 2), data.listedSince());
    assertEquals("GMEXICO", data.providerAttributes().get("clave"));
    assertEquals("BMV", data.providerAttributes().get("bolsa"));
  }

  @Test
  @DisplayName("Asked for BIVA queries only; an unknown ISIN (HTTP 404) gives no listing")
  void scopeAndUnknown() {
    assertFalse(biva().supports(SecuritySearchQuery.of(GMEXICO, "MXN", "XNYS", null)));
    assertTrue(biva().supports(SecuritySearchQuery.of(GMEXICO, "MXN", "BIVA", null)));
    server.status(PATH, 404, "{\"status\":404,\"error\":\"Not Found\"}");
    assertTrue(biva().search(SecuritySearchQuery.of(GMEXICO, "MXN", "BIVA", null)).candidates().isEmpty());
    assertTrue(BivaSecuritySearch.parse("{\"status\":404}", GMEXICO).isEmpty());
  }
}
