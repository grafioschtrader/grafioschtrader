package grafioschtrader.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The bounded worker pool a historical replay executes on, and the transaction boundary it commits each day with.
 *
 * <p>
 * A replay is long: it evaluates one day after another and books transactions as it goes, which is why it cannot be a
 * request thread. The pool is deliberately tiny and refuses work rather than queueing it. A replay competes for the
 * same database as every interactive request, so an unbounded queue would turn a burst of submissions into a server
 * that answers nothing else for an hour; being told that the server is busy is the better answer.
 * </p>
 */
@Configuration
public class AlgoReplayConfig {

  /** Bean name of the replay pool, so that nothing else can accidentally be scheduled onto it. */
  public static final String EXECUTOR = "algoReplayExecutor";

  /** How many replays may execute at once, across all users of the instance. */
  public static final int MAX_CONCURRENT_RUNS = 2;

  @Bean(name = EXECUTOR)
  ThreadPoolTaskExecutor algoReplayExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(MAX_CONCURRENT_RUNS);
    executor.setMaxPoolSize(MAX_CONCURRENT_RUNS);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("algo-replay-");
    // Refuse rather than queue: the caller turns the rejection into a "server busy" answer the user can act on.
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    // A replay that is still running at shutdown is marked interrupted at the next startup, so there is nothing to
    // wait for here.
    executor.setWaitForTasksToCompleteOnShutdown(false);
    return executor;
  }

  /**
   * A replay commits each day on its own, so that a cancellation or a failure keeps the days already evaluated instead
   * of rolling back an hour of work. It therefore needs an explicit transaction boundary rather than the declarative
   * one, which would wrap the whole run.
   */
  @Bean
  TransactionTemplate algoReplayTransactionTemplate(PlatformTransactionManager transactionManager) {
    return new TransactionTemplate(transactionManager);
  }
}
