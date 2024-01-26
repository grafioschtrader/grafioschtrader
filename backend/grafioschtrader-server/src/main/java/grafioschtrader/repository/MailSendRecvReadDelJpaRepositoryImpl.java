package grafioschtrader.repository;

import java.util.function.BiConsumer;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.MailSendRecvReadDel;
import grafioschtrader.entities.MailSendRecvReadDel.MailSendRecvReadDelKey;

public class MailSendRecvReadDelJpaRepositoryImpl implements MailSendRecvReadDelJpaRepositoryCustom {

  @Autowired
  private MailSendRecvReadDelJpaRepository mailSendRecvReadDelJpaRepository;

  @Override
  public void markForRead(Integer idMailSendRecv, Integer idUser) {
    markForReadOrDelete(idMailSendRecv, idUser, MailSendRecvReadDel::setHasBeenRead);
  }

  @Override
  public void markRoleSingleForDelete(Integer idMailSendRecv, Integer idUser) {
    markForReadOrDelete(idMailSendRecv, idUser, MailSendRecvReadDel::setMarkHideDel);
  }

  private void markForReadOrDelete(Integer idMailSendRecv, Integer idUser,
      BiConsumer<MailSendRecvReadDel, Boolean> markSetter) {
    MailSendRecvReadDelKey msrrdKey = new MailSendRecvReadDelKey(idMailSendRecv, idUser);
    MailSendRecvReadDel msrrd = mailSendRecvReadDelJpaRepository.findById(msrrdKey)
        .orElseGet(() -> new MailSendRecvReadDel(msrrdKey));
    markSetter.accept(msrrd, true);
    mailSendRecvReadDelJpaRepository.save(msrrd);
  }

}
