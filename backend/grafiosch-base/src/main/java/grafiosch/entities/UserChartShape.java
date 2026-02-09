package grafiosch.entities;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

/**
 * Persists user-drawn chart shapes (lines, rectangles, circles, paths) for a specific security or currency pair.
 * Shapes are stored as JSON matching the Plotly.js shape format and are scoped per user and instrument.
 */
@Schema(description = """
    Stores chart drawing shapes per user and security/currency pair. The shape data is stored as a JSON array
    of Plotly.js shape objects, enabling persistence across sessions and devices.""")
@Entity
@Table(name = UserChartShape.TABNAME)
public class UserChartShape {

  public static final String TABNAME = "user_chart_shape";

  @EmbeddedId
  private UserChartShapeKey userChartShapeKey;

  @Schema(description = "JSON array of Plotly.js shape objects (lines, rectangles, circles, paths) drawn on the chart.")
  @Type(JsonType.class)
  @Column(name = "shape_data", columnDefinition = "json")
  private List<Map<String, Object>> shapeData;

  public UserChartShape() {
  }

  public UserChartShape(UserChartShapeKey userChartShapeKey, List<Map<String, Object>> shapeData) {
    this.userChartShapeKey = userChartShapeKey;
    this.shapeData = shapeData;
  }

  public UserChartShapeKey getUserChartShapeKey() {
    return userChartShapeKey;
  }

  public void setUserChartShapeKey(UserChartShapeKey userChartShapeKey) {
    this.userChartShapeKey = userChartShapeKey;
  }

  public List<Map<String, Object>> getShapeData() {
    return shapeData;
  }

  public void setShapeData(List<Map<String, Object>> shapeData) {
    this.shapeData = shapeData;
  }

  @Embeddable
  public static class UserChartShapeKey extends UserBaseID implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID of the user who drew the shapes.")
    @Column(name = "id_user")
    @NotNull
    private Integer idUser;

    @Schema(description = "ID of the security or currency pair the shapes belong to.")
    @Column(name = "id_securitycurrency")
    @NotNull
    private Integer idSecuritycurrency;

    public UserChartShapeKey() {
    }

    public UserChartShapeKey(Integer idUser, Integer idSecuritycurrency) {
      this.idUser = idUser;
      this.idSecuritycurrency = idSecuritycurrency;
    }

    public Integer getIdSecuritycurrency() {
      return idSecuritycurrency;
    }

    public void setIdSecuritycurrency(Integer idSecuritycurrency) {
      this.idSecuritycurrency = idSecuritycurrency;
    }

    @Override
    public Integer getIdUser() {
      return idUser;
    }

    @Override
    public void setIdUser(Integer idUser) {
      this.idUser = idUser;
    }

    @Override
    @JsonIgnore
    public Integer getId() {
      throw new UnsupportedOperationException("We have a composite key");
    }

    @Override
    public int hashCode() {
      return Objects.hash(idUser, idSecuritycurrency);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if ((obj == null) || (getClass() != obj.getClass())) {
        return false;
      }
      UserChartShapeKey other = (UserChartShapeKey) obj;
      return Objects.equals(idUser, other.idUser) && Objects.equals(idSecuritycurrency, other.idSecuritycurrency);
    }
  }
}
