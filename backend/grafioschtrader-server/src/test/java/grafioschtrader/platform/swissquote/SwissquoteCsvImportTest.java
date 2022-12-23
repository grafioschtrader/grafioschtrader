package grafioschtrader.platform.swissquote;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
public class SwissquoteCsvImportTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Test
  public void firstUploadWordsTest() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "swissqoute.csv", MediaType.TEXT_PLAIN_VALUE,
        "Hello, World!".getBytes());

    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    mockMvc.perform(multipart("/upload").file(file)).andExpect(status().isOk());
  };
}
