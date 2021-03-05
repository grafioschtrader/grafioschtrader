package grafioschtrader.transform;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;

import java.io.Serializable;

public class TransformTableViewModel {

  private SimpleStringProperty sourceText;
  private SimpleStringProperty targetText;
  private SimpleBooleanProperty removeText;

  public TransformTableViewModel() {
    this(null, null, false);
  }

  public TransformTableViewModel(String sourceText, String targetText, boolean removeText) {
    this.sourceText = new SimpleStringProperty(sourceText);
    this.targetText = new SimpleStringProperty(targetText);
    this.removeText = new SimpleBooleanProperty(removeText);
  }

  public String getSourceText() {
    return sourceText.get();
  }

  public void setSourceText(String sourceText) {
    this.sourceText.set(sourceText);
  }

  public String getTargetText() {
    return targetText.get();
  }

  public void setTargetText(String targetText) {
    this.targetText.set(targetText);
  }

  @JsonIgnore
  public ObservableBooleanValue isRemoveTextObservable() {
    return removeText;
  }

  public boolean isRemoveText() {
    return removeText.get();
  }

  public void setRemoveText(boolean removeText) {
    this.removeText.set(removeText);
  }
}