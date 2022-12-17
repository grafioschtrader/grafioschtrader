package grafioschtrader.entities;

import java.io.Serializable;
import java.util.Map;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class AbstractMultilanguageString implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  public AbstractMultilanguageString() {
  }

  public AbstractMultilanguageString(String lang, String text) {
  }

  protected abstract Map<String, String> getMap();

  public void addText(String lang, String text) {
    getMap().put(lang, text);
  }
}
