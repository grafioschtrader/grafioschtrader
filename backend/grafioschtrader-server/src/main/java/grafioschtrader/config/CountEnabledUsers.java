package grafioschtrader.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import grafioschtrader.repository.UserJpaRepository;

@Component
public class CountEnabledUsers implements InfoContributor {

  @Value("${gt.allowed.users}")
  private int allowed;
  
  @Autowired
  private UserJpaRepository userJpaRepository;

  @Override
  public void contribute(Info.Builder builder) {
    Map<String, Integer> userDetails = new HashMap<>();
    userDetails.put("allowed", allowed);
    userDetails.put("active", userJpaRepository.countByEnabled(true));
    builder.withDetail("users", userDetails);
  }
}
