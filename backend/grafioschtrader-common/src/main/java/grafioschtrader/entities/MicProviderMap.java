package grafioschtrader.entities;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = MicProviderMap.TABNAME)
public class MicProviderMap {

  public static final String TABNAME = "mic_provider_map";

  @EmbeddedId
  private IdProviderMic idProviderMic;

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

    @Column(name = "id_provider")
    @NotNull
    private String idProvider;

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
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      IdProviderMic other = (IdProviderMic) obj;
      return Objects.equals(idProvider, other.idProvider) && Objects.equals(mic, other.mic);
    }

  }
}
