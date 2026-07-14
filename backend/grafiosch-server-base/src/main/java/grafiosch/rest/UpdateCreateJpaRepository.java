package grafiosch.rest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import grafiosch.repository.BaseRepositoryCustom;

/*
 * For generalization of a base REST UpdateCreater, the specific derived REST controller
 * must provide certain functionality of the extended JpaRepository.
 */
@NoRepositoryBean
public interface UpdateCreateJpaRepository<T> extends JpaRepository<T, Integer>, BaseRepositoryCustom<T> {

}
