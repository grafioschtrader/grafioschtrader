/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.entities;

import static jakarta.persistence.InheritanceType.JOINED;

import java.io.Serializable;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import grafiosch.BaseConstants;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.entities.TenantBaseID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * It is the basis of a security or a cash account.
 */
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

  @Schema(description = "Name of the custody account or a cash account.")
  @NotBlank
  @Size(min = 1, max = 25)
  @Basic(optional = false)
  @Column(name = "name")
  @PropertyAlwaysUpdatable
  private String name;

  @Schema(description = "User note for this account.")
  @Column(name = "note")
  @Size(max = BaseConstants.FID_MAX_LETTERS)
  @PropertyAlwaysUpdatable
  private String note;

  @JsonProperty(access = Access.WRITE_ONLY)
  @JoinColumn(name = "id_portfolio", referencedColumnName = "id_portfolio")
  @ManyToOne(optional = false)
  private Portfolio portfolio;

  @JsonIgnore
  @Column(name = "id_tenant")
  private Integer idTenant;

  // TODO integrate in UI and somewhere else
  @Schema(description = "Up to what date can entries be made to this account?")
  @Column(name = "active_to_date")
  private LocalDate activeToDate;

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

  public LocalDate getActiveToDate() {
    return activeToDate;
  }

  public void setActiveToDate(LocalDate activeToDate) {
    this.activeToDate = activeToDate;
  }

  @Override
  public String toString() {
    return "grafioschtrader.entities.Securitycashaccount[ idSecuritycashAccount=" + idSecuritycashAccount + " ]";
  }

}
