package grafiosch.entities;

import java.io.Serializable;
import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Schema(description = "The user can use this to switch a global user-defined field on or off.")
@Entity
@Table(name = UDFSpecialTypeDisableUser.TABNAME)
public class UDFSpecialTypeDisableUser {

  public static final String TABNAME = "udf_special_type_disable_user";

  @EmbeddedId
  private UDFSpecialTypeDisableUserId id;

  public UDFSpecialTypeDisableUser() {
    super();
  }

  public UDFSpecialTypeDisableUser(UDFSpecialTypeDisableUserId id) {
    super();
    this.id = id;
  }

  public UDFSpecialTypeDisableUserId getId() {
    return id;
  }

  @Embeddable
  public static class UDFSpecialTypeDisableUserId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "The ID of the user who switches a user-defined field on or off.")
    @Column(name = "id_user")
    @NotNull
    private Integer idUser;

    @Schema(description = "Which globally defined field is affected by the activation or deactivation.")
    @Column(name = "udf_special_type")
    @NotNull
    private Byte udfSpecialType;

    public UDFSpecialTypeDisableUserId() {
      super();
    }

    public UDFSpecialTypeDisableUserId(@NotNull Integer idUser, @NotNull Byte udfSpecialType) {
      this.idUser = idUser;
      this.udfSpecialType = udfSpecialType;
    }

    public Integer getIdUser() {
      return idUser;
    }

    public Byte getUdfSpecialType() {
      return udfSpecialType;
    }

    @Override
    public int hashCode() {
      return Objects.hash(idUser, udfSpecialType);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if ((obj == null) || (getClass() != obj.getClass())) {
        return false;
      }
      UDFSpecialTypeDisableUserId other = (UDFSpecialTypeDisableUserId) obj;
      return Objects.equals(idUser, other.idUser) && Objects.equals(udfSpecialType, other.udfSpecialType);
    }

  }

}
