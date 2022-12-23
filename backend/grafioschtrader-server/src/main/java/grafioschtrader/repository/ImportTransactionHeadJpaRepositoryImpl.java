package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionPlatform;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.User;
import grafioschtrader.platform.GenericTransactionImport;
import grafioschtrader.platform.IPlatformTransactionImport;
import grafioschtrader.platformimport.ImportTransactionHelper;
import grafioschtrader.repository.ImportTransactionPosJpaRepositoryImpl.SavedImpPosAndTransaction;
import grafioschtrader.types.TemplateFormatType;
import jakarta.transaction.Transactional;

public class ImportTransactionHeadJpaRepositoryImpl extends BaseRepositoryImpl<ImportTransactionHead>
    implements ImportTransactionHeadJpaRepositoryCustom {

  @Autowired
  private ImportTransactionHeadJpaRepository importTransactionHeadJpaRepository;

  @Autowired
  private ImportTransactionTemplateJpaRepository importTransactionTemplateJpaRepository;

  @Autowired
  private ImportTransactionPosJpaRepository importTransactionPosJpaRepository;

  @Autowired
  private ImportTransactionPosFailedJpaRepository importTransactionPosFailedJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private SecurityaccountJpaRepository securityaccountJpaRepository;

  @Autowired(required = false)
  public List<IPlatformTransactionImport> platformTransactionImportList = new ArrayList<>();

  @Override
  public ImportTransactionHead saveOnlyAttributes(ImportTransactionHead importTransactionHead,
      ImportTransactionHead existingEntity, final Set<Class<? extends Annotation>> updatePropertyLevelClasses) {

    ImportTransactionHead createImportTransactionHead = importTransactionHead;
    if (existingEntity != null) {
      createImportTransactionHead = existingEntity;
      createImportTransactionHead.setName(importTransactionHead.getName());
      createImportTransactionHead.setNote(importTransactionHead.getNote());
    }
    return importTransactionHeadJpaRepository.save(createImportTransactionHead);
  }

  @Override
  @Modifying
  @Transactional
  public SuccessFailedDirectImportTransaction uploadPdfFileSecurityAccountTransactions(Integer idSecuritycashaccount,
      MultipartFile[] uploadFiles) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Securityaccount securityaccount = this.securityaccountJpaRepository
        .findByIdSecuritycashAccountAndIdTenant(idSecuritycashaccount, user.getIdTenant());
    if (securityaccount != null) {
      ImportTransactionHead importTransactionHead = new ImportTransactionHead(user.getIdTenant(), securityaccount,
          LocalDateTime.now().toString(), "Computer generated");
      importTransactionHead = importTransactionHeadJpaRepository.save(importTransactionHead);
      this.getTemplateReadFilesAndSaveAsImport(importTransactionHead, uploadFiles, null);

      List<ImportTransactionPos> importTransactionPosList = importTransactionPosJpaRepository
          .findByIdTransactionHeadAndIdTenant(importTransactionHead.getIdTransactionHead(), user.getIdTenant());
      Optional<ImportTransactionPos> itpOpt = importTransactionPosList.stream()
          .filter(importTransactionPos -> !importTransactionPos.isReadyForTransaction()).findFirst();
      if (itpOpt.isPresent()) {
        return new SuccessFailedDirectImportTransaction(importTransactionHead.getIdTransactionHead());
      } else {
        // Every pdf import is ready for transaction
        List<SavedImpPosAndTransaction> savedImpPosAndTransactions = importTransactionPosJpaRepository
            .createAndSaveTransactionsImpPos(importTransactionPosList, null);
        Optional<ImportTransactionPos> itpErrorOpt = importTransactionPosList.stream()
            .filter(itp -> itp.getTransactionError() != null).findFirst();
        if (itpErrorOpt.isPresent()) {
          // Failed to create a transaction
          return new SuccessFailedDirectImportTransaction(importTransactionHead.getIdTransactionHead());
        } else {
          // Every transaction was created
          importTransactionPosJpaRepository.deleteAll(
              savedImpPosAndTransactions.stream().map(spat -> spat.importTransactionPos).collect(Collectors.toList()));
          importTransactionHeadJpaRepository.delete(importTransactionHead);
          int noOfDifferentSecurities = (int) savedImpPosAndTransactions.stream()
              .map(spat -> spat.transaction.getSecurity().getIdSecuritycurrency()).distinct().count();
          return new SuccessFailedDirectImportTransaction(savedImpPosAndTransactions.size(), noOfDifferentSecurities);
        }
      }

    } else {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
  }

  @Override
  public void uploadCsvPdfTxtFileSecurityAccountTransactions(Integer idTransactionHead, MultipartFile[] uploadFiles,
      Integer idTransactionImportTemplate) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    ImportTransactionHead importTransactionHead = importTransactionHeadJpaRepository.getReferenceById(idTransactionHead);
    if (user.getIdTenant().equals(importTransactionHead.getIdTenant())) {
      this.getTemplateReadFilesAndSaveAsImport(importTransactionHead, uploadFiles, idTransactionImportTemplate);
    } else {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }

  }

  private void getTemplateReadFilesAndSaveAsImport(ImportTransactionHead importTransactionHead,
      MultipartFile[] uploadFiles, Integer idTransactionImportTemplate) throws Exception {
    SingleMultiTemplateFormatType singleMultiTemplateFormatType = getTemplateFormatTypeOfUpload(uploadFiles);

    if (singleMultiTemplateFormatType != null) {
      ImportTransactionPlatform importTransactionPlatform = importTransactionHead.getSecurityaccount()
          .getTradingPlatformPlan().getImportTransactionPlatform();

      List<ImportTransactionTemplate> importTransactionTemplateList = importTransactionTemplateJpaRepository
          .findByIdTransactionImportPlatformAndTemplateFormatTypeOrderByTemplatePurpose(
              importTransactionPlatform.getIdTransactionImportPlatform(),
              singleMultiTemplateFormatType.templateFormatType.getValue());

      Optional<IPlatformTransactionImport> pti = platformTransactionImportList.stream()
          .filter(platformTransactionImport -> platformTransactionImport.getID()
              .equals(importTransactionPlatform.getIdCsvImportImplementation()))
          .findFirst();

      if (pti.isPresent()) {
        readUploadedFilesAndSaveAsImport(importTransactionHead, importTransactionTemplateList, pti.get(), uploadFiles,
            singleMultiTemplateFormatType, idTransactionImportTemplate);
      } else {
        // Use Generic
        IPlatformTransactionImport ptiGeneric = new GenericTransactionImport();
        readUploadedFilesAndSaveAsImport(importTransactionHead, importTransactionTemplateList, ptiGeneric, uploadFiles,
            singleMultiTemplateFormatType, idTransactionImportTemplate);
      }
    }
  }

  private SingleMultiTemplateFormatType getTemplateFormatTypeOfUpload(MultipartFile[] uploadFiles) {
    if (uploadFiles.length > 0) {
      String ending = uploadFiles[0].getOriginalFilename().toLowerCase();
      if (ending.endsWith(ImportTransactionHelper.CSV_FILE_NAME_ENDING)) {
        return new SingleMultiTemplateFormatType(TemplateFormatType.CSV, uploadFiles.length == 1, false);
      } else if (ending.endsWith(ImportTransactionHelper.TXT_FILE_NAME_ENDING)
          || ending.endsWith(ImportTransactionHelper.PDF_FILE_NAME_ENDING)) {
        return new SingleMultiTemplateFormatType(TemplateFormatType.PDF, uploadFiles.length == 1,
            ending.endsWith(ImportTransactionHelper.TXT_FILE_NAME_ENDING));
      }
    }
    return null;
  }

  private void readUploadedFilesAndSaveAsImport(ImportTransactionHead importTransactionHead,
      List<ImportTransactionTemplate> importTransactionTemplateList, IPlatformTransactionImport pti,
      MultipartFile[] uploadFiles, SingleMultiTemplateFormatType singleMultiTemplateFormatType,
      Integer idTransactionImportTemplate) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (singleMultiTemplateFormatType.singleFile) {
      if (singleMultiTemplateFormatType.gtTransformed) {
        // Import a GT (Grafiosch Transform) Transform txt file
        pti.importGTTransform(importTransactionHead, uploadFiles[0], importTransactionTemplateList,
            importTransactionPosJpaRepository, securityJpaRepository, importTransactionPosFailedJpaRepository,
            user.createAndGetJavaLocale());
      } else if (singleMultiTemplateFormatType.templateFormatType == TemplateFormatType.CSV) {
        // Import a csv file with many transaction
        pti.importCSV(importTransactionHead, uploadFiles[0], importTransactionTemplateList,
            importTransactionPosJpaRepository, securityJpaRepository, importTransactionPosFailedJpaRepository,
            user.createAndGetJavaLocale(), idTransactionImportTemplate);
      } else {
        // import single pdf
        pti.importSinglePdfAsPdf(importTransactionHead, uploadFiles[0], importTransactionTemplateList,
            importTransactionPosJpaRepository, securityJpaRepository, importTransactionPosFailedJpaRepository,
            user.createAndGetJavaLocale());
      }
    } else {
      // 2 files and more
      if (singleMultiTemplateFormatType.templateFormatType == TemplateFormatType.PDF) {
        pti.importMultiplePdfAsPdf(importTransactionHead, uploadFiles, importTransactionTemplateList,
            importTransactionPosJpaRepository, securityJpaRepository, importTransactionPosFailedJpaRepository,
            user.createAndGetJavaLocale());
      } else {
        // TODO not the right format
      }
    }
  }

  static class SingleMultiTemplateFormatType {
    public final TemplateFormatType templateFormatType;
    public final boolean singleFile;
    public final boolean gtTransformed;

    public SingleMultiTemplateFormatType(TemplateFormatType templateFormatType, boolean singleFile,
        boolean gtTransformed) {

      this.templateFormatType = templateFormatType;
      this.singleFile = singleFile;
      this.gtTransformed = gtTransformed;
    }
  }

  @Override
  public int delEntityWithTenant(Integer id, Integer idTenant) {
    return importTransactionHeadJpaRepository.deleteByIdTransactionHeadAndIdTenant(id, idTenant);
  }

  public static class SuccessFailedDirectImportTransaction {
    public Integer idTransactionHead;
    public Integer noOfImportedTransactions;
    public Integer noOfDifferentSecurities;
    public boolean failed = true;

    public SuccessFailedDirectImportTransaction(Integer idTransactionHead) {
      this.idTransactionHead = idTransactionHead;
    }

    public SuccessFailedDirectImportTransaction(Integer noOfImportedTransactions, Integer noOfDifferentSecurities) {
      this.noOfImportedTransactions = noOfImportedTransactions;
      this.noOfDifferentSecurities = noOfDifferentSecurities;

      this.failed = false;
    }

  }

}
