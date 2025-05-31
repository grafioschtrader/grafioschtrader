package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.BaseConstants;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.ProposeUserTask;
import grafiosch.entities.Role;
import grafiosch.entities.User;
import grafiosch.usertask.UserTaskType;

public class UserJpaRepositoryImpl extends BaseRepositoryImpl<User>
    implements UserJpaRepositoryCustom, InfoContributor {

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private RoleJpaRepository roleJpaRepository;

  @Autowired
  private ProposeUserTaskJpaRepository proposeUserTaskJpaRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Value("${gt.allowed.users}")
  private int allowed;

  @Override
  public void contribute(Info.Builder builder) {
    Map<String, Integer> userDetails = new HashMap<>();
    userDetails.put("allowed", allowed);
    userDetails.put("active", userJpaRepository.countByEnabled(true));
    builder.withDetail("users", userDetails);
  }

  @Override
  public User saveOnlyAttributes(User user, User existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    Map<String, Role> roleMap = roleJpaRepository.findAll().stream()
        .collect(Collectors.toMap(Role::getRolename, Function.identity()));
    existingEntity.setRoleMap(roleMap);
    if (!existingEntity.getMostPrivilegedRole().equals(user.getMostPrivilegedRole())) {
      existingEntity.setLastRoleModifiedTime(new Date());
    }
    return RepositoryHelper.saveOnlyAttributes(userJpaRepository, user, existingEntity, updatePropertyLevelClasses);
  }

  @Override
  public List<User> connectUserWithUserAndLimitProposals() {
    List<User> users = userJpaRepository.findAllByOrderByIdUserAsc();
    List<ProposeUserTask> proposeUserTasks = proposeUserTaskJpaRepository.findAll();
    Comparator<User> byIdUser = (User u1, User u2) -> u1.getIdUser().compareTo(u2.getIdUser());
    User user = new User();
    proposeUserTasks.forEach(proposeUT -> {
      user.setIdUser(proposeUT.getIdTargetUser());
      int index = Collections.binarySearch(users, user, byIdUser);
      if (index >= 0) {
        if (proposeUT.getUserTaskType() == UserTaskType.RELEASE_LOGOUT) {
          users.get(index).userChangePropose = proposeUT;
        } else {
          users.get(index).addUserChangeLimitPropose(proposeUT);
        }
      }
    });
    return users;
  }

  @Override
  public Integer moveCreatedByUserToOtherUser(Integer fromIdUser, Integer toIdUser) throws SQLException {
    String url = jdbcTemplate.getDataSource().getConnection().getMetaData().getURL();
    String databaseName = StringUtils.substringAfterLast(url, "/");
    return userJpaRepository.moveCreatedByUserToOtherUser(fromIdUser, toIdUser, databaseName);
  }

  @Override
  public List<ValueKeyHtmlSelectOptions> getIdUserAndNicknameExcludeMe() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<ValueKeyHtmlSelectOptions> vkhsoList = new ArrayList<>();
    if (user.getMostPrivilegedRole() == Role.ROLE_ADMIN) {
      userJpaRepository.getIdUserAndNicknameExcludeUser(user.getIdUser()).forEach(rs -> vkhsoList.add(
          new ValueKeyHtmlSelectOptions(String.valueOf(rs.getIdUser()), rs.getIdUser() + " - " + rs.getNickname())));
    } else {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    return vkhsoList;
  }

}
