import java.nio.file.*;
import grafioschtrader.platformimport.pdf.ImportTransactionHelperPdf;
class Extract {
  public static void main(String[] args) throws Exception {
    try (var input = Files.newInputStream(Path.of(args[0]))) {
      String text = ImportTransactionHelperPdf.transFormPDFToTxt(input)
          .replace("732746", "999999").replace("F4CORACT03/26/11664936/1", "EXAMPLE/2026/1");
      Files.writeString(Path.of(args[1]), text);
    }
  }
}
