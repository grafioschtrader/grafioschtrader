package grafioschtrader.transform;


import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;

import java.util.ResourceBundle;

public class TransformTableView  {

  private final TableView<TransformTableViewModel> tableView = new TableView<>();
  private final ObservableList<TransformTableViewModel> transformTableViewModelList = FXCollections.observableArrayList();
  private final ResourceBundle bundle;

  public TransformTableView(ResourceBundle bundle) {
    this.bundle = bundle;
  }

  public TableView<TransformTableViewModel> getTransformTableView(){
    tableView.setEditable(false);

    TableColumn<TransformTableViewModel, String> tcSourceText =  new TableColumn<>(bundle.getString("SOURCE_LINE"));
    tcSourceText.setMinWidth(100);
    tcSourceText.setCellValueFactory(new PropertyValueFactory<>("sourceText"));

    tcSourceText.setCellFactory(TextFieldTableCell.<TransformTableViewModel>forTableColumn());
    tcSourceText.setOnEditCommit(
            (CellEditEvent<TransformTableViewModel, String> t) -> {
              ((TransformTableViewModel) t.getTableView().getItems().get(
                      t.getTablePosition().getRow())
              ).setSourceText(t.getNewValue());
            });
    tcSourceText.setMinWidth(300);

    TableColumn<TransformTableViewModel, String> tcTargetText = new TableColumn<>(bundle.getString("TARGET_LINE"));
    tcTargetText.setMinWidth(100);
    tcTargetText.setCellValueFactory(new PropertyValueFactory<>("targetText"));
    tcTargetText.setCellFactory(TextFieldTableCell.<TransformTableViewModel>forTableColumn());
    tcTargetText.setOnEditCommit(
            (CellEditEvent<TransformTableViewModel, String> t) -> {
              ((TransformTableViewModel) t.getTableView().getItems().get(
                      t.getTablePosition().getRow())
              ).setTargetText(t.getNewValue());
            });
    tcTargetText.setMinWidth(300);

    TableColumn<TransformTableViewModel, Boolean> tcRemove = new TableColumn<>(bundle.getString("REMOVE_LINE"));
    tcRemove.setMinWidth(200);

    tcRemove.setCellValueFactory(param -> param.getValue().isRemoveTextObservable());
    tcRemove.setCellFactory(CheckBoxTableCell.forTableColumn(tcRemove));

    tableView.setItems(transformTableViewModelList);
    tableView.getColumns().addAll(tcSourceText, tcTargetText, tcRemove);

    MenuItem miDelete = new MenuItem("Delete");
    miDelete.setOnAction(event -> {
      TransformTableViewModel selectedTransformTableViewModel = tableView.getSelectionModel().getSelectedItem();
      if(selectedTransformTableViewModel != null) {
        transformTableViewModelList.remove(selectedTransformTableViewModel);
      }
    });
    tableView.setContextMenu(new ContextMenu(miDelete));

    return tableView;
  }

  public void addTransformation(String sourceText, String targetText, boolean removeText) {
    transformTableViewModelList.add(new TransformTableViewModel(sourceText, targetText, removeText));
  }

  public ObservableList<TransformTableViewModel> getTransformTableViewModelList() {
    return transformTableViewModelList;
  }
}
