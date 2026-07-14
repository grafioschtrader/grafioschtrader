package grafiosch.rest;

import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface UpdateCreateDeleteWithUserIdJpaRepository<T> extends UpdateCreateJpaRepository<T> {
  int delEntityWithUserId(Integer id, Integer idUser);
}
