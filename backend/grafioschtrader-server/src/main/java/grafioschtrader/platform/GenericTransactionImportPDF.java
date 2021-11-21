package grafioschtrader.platform;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionPosFailed;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.platformimport.ImportProperties;
import grafioschtrader.platformimport.pdf.ImportTransactionHelperPdf;
import grafioschtrader.platformimport.pdf.ParseFormInputPDFasTXT;
import grafioschtrader.platformimport.pdf.TemplateConfigurationPDFasTXT;
import grafioschtrader.repository.ImportTransactionPosFailedJpaRepository;
import grafioschtrader.repository.ImportTransactionPosJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * It is a generic importer CSV and PDF importer. It can be used for the most
 * online broker.
 *
 *
 * @author Hugo Graf
 *
 */
public class GenericTransactionImportPDF extends GenericTransactionImportCsvPdfBase {

  public GenericTransactionImportPDF(final ImportTransactionHead importTransactionHead,
      List<ImportTransactionTemplate> importTransactionTemplateList) {
    super(importTransactionHead, importTransactionTemplateList);
  }

  public void importMultiplePdfForm(MultipartFile[] uploadFiles,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale)
      throws Exception {
    Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> templateScannedMap = ImportTransactionHelperPdf
        .readTemplates(importTransactionTemplateList, userLocale);

    for (MultipartFile uploadFile : uploadFiles) {
      this.parseSinglePdfForm(templateScannedMap, uploadFile, importTransactionPosJpaRepository, securityJpaRepository,
          importTransactionPosFailedJpaRepository);
    }
  }

  public void importSinglePdfForm(MultipartFile uploadFile,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale)
      throws Exception {
    Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> templateScannedMap = ImportTransactionHelperPdf
        .readTemplates(importTransactionTemplateList, userLocale);

    this.parseSinglePdfForm(templateScannedMap, uploadFile, importTransactionPosJpaRepository, securityJpaRepository,
        importTransactionPosFailedJpaRepository);
  }

  private void parseSinglePdfForm(Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> templateScannedMap,
      MultipartFile uploadFile, ImportTransactionPosJpaRepository importTransactionPosJpaRepository,
      SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository) throws Exception {
    List<ImportProperties> importPropertiesList = null;
    if (!uploadFile.isEmpty()) {
      try (InputStream is = uploadFile.getInputStream()) {
        ParseFormInputPDFasTXT parseInputPDFasTXT = new ParseFormInputPDFasTXT(
            cleanReadPDF(ImportTransactionHelperPdf.transFormPDFToTxt(is)), templateScannedMap);
        importPropertiesList = parseInputPDFasTXT.parseInput();
        if (importPropertiesList != null) {
          Portfolio portfolio = importTransactionHead.getSecurityaccount().getPortfolio();
          // Found matching template
          ImportTransactionTemplate importTransactionTemplate = parseInputPDFasTXT
              .getSuccessTemplate(importPropertiesList);
          checkAndSaveSuccessImportTransaction(importTransactionTemplate, portfolio.getCashaccountList(),
              importPropertiesList, uploadFile.getOriginalFilename(), importTransactionPosJpaRepository,
              securityJpaRepository);
        } else {
          this.failedParse(parseInputPDFasTXT, uploadFile.getOriginalFilename(), null,
              importTransactionPosJpaRepository, importTransactionPosFailedJpaRepository);
        }
      }
    }
  }

  /**
   * Override this if the PDF as text needs some cleaning after reading as text
   * before it is processed
   *
   * @param readPDFAsText
   * @return
   */
  protected String cleanReadPDF(String readPDFAsText) {
    return readPDFAsText;
  }

  public void importGTTransform(MultipartFile uploadFile,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository, Locale userLocale) {

    Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> templateScannedMap = ImportTransactionHelperPdf
        .readTemplates(importTransactionTemplateList, userLocale);
    parseGTTransform(uploadFile, templateScannedMap, importTransactionPosJpaRepository, securityJpaRepository,
        importTransactionPosFailedJpaRepository);
  }

  private void parseGTTransform(MultipartFile uploadFile,
      Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> templateScannedMap,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository, SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository) {
    Pattern startNewFormPatter = Pattern.compile("^\\[(\\d+)\\|(.*)\\]$");
    int lastFileNumber = 0;
    String lastFile = null;
    if (!uploadFile.isEmpty()) {
      Portfolio portfolio = importTransactionHead.getSecurityaccount().getPortfolio();

      try (InputStream is = uploadFile.getInputStream();
          BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
        StringBuilder singleFormSB = null;

        while (reader.ready()) {
          String line = reader.readLine();
          Matcher matcher = startNewFormPatter.matcher(line);
          if (matcher.find()) {
            if (singleFormSB != null) {
              parseAndSaveImportTransactionPos(portfolio.getCashaccountList(), templateScannedMap, singleFormSB,
                  lastFileNumber, lastFile, importTransactionPosJpaRepository, securityJpaRepository,
                  importTransactionPosFailedJpaRepository);
            }
            lastFileNumber = Integer.parseInt(matcher.group(1));
            lastFile = matcher.group(2);
            singleFormSB = new StringBuilder("");
          } else {
            singleFormSB.append(line).append(System.lineSeparator());
          }

        }
        if (singleFormSB != null) {
          // For the last form
          parseAndSaveImportTransactionPos(portfolio.getCashaccountList(), templateScannedMap, singleFormSB,
              lastFileNumber, lastFile, importTransactionPosJpaRepository, securityJpaRepository,
              importTransactionPosFailedJpaRepository);
        }
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  /**
   * Every PDF form is saved in a transaction.
   *
   * @param cashaccountList
   * @param templateScannedMap
   * @param singleFormSB
   * @param lastFileNumber
   * @param lastFile
   * @param importTransactionPosJpaRepository
   * @param securityJpaRepository
   * @param importTransactionPosFailedJpaRepository
   */
  @Transactional
  @Modifying
  private void parseAndSaveImportTransactionPos(List<Cashaccount> cashaccountList,
      Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> templateScannedMap, StringBuilder singleFormSB,
      int lastFileNumber, String lastFile, ImportTransactionPosJpaRepository importTransactionPosJpaRepository,
      SecurityJpaRepository securityJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository) {
    try {
      ParseFormInputPDFasTXT parseInputPDFasTXT = new ParseFormInputPDFasTXT(singleFormSB.toString(),
          templateScannedMap);
      List<ImportProperties> importPropertiesList = parseInputPDFasTXT.parseInput(lastFileNumber);
      if (importPropertiesList != null) {
        // Found matching template
        ImportTransactionTemplate importTransactionTemplate = parseInputPDFasTXT
            .getSuccessTemplate(importPropertiesList);

        checkAndSaveSuccessImportTransaction(importTransactionTemplate, cashaccountList, importPropertiesList, lastFile,
            importTransactionPosJpaRepository, securityJpaRepository);
      } else {
        this.failedParse(parseInputPDFasTXT, lastFile, lastFileNumber, importTransactionPosJpaRepository,
            importTransactionPosFailedJpaRepository);
      }

    } catch (Exception e) {
      // TODO
      e.printStackTrace();
    }
  }

  private void failedParse(ParseFormInputPDFasTXT parseInputPDFasTXT, String orginalFileName, Integer lastFileNumber,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository,
      ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository) {
    ImportTransactionPos importTransactionPos = new ImportTransactionPos(importTransactionHead.getIdTenant(),
        orginalFileName, importTransactionHead.getIdTransactionHead());
    importTransactionPos.setIdFilePart(lastFileNumber);
    importTransactionPos = importTransactionPosJpaRepository.save(importTransactionPos);
    List<ImportTransactionPosFailed> importTransactionPosFailedList = parseInputPDFasTXT
        .getImportTransactionPosFailed(importTransactionPos.getIdTransactionPos());
    importTransactionPosFailedJpaRepository.saveAll(importTransactionPosFailedList);
  }

}
