package grafioschtrader.repository;

import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;

public interface GTNetJpaRepository extends JpaRepository<GTNet, Integer>, GTNetJpaRepositoryCustom {
  List<GTNet> findByLastpriceConsumerUsageAndLastpriceSupplierCapability(byte lastpriceConsumerUsage,
      byte lastpriceSupplierCapability);

  
  public static class GTNetWithMessages {
    public List<GTNet> gtNetList;
    public Map<Integer, List<GTNetMessage>> gtNetMessageMap;
    
    public GTNetWithMessages(List<GTNet> gtNetList, Map<Integer, List<GTNetMessage>> gtNetMessageMap) {
      this.gtNetList = gtNetList;
      this.gtNetMessageMap = gtNetMessageMap;
    }
    
  }
}
