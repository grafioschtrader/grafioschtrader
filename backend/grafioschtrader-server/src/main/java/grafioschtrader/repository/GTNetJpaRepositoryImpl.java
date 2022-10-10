package grafioschtrader.repository;

import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.repository.GTNetJpaRepository.GTNetWithMessages;

public class GTNetJpaRepositoryImpl implements GTNetJpaRepositoryCustom {

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GTNetMessageJpaRepository gtNetMessageJpaRepository;

  @Override
  @Transactional
  public GTNetWithMessages getAllGTNetsWithMessages() {
    return new GTNetWithMessages(gtNetJpaRepository.findAll(),
        gtNetMessageJpaRepository.findAllByOrderByIdGtNetAscTimestampAsc()
        .collect(Collectors.groupingBy(GTNetMessage::getIdGtNet)));
  }
  

}
