package grafioschtrader.repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.BaseConstants;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.GTNetSecurityImpHead;
import grafioschtrader.entities.GTNetSecurityImpPos;
import grafioschtrader.entities.Security;
import jakarta.transaction.Transactional;

/**
 * Implementation of custom repository operations for GTNetSecurityImpPos.
 * All operations verify tenant access through the parent header entity.
 */
public class GTNetSecurityImpPosJpaRepositoryImpl implements GTNetSecurityImpPosJpaRepositoryCustom {

  @Autowired
  private GTNetSecurityImpPosJpaRepository gtNetSecurityImpPosJpaRepository;

  @Autowired
  private GTNetSecurityImpHeadJpaRepository gtNetSecurityImpHeadJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Override
  public List<GTNetSecurityImpPos> findByIdGtNetSecurityImpHeadAndIdTenant(Integer idGtNetSecurityImpHead,
      Integer idTenant) {
    // Verify tenant has access to the header
    GTNetSecurityImpHead header = gtNetSecurityImpHeadJpaRepository
        .findByIdGtNetSecurityImpHeadAndIdTenant(idGtNetSecurityImpHead, idTenant);
    if (header == null) {
      return Collections.emptyList();
    }
    return gtNetSecurityImpPosJpaRepository.findByIdGtNetSecurityImpHead(idGtNetSecurityImpHead);
  }

  @Override
  @Transactional
  public GTNetSecurityImpPos saveWithTenantCheck(GTNetSecurityImpPos entity, Integer idTenant) {
    // Verify tenant has access to the header
    GTNetSecurityImpHead header = gtNetSecurityImpHeadJpaRepository
        .findByIdGtNetSecurityImpHeadAndIdTenant(entity.getIdGtNetSecurityImpHead(), idTenant);
    if (header == null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }

    // If updating existing, verify it belongs to this header
    if (entity.getId() != null) {
      Optional<GTNetSecurityImpPos> existing = gtNetSecurityImpPosJpaRepository.findById(entity.getId());
      if (existing.isEmpty()
          || !existing.get().getIdGtNetSecurityImpHead().equals(entity.getIdGtNetSecurityImpHead())) {
        throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
      }
    }

    // Check if a security with the same ISIN+currency already exists
    if (entity.getIsin() != null && entity.getCurrency() != null) {
      Security existingSecurity = securityJpaRepository.findByIsinAndCurrency(entity.getIsin(), entity.getCurrency());
      if (existingSecurity != null) {
        throw new DataViolationException("isin", "gt.security.isin.currency.exists",
            new Object[] { entity.getIsin(), entity.getCurrency(), existingSecurity.getName() });
      }
    }

    return gtNetSecurityImpPosJpaRepository.save(entity);
  }

  @Override
  @Transactional
  public int deleteWithTenantCheck(Integer id, Integer idTenant) {
    Optional<GTNetSecurityImpPos> posOpt = gtNetSecurityImpPosJpaRepository.findById(id);
    if (posOpt.isEmpty()) {
      return 0;
    }

    // Verify tenant has access through the header
    GTNetSecurityImpHead header = gtNetSecurityImpHeadJpaRepository
        .findByIdGtNetSecurityImpHeadAndIdTenant(posOpt.get().getIdGtNetSecurityImpHead(), idTenant);
    if (header == null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }

    gtNetSecurityImpPosJpaRepository.deleteById(id);
    return 1;
  }
}
