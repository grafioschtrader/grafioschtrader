package grafioschtrader.entities;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

/**
 * The exchange has a unique MIC (Market Identifier Code). Some data providers may have other exchange codes, such as
 * Yahoo Finance. These can therefore be mapped here. Is only used internally and cannot yet be edited via a user
 * interface.
 */
@Entity
@Table(name = MicProviderMap.TABNAME)
public class MicProviderMap {

  public static final String TABNAME = "mic_provider_map";

  @EmbeddedId
  private IdProviderMic idProviderMic;

  /**
   * The code which the provider uses for this exchange.
   */
  @Column(name = "code_provider")
  @NotNull
  private String codeProvider;

  @Column(name = "symbol_suffix")
  private String symbolSuffix;

  public MicProviderMap() {
  }

  public IdProviderMic getIdProviderMic() {
    return idProviderMic;
  }

  public String getCodeProvider() {
    return codeProvider;
  }

  public String getSymbolSuffix() {
    return symbolSuffix;
  }

  @Embeddable
  public static class IdProviderMic {

    public IdProviderMic(@NotNull String idProvider, @NotNull String mic) {
      this.idProvider = idProvider;
      this.mic = mic;
    }

    /**
     * ID of the data provider, for example “yahoo”.
     */
    @Column(name = "id_provider")
    @NotNull
    private String idProvider;

    /**
     * The official MIC of the exchange, for example “XETR”.
     */
    @Column(name = "mic")
    @NotNull
    private String mic;

    public String getIdProvider() {
      return idProvider;
    }

    public String getMic() {
      return mic;
    }

    @Override
    public int hashCode() {
      return Objects.hash(idProvider, mic);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if ((obj == null) || (getClass() != obj.getClass())) {
        return false;
      }
      IdProviderMic other = (IdProviderMic) obj;
      return Objects.equals(idProvider, other.idProvider) && Objects.equals(mic, other.mic);
    }

  }
}
