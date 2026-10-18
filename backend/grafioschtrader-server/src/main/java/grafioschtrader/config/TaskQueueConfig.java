package grafioschtrader.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import grafiosch.task.TaskQueueDefinition;

/**
 * The queues of background tasks Grafioschtrader adds to the main queue of the library. Language model runs spend
 * their time waiting on the provider and take minutes, so they get a queue of their own with several workers instead
 * of holding up the price updates and the other tasks of the main queue.
 */
@Configuration
public class TaskQueueConfig {

  /** The queue of the tasks that call a language model. */
  public static final String AI_QUEUE = "AI";

  @Bean
  public TaskQueueDefinition aiTaskQueue(@Value("${gt.ai.task.parallel:3}") int parallel) {
    return new TaskQueueDefinition(AI_QUEUE, parallel, "TASK_QUEUE_AI");
  }
}
