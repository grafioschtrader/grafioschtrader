package grafioschtrader.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Contains a stock exchange with mic, it may be used for the stochexchange creation")
@Entity
@Table(name = StockexchangeMic.TABNAME)
public class StockexchangeMic {
  public static final String TABNAME = "stockexchange_mic";
  
  @Id
  @Column(name = "mic")
  private String mic;
  
  @Column(name = "name")
  private String name;
  
  @Column(name = "country_code")
  private String countryCode;
  
  @Column(name = "city")
  private String city;
  
  @Column(name = "website")
  private String website;
  
  @Column(name = "time_zone")
  private String timeZone;

  public String getMic() {
    return mic;
  }

  public void setMic(String mic) {
    this.mic = mic;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCountryCode() {
    return countryCode;
  }

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getWebsite() {
    return website;
  }

  public void setWebsite(String website) {
    this.website = website;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(String timeZone) {
    this.timeZone = timeZone;
  }
  
  
  
}
