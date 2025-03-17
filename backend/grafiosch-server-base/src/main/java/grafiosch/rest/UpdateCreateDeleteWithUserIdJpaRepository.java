package grafiosch.rest;

public interface UpdateCreateDeleteWithUserIdJpaRepository<T> extends UpdateCreateJpaRepository<T> {
  int delEntityWithUserId(Integer id, Integer idUser);
}
