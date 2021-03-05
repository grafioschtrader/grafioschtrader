package grafioschtrader.transform;


import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.ListBinding;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 *
 */
public class FileListTableView {

  private final CommunicationService communicationService;
  private CheckBox selectAllCheckBox;
  private TableView<FileTableViewModel> tableView;
  private Button exportButton;
  private ObservableList<FileTableViewModel> fileList = FXCollections.observableArrayList();

  private DateTimeFormatter formatter =
          DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
                  .withZone(ZoneId.systemDefault());

  public FileListTableView(CommunicationService communicationService) {
    this.communicationService = communicationService;
  }


  private final EventHandler<ActionEvent> event = new EventHandler<ActionEvent>() {
    @Override
    public void handle(ActionEvent event) {

      // Checking for an unselected employee in the table view.
      boolean unSelectedFlag = false;
      for (FileTableViewModel item : getFilesTableView().getItems()) {
        if (!item.getSelected()) {
          unSelectedFlag = true;
          break;
        }
      }
      /*
       * If at least one employee is not selected, then deselecting the check box in the table column header, else if all employees
       * are selected, then selecting the check box in the header.
       */
      if (unSelectedFlag) {
        getSelectAllCheckBox().setSelected(false);
      } else {
        getSelectAllCheckBox().setSelected(true);
      }

      // Checking for a selected employee in the table view.
      boolean selectedFlag = false;
      for (FileTableViewModel item : getFilesTableView().getItems()) {
        if (item.getSelected()) {
          selectedFlag = true;
          break;
        }
      }
      /*
       * If at least one employee is selected, then enabling the "Export" button, else if none of the employees are selected, then
       * disabling the "Export" button.
       */
      if (selectedFlag) {
        enableExportButton();
      } else {
        disableExportButton();
      }
    }
  };


  /**
   * Lazy getter for the selectAllCheckBox.
   *
   * @return selectAllCheckBox
   */
  public CheckBox getSelectAllCheckBox() {
    if (selectAllCheckBox == null) {
      final CheckBox selectAllCheckBox = new CheckBox();

      // Adding EventHandler to the CheckBox to select/deselect all employees in table.
      selectAllCheckBox.setOnAction(new EventHandler<ActionEvent>() {
        @Override
        public void handle(ActionEvent event) {
          // Setting the value in all the employees.
          for (FileTableViewModel item : getFilesTableView().getItems()) {
            item.setSelected(selectAllCheckBox.isSelected());
          }
          getExportButton().setDisable(!selectAllCheckBox.isSelected());
        }
      });

      this.selectAllCheckBox = selectAllCheckBox;
    }
    return selectAllCheckBox;
  }

  @SuppressWarnings("unchecked")
  public TableView<FileTableViewModel> getFilesTableView() {
    if (tableView == null) {
      // "Selected" column
      TableColumn<FileTableViewModel, Boolean> selectedCol = new TableColumn<FileTableViewModel, Boolean>();
      selectedCol.setGraphic(getSelectAllCheckBox());
      selectedCol.setMinWidth(40);
      selectedCol.setMaxWidth(40);
      selectedCol.setCellValueFactory(new PropertyValueFactory<FileTableViewModel, Boolean>("selected"));
      selectedCol.setCellFactory(new Callback<TableColumn<FileTableViewModel, Boolean>, TableCell<FileTableViewModel, Boolean>>() {
        public TableCell<FileTableViewModel, Boolean> call(TableColumn<FileTableViewModel, Boolean> p) {
          final TableCell<FileTableViewModel, Boolean> cell = new TableCell<FileTableViewModel, Boolean>() {
            @Override
            public void updateItem(final Boolean item, boolean empty) {
              if (item == null)
                return;
              super.updateItem(item, empty);
              if (!isEmpty()) {
                final FileTableViewModel fileTableViewModel = getTableView().getItems().get(getIndex());
                CheckBox checkBox = new CheckBox();
                checkBox.selectedProperty().bindBidirectional(fileTableViewModel.selectedProperty());
                // checkBox.setOnAction(event);
                setGraphic(checkBox);
              }
            }
          };
          cell.setAlignment(Pos.CENTER);
          return cell;
        }
      });
      // file name column
      TableColumn<FileTableViewModel, String> firstNameCol = new TableColumn<FileTableViewModel, String>();
      firstNameCol.setText("File name");
      firstNameCol.setCellValueFactory(new PropertyValueFactory<FileTableViewModel, String>("fileName"));
      firstNameCol.setPrefWidth(150);

      //  size column
      TableColumn<FileTableViewModel, String> sizeNameCol = new TableColumn<>("Size");
      sizeNameCol.setMinWidth(50);
      sizeNameCol.setMaxWidth(50);
      sizeNameCol.setCellValueFactory(new PropertyValueFactory<FileTableViewModel, String>("size"));

      //  Creation time column
      TableColumn<FileTableViewModel, LocalDateTime> creationTime = new TableColumn<>("Creation Time");
      creationTime.setCellValueFactory(new PropertyValueFactory("creationTime"));
      creationTime.setMinWidth(100);
      creationTime.setMaxWidth(100);
      creationTime.setCellFactory((TableColumn<FileTableViewModel, LocalDateTime> column) -> {
        return new TableCell<FileTableViewModel, LocalDateTime>() {
          @Override
          protected void updateItem(LocalDateTime item, boolean empty) {
            super.updateItem(item, empty);
            if (item == null || empty) {
              setText(null);
            } else {
              setText(formatter.format(item));
            }
          }
        };
      });

      // path column
      TableColumn<FileTableViewModel, String> pathCol = new TableColumn<FileTableViewModel, String>();
      pathCol.setText("Path");
      pathCol.setMinWidth(300);
      pathCol.setCellValueFactory(new PropertyValueFactory<FileTableViewModel, String>("pathWithoutName"));


      final TableView<FileTableViewModel> tableView = new TableView<>();

      tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

      tableView.setItems(fileList);
      tableView.getColumns().addAll(selectedCol, firstNameCol, sizeNameCol, creationTime, pathCol);

      ListBinding<Boolean> lb = new ListBinding<Boolean>() {
        {
          bind(tableView.getItems());
        }

        @Override
        protected ObservableList<Boolean> computeValue() {
          ObservableList<Boolean> list = FXCollections.observableArrayList();
          for (FileTableViewModel p : tableView.getItems()) {
            list.add(p.getSelected());
          }
          return list;
        }
      };

      lb.addListener(new ChangeListener<ObservableList<Boolean>>() {
        @Override
        public void changed(ObservableValue<? extends ObservableList<Boolean>> arg0, ObservableList<Boolean> arg1,
                            ObservableList<Boolean> l) {
          // Checking for an unselected employee in the table view.
          boolean unSelectedFlag = false;
          for (boolean b : l) {
            if (!b) {
              unSelectedFlag = true;
              break;
            }
          }
          /*
           * If at least one employee is not selected, then deselecting the check box in the table column header, else if all
           * employees are selected, then selecting the check box in the header.
           */
          if (unSelectedFlag) {
            getSelectAllCheckBox().setSelected(false);
          } else {
            getSelectAllCheckBox().setSelected(true);
          }

          // Checking for a selected employee in the table view.
          boolean selectedFlag = false;
          for (boolean b : l) {
            if (!b) {
              selectedFlag = true;
              break;
            }
          }

          /*
           * If at least one employee is selected, then enabling the "Export" button, else if none of the employees are selected,
           * then disabling the "Export" button.
           */
          if (selectedFlag) {
            enableExportButton();
          } else {
            disableExportButton();
          }
        }
      });

      tableView.getItems().addListener(new InvalidationListener() {
        @Override
        public void invalidated(Observable arg0) {
          System.out.println("invalidated");
        }
      });

      tableView.getItems().addListener(new ListChangeListener<FileTableViewModel>() {
        @Override
        public void onChanged(javafx.collections.ListChangeListener.Change<? extends FileTableViewModel> arg0) {
          System.out.println("changed");
        }
      });

      tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
        communicationService.fileViewSelectionChanged(newSelection);

      });

      this.tableView = tableView;

    }
    return tableView;
  }

  public Button getExportButton() {
    if (this.exportButton == null) {
      final Button exportButton = new Button("Export");
      exportButton.setOnAction(new EventHandler<ActionEvent>() {
        @Override
        public void handle(ActionEvent arg0) {
          System.out.print("employees Selected : [");
          for (FileTableViewModel fileTableViewModel : getFilesTableView().getItems()) {
            if (fileTableViewModel.getSelected()) {
              System.out.print(fileTableViewModel.getFileName() + ", ");
            }
          }
          System.out.print(" ]\n");
        }
      });
      exportButton.setDisable(true);
      this.exportButton = exportButton;
    }
    return this.exportButton;
  }

  /**
   * Enables the "Export" button.
   */
  public void enableExportButton() {
    getExportButton().setDisable(false);
  }

  /**
   * Disables the "Export" button.
   */
  public void disableExportButton() {
    getExportButton().setDisable(true);
  }


  public void clearList() {
    fileList.clear();
  }

  public void addFile(Path path, String pathWithoutName, String fileName, long size, LocalDateTime creationDate) {
    fileList.add(new FileTableViewModel(false, path, pathWithoutName, fileName, size, creationDate));
  }

  public ObservableList<FileTableViewModel> getFileList() {
    return fileList;
  }
}
