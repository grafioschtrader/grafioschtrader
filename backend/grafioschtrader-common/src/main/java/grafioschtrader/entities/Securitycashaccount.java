/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.entities;

import static javax.persistence.InheritanceType.JOINED;

import java.io.Serializable;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.PropertyAlwaysUpdatable;

@Entity
@Table(name = Securitycashaccount.TABNAME)
@Inheritance(strategy = JOINED)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.STRING)
public abstract class Securitycashaccount extends TenantBaseID implements Serializable {

  public static final String TABNAME = "securitycashaccount";
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_securitycash_account")
  protected Integer idSecuritycashAccount;

  @NotBlank
  @Size(min = 1, max = 25)
  @Basic(optional = false)
  @Column(name = "name")
  @PropertyAlwaysUpdatable
  private String name;

  @Column(name = "note")
  @Size(max = GlobalConstants.NOTE_SIZE)
  @PropertyAlwaysUpdatable
  private String note;

  @JsonProperty(access = Access.WRITE_ONLY)
  @JoinColumn(name = "id_portfolio", referencedColumnName = "id_portfolio")
  @ManyToOne(optional = false)
  private Portfolio portfolio;

  @JsonIgnore
  @Column(name = "id_tenant")
  private Integer idTenant;

  public Securitycashaccount() {
  }

  public Securitycashaccount(String name, Portfolio portfolio) {
    this.name = name;
    this.portfolio = portfolio;
  }

  @JsonIgnore
  @Override
  public Integer getId() {
    return idSecuritycashAccount;
  }

  public Integer getIdSecuritycashAccount() {
    return idSecuritycashAccount;
  }

  public void setIdSecuritycashAccount(Integer idSecuritycashAccount) {
    this.idSecuritycashAccount = idSecuritycashAccount;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public Portfolio getPortfolio() {
    return portfolio;
  }

  public void setPortfolio(Portfolio portfolio) {
    this.portfolio = portfolio;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  @Override
  public String toString() {
    return "grafioschtrader.entities.Securitycashaccount[ idSecuritycashAccount=" + idSecuritycashAccount + " ]";
  }

}
