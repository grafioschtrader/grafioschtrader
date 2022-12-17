package grafioschtrader.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.MappedSuperclass;

@Embeddable
@MappedSuperclass
public abstract class BaseParam {
  // no need of declaring key
  // key column will be created by MapKeyColumn

  public BaseParam() {
  }

  public BaseParam(String paramValue) {
    this.paramValue = paramValue;
  }

  
  @Column(name = "param_value")
  protected String paramValue;

  public String getParamValue() {
    return paramValue;
  }

  public void setParamValue(String paramValue) {
    this.paramValue = paramValue;
  }
}
