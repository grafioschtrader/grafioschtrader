package grafioschtrader.rest;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.repository.BaseRepositoryCustom;

public interface UpdateCreateJpaRepository<T> extends JpaRepository<T, Integer>, BaseRepositoryCustom<T> {

}
