package grafioschtrader.common;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafiosch.exceptions.TaskInterruptException;

public class ThreadHelper {

  private static final Logger log = LoggerFactory.getLogger(ThreadHelper.class);

  /**
   * Executes the specified task using a custom ForkJoinPool.
   *
   * <p>This method creates a ForkJoinPool with a thread count determined by multiplying the number of
   * available processors by the provided coreMultiplier. It submits the task to the pool and waits for its
   * completion. To prevent a resource leak, the pool is shut down in a finally block after execution.</p>
   *
   * @param task the Runnable task to be executed
   * @param coreMultiplier the multiplier for the available processor count to determine the number of threads in the pool
   * @throws TaskInterruptException if the execution is interrupted
   */
  public static void executeForkJoinPool(final Runnable task, int coreMultiplier) {
      final int numberOfThreads = Runtime.getRuntime().availableProcessors() * coreMultiplier;
      final ForkJoinPool customThreadPool = new ForkJoinPool(numberOfThreads);
      try {
          customThreadPool.submit(task).get();
      } catch (ExecutionException e) {
          log.error("ForkJoinPool encountered an execution exception", e);
      } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();  // preserve interruption status
          throw new TaskInterruptException(ie);
      } finally {
          customThreadPool.close();
      }
  }

}
