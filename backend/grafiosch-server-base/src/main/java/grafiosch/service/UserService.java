package grafiosch.service;

import java.util.Optional;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import grafiosch.dto.ChangePasswordDTO;
import grafiosch.dto.UserDTO;
import grafiosch.entities.User;
import grafiosch.entities.projection.SuccessfullyChanged;
import grafiosch.entities.projection.UserOwnProjection;
import grafiosch.types.UserRightLimitCounter;

public interface UserService extends UserDetailsService {

  User updateButPassword(UserDTO params);

  Optional<User> findUser(Integer id);

  void checkUserLimits(User user);

  User createUser(UserDTO userDTO);

  User updateTimezoneOffset(User user, Integer timezoneOffset);

  User createUserForVerification(UserDTO userDTO, String hostName) throws Exception;

  SuccessfullyChanged changePassword(ChangePasswordDTO changePasswortDTO) throws Exception;

  boolean isPasswordAccepted(String password) throws Exception;

  User incrementRightsLimitCount(Integer userId, UserRightLimitCounter userRightLimitCounter);

  UserDetails loadUserByUserIdAndCheckUsername(Integer idUser, String username);

  SuccessfullyChanged updateNicknameLocal(UserOwnProjection userOwnProjection);
}
