package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import grafioschtrader.entities.Role;

@Repository
public interface RoleJpaRepository extends JpaRepository<Role, Integer> {
  Role findByRolename(String rolename);
}
