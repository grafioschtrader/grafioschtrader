package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Statistics for bulk template upload operation results, categorizing each file by processing outcome")
public class SuccessFailedImportTransactionTemplate {
  @Schema(description = "Number of new templates successfully created from uploaded files")
  public int successNew;
  
  @Schema(description = "Number of existing templates successfully updated with new content")
  public int successUpdated;
  
  @Schema(description = "Number of files rejected due to insufficient user permissions")
  public int notOwner;
  
  @Schema(description = "Number of files with invalid filename format or metadata")
  public int fileNameError;
  
  @Schema(description = "Number of files with invalid template content or structure")
  public int contentError;
}
