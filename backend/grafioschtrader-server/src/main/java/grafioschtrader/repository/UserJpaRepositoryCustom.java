package grafioschtrader.repository;

import java.util.List;

import javax.mail.MessagingException;

import grafioschtrader.entities.User;

public interface UserJpaRepositoryCustom extends BaseRepositoryCustom<User> {
  
  List<User> connectUserWithUserAndLimitProposals();
  
  void sendSimpleMessage(String to, String subject, String text) throws MessagingException;
}
