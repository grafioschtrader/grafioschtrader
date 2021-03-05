package grafioschtrader.types;

import java.util.Locale;

public enum Language {

  ENGLISH("en", Locale.ENGLISH), FRENCH("fr", Locale.FRENCH), GERMAN("de", Locale.GERMAN),
  ITALIAN("it", Locale.ITALIAN);

  private String key;

  private Locale locale;

  private Language(String key, Locale locale) {
    this.key = key;
    this.locale = locale;
  }

  public String getKey() {
    return key;
  }

  public static Language getByCode(String code) {
    for (Language language : Language.values()) {
      if (language.getLocale().getLanguage().equalsIgnoreCase(code)
          || language.getLocale().getDisplayName(Locale.ENGLISH).equalsIgnoreCase(code)) {
        return language;
      }
    }
    return null;
  }

  public Locale getLocale() {
    return locale;
  }
}