package grafioschtrader.reports;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CorrelationReport {

  @Autowired
  JdbcTemplate jdbcTemplate;
  
  
  
  
}
