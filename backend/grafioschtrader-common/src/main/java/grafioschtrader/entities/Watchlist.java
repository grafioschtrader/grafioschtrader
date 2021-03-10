/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.entities;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.common.PropertyAlwaysUpdatable;

/**
 *
 * @author Hugo Graf
 */

@Entity
@Table(name = Watchlist.TABNAME)
public class Watchlist extends TenantBaseID implements Serializable {

  public static final String TABNAME = "watchlist";
  public static final String TABNAME_SEC_CUR = "watchlist_sec_cur";

  private static final long serialVersionUID = 1L;
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_watchlist")
  private Integer idWatchlist;

  @Basic(optional = false)
  @NotNull
  @Size(min = 1, max = 25)
  @PropertyAlwaysUpdatable
  private String name;

  @JsonIgnore
  @JoinTable(name = TABNAME_SEC_CUR, joinColumns = {
      @JoinColumn(name = "id_watchlist", referencedColumnName = "id_watchlist") }, inverseJoinColumns = {
          @JoinColumn(name = "id_securitycurrency", referencedColumnName = "id_securitycurrency") })
  @ManyToMany(fetch = FetchType.LAZY)
  private List<Securitycurrency<?>> securitycurrencyList;

  @Column(name = "id_tenant")
  private Integer idTenant;

  @Column(name = "last_timestamp")
  protected Date lastTimestamp;

  public Watchlist() {
  }

  public Watchlist(Integer idTenant, String name) {
    this.idTenant = idTenant;
    this.name = name;
  }

  @JsonIgnore
  @Override
  public Integer getId() {
    return idWatchlist;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public Integer getIdWatchlist() {
    return idWatchlist;
  }

  public void setIdWatchlist(Integer idWatchlist) {
    this.idWatchlist = idWatchlist;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<Securitycurrency<?>> getSecuritycurrencyList() {
    return securitycurrencyList;
  }

  public void setSecuritycurrencyList(List<Securitycurrency<?>> securitycurrencyList) {
    this.securitycurrencyList = securitycurrencyList;
  }

  @SuppressWarnings("unchecked")
  public <T> List<T> getSecuritycurrencyListByType(Class<T> type) {
    List<T> resultSecuritycurrency = new ArrayList<>();
    for (Securitycurrency<?> securitycurrency : securitycurrencyList) {
      if (type.isInstance(securitycurrency)) {
        resultSecuritycurrency.add((T) securitycurrency);
      }
    }
    return resultSecuritycurrency;
  }

  public Date getLastTimestamp() {
    return lastTimestamp;
  }

  public void setLastTimestamp(Date lastTimestamp) {
    this.lastTimestamp = lastTimestamp;
  }

  @JsonIgnore
  public int getWatchlistLength() {
    return securitycurrencyList.size();
  }

  @Override
  public String toString() {
    return "entities.Watchlist[ idWatchlist=" + idWatchlist + " ]";
  }

}
