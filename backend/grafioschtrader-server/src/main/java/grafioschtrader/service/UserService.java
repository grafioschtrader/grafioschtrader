package grafioschtrader.service;

import java.util.Optional;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import grafioschtrader.dto.ChangePasswordDTO;
import grafioschtrader.dto.UserDTO;
import grafioschtrader.entities.User;
import grafioschtrader.entities.projection.SuccessfullyChanged;
import grafioschtrader.entities.projection.UserOwnProjection;
import grafioschtrader.security.UserRightLimitCounter;

public interface UserService extends UserDetailsService {

  User updateButPassword(UserDTO params);

  Optional<User> findUser(Integer id);

  void checkUserLimits(User user);

  User createUser(UserDTO userDTO);

  User updateTimezoneOffset(User user, Integer timezoneOffset);

  User createUserForVerification(UserDTO userDTO, String hostName);

  SuccessfullyChanged changePassword(ChangePasswordDTO changePasswortDTO);

  User incrementRightsLimitCount(Integer userId, UserRightLimitCounter userRightLimitCounter);

  UserDetails loadUserByUserIdAndCheckUsername(Integer idUser, String username);

  SuccessfullyChanged updateNicknameLocal(UserOwnProjection userOwnProjection);
}
