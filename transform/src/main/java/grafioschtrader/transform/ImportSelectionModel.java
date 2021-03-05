package grafioschtrader.transform;

import java.util.regex.Pattern;

public class ImportSelectionModel {
  public boolean importDirectory;
  public boolean includeSubDir;
  public String excludeFileNameRegEx;
  private Pattern excludeFileNamePattern;

  public ImportSelectionModel(boolean importDirectory, boolean includeSubDir, String excludeFileNameRegEx) {
    this.importDirectory = importDirectory;
    this.includeSubDir = includeSubDir;
    this.excludeFileNameRegEx = excludeFileNameRegEx;
    if(this.excludeFileNameRegEx != null && !this.excludeFileNameRegEx.isBlank()) {
      excludeFileNamePattern = Pattern.compile(excludeFileNameRegEx, Pattern.CASE_INSENSITIVE);
    }
  }

  public boolean isPassedFileNameFilter(String fileName) {
    if(excludeFileNamePattern != null ) {
      return !excludeFileNamePattern.matcher(fileName).find();
    } else {
      return true;
    }
  }


  @Override
  public String toString() {
    return "ImportSelectionModel{" +
            "importDirectory=" + importDirectory +
            ", includeSubDir=" + includeSubDir +
            ", excludeFileNameRegEx='" + excludeFileNameRegEx + '\'' +
            '}';
  }
}
