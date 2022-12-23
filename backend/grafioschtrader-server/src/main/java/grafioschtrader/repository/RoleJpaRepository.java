package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.Role;

public interface RoleJpaRepository extends JpaRepository<Role, Integer> {
  Role findByRolename(String rolename);
}
