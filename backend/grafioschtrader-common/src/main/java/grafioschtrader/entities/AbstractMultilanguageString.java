package grafioschtrader.entities;

import java.io.Serializable;
import java.util.Map;

import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

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
