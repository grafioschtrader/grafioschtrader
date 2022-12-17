package grafioschtrader.repository;

import java.sql.SQLException;
import java.util.List;

import jakarta.mail.MessagingException;

import grafioschtrader.entities.User;

public interface UserJpaRepositoryCustom extends BaseRepositoryCustom<User> {

  List<User> connectUserWithUserAndLimitProposals();

  void sendSimpleMessage(String to, String subject, String text) throws MessagingException;

  Integer moveCreatedByUserToOtherUser(Integer fromIdUser, Integer toIdUser) throws SQLException;
}