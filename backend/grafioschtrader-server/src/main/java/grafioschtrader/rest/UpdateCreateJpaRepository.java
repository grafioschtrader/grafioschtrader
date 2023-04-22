package grafioschtrader.rest;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.repository.BaseRepositoryCustom;

/*
 * For generalization of a base REST UpdateCreater, the specific derived REST controller 
 * must provide certain functionality of the extended JpaRepository.
 */
public interface UpdateCreateJpaRepository<T> extends JpaRepository<T, Integer>, BaseRepositoryCustom<T> {

}
