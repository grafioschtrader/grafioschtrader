package grafioschtrader.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** HTTP validation contract without starting Spring or touching a database. */
class YamlValidationResourceTest {
  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new YamlValidationResource()).build();

  @Test
  void returnsLocationsAndAllowsOnlyKnownFormats() throws Exception {
    mvc.perform(post("/api/yaml/validate").contentType(MediaType.APPLICATION_JSON)
        .content("{\"format\":\"FEES\",\"yaml\":\"rules: [\"}")).andExpect(status().isOk())
        .andExpect(jsonPath("$[0].category").value("SYNTAX")).andExpect(jsonPath("$[0].line").isNumber());
    mvc.perform(post("/api/yaml/validate").contentType(MediaType.APPLICATION_JSON)
        .content("{\"format\":\"OTHER\",\"yaml\":\"a: b\"}")).andExpect(status().isBadRequest());
    mvc.perform(post("/api/yaml/validate").contentType(MediaType.APPLICATION_JSON)
        .content("{\"format\":\"CUSTODY\",\"yaml\":\"Account: {accruedFees: 0}\"}")).andExpect(status().isOk())
        .andExpect(content().json("[]"));
  }
}
