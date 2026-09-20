package grafioschtrader.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Tests the routes consumed by the bond terms dialog without starting an application or accessing a database. */
class TaxDataCouponOptionsTest {

  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TaxDataResource()).build();

  @Test
  @DisplayName("Bond terms can load the available coupon day-count conventions")
  void couponDayCountOptions() throws Exception {
    mvc.perform(get("/api/taxdata/coupondaycounts")).andExpect(status().isOk())
        .andExpect(jsonPath("$[0].key").value("ACT_ACT_ICMA")).andExpect(jsonPath("$[0].value").value("ACT_ACT_ICMA"))
        .andExpect(jsonPath("$[1].key").value("THIRTY_E_360")).andExpect(jsonPath("$[1].value").value("THIRTY_E_360"));
  }

  @Test
  @DisplayName("Bond terms can load the fallback and currency-specific day-count proposals")
  void couponDayCountDefaults() throws Exception {
    mvc.perform(get("/api/taxdata/coupondaycounts/defaults")).andExpect(status().isOk())
        .andExpect(jsonPath("$.fallback").value("ACT_ACT_ICMA"))
        .andExpect(jsonPath("$.byCurrency.CHF").value("THIRTY_E_360"));
  }
}
