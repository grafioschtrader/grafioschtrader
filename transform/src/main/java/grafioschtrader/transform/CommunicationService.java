package grafioschtrader.transform;

import javafx.collections.ObservableList;
import javafx.scene.control.TextArea;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class CommunicationService {

  private final static String RemoveEmptyLinePattern = "(?m)^\\s*$[\n\r]{1,}";

  private TextArea taPdfBefore;
  private TextArea taPdfAfter;

 // private String pdfAsText;
  private ObservableList<TransformTableViewModel> transformTableViewModelList;


  public CommunicationService(ObservableList<TransformTableViewModel> transformTableViewModelList) {
    this.transformTableViewModelList = transformTableViewModelList;
  }

  /**
   * Selection in FileTableView has changed
   *
   * @param fileTableViewModel
   */
  public void fileViewSelectionChanged(FileTableViewModel fileTableViewModel) {
    String pdfAsTxt = getPdfAsTextFromFilePath(fileTableViewModel.getAbsolutePath());
    taPdfBefore.setText(pdfAsTxt);
    setTransformTxt();
  }

  private String getPdfAsTextFromFilePath(Path path) {
    String pdfAsText = null;
    try (InputStream is = Files.newInputStream(path); PDDocument document = PDDocument.load(is)) {
      PDFTextStripper textStripper = new PDFTextStripper();
      textStripper.setSortByPosition(true);
      pdfAsText = removeEmptyLinesAndReduceSpaces(textStripper.getText(document));
    } catch (Exception e) {
      e.printStackTrace();
    }
    return pdfAsText;
  }

  public void setTransformTxt() {
    String transformedPdfAsTxt = reduceAndtransformPdfAsTxt(removeEmptyLinesAndReduceSpaces(taPdfBefore.getText()));
    taPdfAfter.setText(transformedPdfAsTxt);
  }

  private String reduceAndtransformPdfAsTxt(String pdfAsTxt) {
    String transformedPdfAsTxt = pdfAsTxt;

    for(TransformTableViewModel transformTableViewModel : transformTableViewModelList) {
      Pattern pattern = Pattern.compile(transformTableViewModel.getSourceText().replaceAll("\r\n|\r|\n", System.lineSeparator()), Pattern.LITERAL);
      transformedPdfAsTxt = pattern.matcher(transformedPdfAsTxt).replaceFirst(transformTableViewModel.isRemoveText()? ""
              : transformTableViewModel.getTargetText());
      transformedPdfAsTxt = removeEmptyLinesAndReduceSpaces(transformedPdfAsTxt);
    }
    return transformedPdfAsTxt;

  }

  private String removeEmptyLinesAndReduceSpaces(String rowPdfAsText) {
    return rowPdfAsText.replaceAll(RemoveEmptyLinePattern, "").replaceAll("\r\n|\r|\n", System.lineSeparator())
            .replaceAll(" +", " ").trim();
  }


  public void transformAndExportPdfAsTxt(ObservableList<FileTableViewModel> fileList, File targetFile) {
    int fileCounter = 1;
    try (FileWriter fileWriter = new FileWriter(targetFile)) {

      for (FileTableViewModel fileTableViewModel : fileList) {
        if (fileTableViewModel.getSelected()) {
          String fileHeader = String.format("%s[%d|%s]%s", (fileCounter == 1)? "": System.lineSeparator(), fileCounter++,
                  fileTableViewModel.getAbsolutePath(), System.lineSeparator());
          fileWriter.write(fileHeader);
          String pdfAsTxt = getPdfAsTextFromFilePath(fileTableViewModel.getAbsolutePath());
          String transformedPdfAsTxt = reduceAndtransformPdfAsTxt(pdfAsTxt);
          fileWriter.write(transformedPdfAsTxt);
        }
      }
    } catch (IOException e){
      e.printStackTrace();
    }

  }


  public void setTaPdfBefore(TextArea taPdfBefore) {
    this.taPdfBefore = taPdfBefore;
  }

  public void setTaPdfAfter(TextArea taPdfAfter) {
    this.taPdfAfter = taPdfAfter;
  }
}
