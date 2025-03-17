package grafiosch.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.entities.Role;

public interface RoleJpaRepository extends JpaRepository<Role, Integer> {
  Role findByRolename(String rolename);
}
