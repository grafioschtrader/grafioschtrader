package grafioschtrader.transform;


import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Optional;
import java.util.ResourceBundle;

public class LoadFilesDialog {

  private final CheckBox cbSetOwner = new CheckBox();
  private final ResourceBundle bundle;
  private Stage stage;

  public LoadFilesDialog(ResourceBundle bundle) {
    this.bundle = bundle;
  }

  public Optional<ImportSelectionModel> createDialog(Stage primaryState) {

    Dialog<ImportSelectionModel> dialog = new Dialog<>();
    dialog.initOwner(primaryState);
    dialog.setTitle(bundle.getString("NEW_IMPORT"));
    dialog.setHeaderText(bundle.getString("NEW_IMPORT_EXACT"));


    // Set the button types.
    ButtonType bChooseFile = new ButtonType(bundle.getString("CHOOSE_FILE"), ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(bChooseFile, ButtonType.CANCEL);

    // Create the username and password labels and fields.
    VBox vbox = new VBox();
    vbox.setPadding(new Insets(20, 150, 10, 10));

    CheckBox cbImportDir = new CheckBox(bundle.getString("IMPORT_DIRECTORY"));

    Label lbExcludeRegEx = new Label(bundle.getString("EXLUDE_FILE_PATTERN"));
    TextField tfExcludeRegEx = new TextField();
    CheckBox cbIncludeSubDir = new CheckBox(bundle.getString("IMPORT_INCLUDE_SUB_DIR"));
    vbox.getChildren().add(cbImportDir);

    cbImportDir.setOnAction((event) -> {
      if (cbImportDir.isSelected()) {
        HBox hExcludeRegEx = new HBox();
        vbox.getChildren().add(hExcludeRegEx);
        hExcludeRegEx.getChildren().add(lbExcludeRegEx);
        hExcludeRegEx.getChildren().add(tfExcludeRegEx);
        vbox.getChildren().add(cbIncludeSubDir);
      } else {
        cbIncludeSubDir.setSelected(false);
        vbox.getChildren().remove(cbIncludeSubDir);
      }
      dialog.getDialogPane().getScene().getWindow().sizeToScene();
    });

    dialog.getDialogPane().setContent(vbox);
    Platform.runLater(() -> cbImportDir.requestFocus());

    dialog.setResultConverter(dialogButton -> {
      if (dialogButton == bChooseFile) {
        return new ImportSelectionModel(cbImportDir.isSelected(), cbIncludeSubDir.isSelected(), tfExcludeRegEx.getText());
      }
      return null;
    });

    Optional<ImportSelectionModel> result = dialog.showAndWait();

    return result;
  }
}
