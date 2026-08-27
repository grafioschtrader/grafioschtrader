package grafiosch.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class BackgroundWorkerConditionTest {

  @Test
  void pollingCanBeDisabledButRemainsEnabledByDefault() {
    ConditionalOnProperty condition = BackgroundWorker.class.getAnnotation(ConditionalOnProperty.class);

    assertThat(condition).isNotNull();
    assertThat(condition.name()).containsExactly("g.background.worker.enabled");
    assertThat(condition.havingValue()).isEqualTo("true");
    assertThat(condition.matchIfMissing()).isTrue();
  }
}
