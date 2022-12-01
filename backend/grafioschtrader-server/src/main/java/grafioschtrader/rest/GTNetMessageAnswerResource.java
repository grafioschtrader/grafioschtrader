package grafioschtrader.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.repository.GTNetMessageAnswerJpaRepository;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.GTNET_MESSAGE_ANSWER_MAP)
@Tag(name = RequestMappings.GTNET_MESSAGE_ANSWER, description = "Controller for GTNet message answer")
public class GTNetMessageAnswerResource {

  @Autowired
  private GTNetMessageAnswerJpaRepository gtNetMessageAnswerJpaRepository;
  
}
