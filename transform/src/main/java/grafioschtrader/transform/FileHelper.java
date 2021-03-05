package grafioschtrader.transform;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.collections.ObservableList;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

public final class FileHelper {

  private static final String PREFERENCE_LAST_PATH = "last.path";

  public static void chooseFileDir(Stage primaryStage, ImportSelectionModel importSelectionModel,
                                   FileListTableView fileListTableView) {
    if (importSelectionModel.importDirectory) {
      // Set title for DirectoryChooser


      final DirectoryChooser directoryChooser = new DirectoryChooser();
      directoryChooser.setTitle("Select Some Directories");

      // Set Initial Directory
      directoryChooser.setInitialDirectory(getPreferenceLastPath());

      File selectedDirectory = directoryChooser.showDialog(primaryStage);

      if(selectedDirectory != null) {
        fileListTableView.clearList();
        File directory = new File(selectedDirectory.getAbsolutePath());
        setPreferenceLastPath(directory.getAbsolutePath().toString());

        List<File> fileList = new ArrayList<>();

        Path startDir = Paths.get(directory.getAbsolutePath());
        try {
          Files.walkFileTree(startDir, EnumSet.noneOf(FileVisitOption.class), importSelectionModel.includeSubDir ? Integer.MAX_VALUE : 1,
                  new FindTextFilesVisitor(fileList, fileListTableView, importSelectionModel));
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    } else {
      // Files to choose on a single directory
      FileChooser fileChooser = new FileChooser();
      fileChooser.setInitialDirectory(getPreferenceLastPath());
      fileChooser.setTitle("Select one or more PDF file");
      FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("PDF Files", "*.pdf", "pdf");
      fileChooser.getExtensionFilters().add(extFilter);
      List<File> files = fileChooser.showOpenMultipleDialog(primaryStage);

      if(files != null) {
        fileListTableView.clearList();
        for (File file : files) {
          try {
            Path path = file.toPath();
            addFile(fileListTableView, path, Files.readAttributes(path, BasicFileAttributes.class));
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }
  }


  /**
   * FindTextFilesVisitor.
   */
  static class FindTextFilesVisitor extends SimpleFileVisitor<Path> {
    private final List<File> fileList;
    private final FileListTableView fileListTableView;
    private final ImportSelectionModel importSelectionModel;



    FindTextFilesVisitor(List<File> fileList, FileListTableView fileListTableView, ImportSelectionModel importSelectionModel) {
      this.fileList = fileList;
      this.fileListTableView = fileListTableView;
      this.importSelectionModel = importSelectionModel;
    }

    @Override
    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
      if (path.toString().toLowerCase().endsWith(".pdf")) {
        if(importSelectionModel.isPassedFileNameFilter(path.getFileName().toString())) {
          addFile(fileListTableView, path, attrs);
        }
      }
      return FileVisitResult.CONTINUE;
    }
  }

  private static void addFile(FileListTableView fileListTableView, Path file, BasicFileAttributes attrs) {
    fileListTableView.addFile(file.toAbsolutePath(),
            file.subpath(0, file.getNameCount() - 1).toString(),
            file.getFileName().toString().substring(0, file.getFileName().toString().lastIndexOf(".")),
            attrs.size(),
            LocalDateTime.ofInstant(attrs.creationTime().toInstant(), ZoneId.systemDefault()));
  }

  public static void readSaveTransformation(final boolean read, final Stage primaryStage, final ObservableList<TransformTableViewModel> transformTableViewModelList) {
    FileChooser fileChooser = getFileChoose("JSON files (*.json)", "*.json");

    File file = read ? fileChooser.showOpenDialog(primaryStage) : fileChooser.showSaveDialog(primaryStage);
    if (file != null) {
      setPreferenceLastPath(file.getParent());
      if (read) {
        readTransformTableViewModel(file, transformTableViewModelList);
      } else {
        writeTransformTableViewModel(file, transformTableViewModelList);
      }

    }
  }

  public static File getTransformTargetFile(final Stage primaryStage) {
    FileChooser fileChooser = getFileChoose("Text files (*.txt)", "*.txt");
    File file = fileChooser.showSaveDialog(primaryStage);
    if (file != null) {
      setPreferenceLastPath(file.getParent());
    }
    return file;
  }

  private static FileChooser getFileChoose(String description, String extensions) {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setInitialDirectory(getPreferenceLastPath());

    //Set extension filter
    FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter(description, extensions);
    fileChooser.getExtensionFilters().add(extFilter);
    return fileChooser;
  }


  private static void writeTransformTableViewModel(File jsonFile, ObservableList<TransformTableViewModel> transformTableViewModelList) {
    ObjectMapper mapper = new ObjectMapper();

    try (FileWriter fileWriter = new FileWriter(jsonFile)) {
      mapper.writeValue(fileWriter, transformTableViewModelList);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  private static void readTransformTableViewModel(File jsonFile, ObservableList<TransformTableViewModel> transformTableViewModelList) {
    ObjectMapper mapper = new ObjectMapper();

    try (FileReader fileReader = new FileReader(jsonFile)) {
      TransformTableViewModel[] ttvmList = mapper.readValue(fileReader, TransformTableViewModel[].class);
      transformTableViewModelList.addAll(ttvmList);

    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  private static File getPreferenceLastPath() {
    // Set Initial Directory
    Preferences prefs = Preferences.userNodeForPackage(GrafioschtraderTransformApp.class);
    String path = prefs.get(PREFERENCE_LAST_PATH, System.getProperty("user.home"));
    return new File(path);
  }

  private static void setPreferenceLastPath(String absolutePath) {
    Preferences prefs = Preferences.userNodeForPackage(GrafioschtraderTransformApp.class);
    prefs.put(PREFERENCE_LAST_PATH, absolutePath);
  }

}
