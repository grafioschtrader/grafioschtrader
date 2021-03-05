package grafioschtrader.entities;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.Cacheable;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = MultilanguageString.TABNAME)
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class MultilanguageString extends AbstractMultilanguageString {

  public static final String TABNAME = "multilinguestring";

  private static final long serialVersionUID = 1L;

  @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "multilinguestrings", joinColumns = @JoinColumn(name = "id_string"))
  @MapKeyColumn(name = "language", nullable = false, length = 2)
  @Column(name = "text", nullable = false, length = 64)
  private Map<String, String> map = new HashMap<String, String>();

  public MultilanguageString() {
    super();
  }

  public MultilanguageString(final String language, final String text) {
    addText(language, text);
  }

  public String getText(final String language) {
    return map.get(language);
  }

  @Override
  public Map<String, String> getMap() {
    return map;
  }

}
