package grafioschtrader.entities;

import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import jakarta.persistence.Cacheable;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = MultilanguageString.TABNAME)
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class MultilanguageString extends AbstractMultilanguageString {

  public static final String TABNAME = "multilinguestring";
  public static final String MULTILINGUESTRINGS = "multilinguestrings";

  private static final long serialVersionUID = 1L;

  @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = MULTILINGUESTRINGS, joinColumns = @JoinColumn(name = "id_string"))
  @MapKeyColumn(name = "language", nullable = false, length = 2)
  @Column(name = "text", nullable = false, length = 64)
  private Map<String, String> map = new HashMap<>();

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
