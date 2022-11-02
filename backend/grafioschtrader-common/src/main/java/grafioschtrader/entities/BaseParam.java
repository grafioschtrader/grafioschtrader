package grafioschtrader.entities;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.MappedSuperclass;

@Embeddable
@MappedSuperclass
public abstract class BaseParam {
  // no need of declaring key
  // key column will be created by MapKeyColumn

  @Column(name = "param_value")
  protected String paramValue;

  public String getParamValue() {
    return paramValue;
  }

  public void setParamValue(String paramValue) {
    this.paramValue = paramValue;
  }
}
