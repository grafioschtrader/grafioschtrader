package grafioschtrader.platform;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.LocaleUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.ImportTransactionPlatform;
import grafioschtrader.platformimport.FormTemplateCheck;
import grafioschtrader.repository.ImportTransactionPlatformJpaRepository;
import grafioschtrader.repository.ImportTransactionTemplateJpaRepository;

public abstract class PdfReaderTest {

  @Autowired
  ImportTransactionPlatformJpaRepository importTransactionPlatformJpaRepository;

  @Autowired
  ImportTransactionTemplateJpaRepository importTransactionTemplateJpaRepository;

  abstract protected String getPlatformName();
  
  @Test
  void checkFormAgainstTemplateTest() {
    try {
      List<File> files = getAllTextFiles();
      ImportTransactionPlatform importTransactionPlatform = importTransactionPlatformJpaRepository
          .findFristByNameContaining(getPlatformName());
      FormTemplateCheck formTemplateCheck = new FormTemplateCheck();
      formTemplateCheck.setIdTransactionImportPlatform(importTransactionPlatform.getIdTransactionImportPlatform());
      for (File file : files) {
        formTemplateCheck.setPdfAsTxt(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
        FormTemplateCheck formTemplateCheckRc = importTransactionTemplateJpaRepository
            .checkFormAgainstTemplate(formTemplateCheck, LocaleUtils.toLocale("de_CH"));
        System.out.println("File: "+ file.getName());
        assertNull(formTemplateCheckRc.getFailedParsedTemplateStateList());
      }
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private List<File> getAllTextFiles() throws IOException {
    String platformDir = getPlatformName().toLowerCase().replaceAll(" ", "_");
    return Files.list(Paths.get("src/test/resources/" + platformDir)).filter(Files::isRegularFile).map(Path::toFile)
        .filter(f -> f.getName().endsWith(".txt")).collect(Collectors.toList());
  }

}
