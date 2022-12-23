package grafioschtrader.repository;

import java.sql.SQLException;
import java.util.List;

import grafioschtrader.entities.User;
import jakarta.mail.MessagingException;

public interface UserJpaRepositoryCustom extends BaseRepositoryCustom<User> {

  List<User> connectUserWithUserAndLimitProposals();

  void sendSimpleMessage(String to, String subject, String text) throws MessagingException;

  Integer moveCreatedByUserToOtherUser(Integer fromIdUser, Integer toIdUser) throws SQLException;
}