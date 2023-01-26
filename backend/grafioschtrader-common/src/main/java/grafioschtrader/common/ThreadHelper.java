package grafioschtrader.common;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafioschtrader.exceptions.TaskInterruptException;

public class ThreadHelper {

  private static final Logger log = LoggerFactory.getLogger(ThreadHelper.class);

  public static void executeForkJoinPool(final Runnable task, int coreMultiplier) {
    final int numberOfThreads = Runtime.getRuntime().availableProcessors() * coreMultiplier;
    final ForkJoinPool customThreadPool = new ForkJoinPool(numberOfThreads);
    try {
      customThreadPool.submit(task).get();
    } catch (ExecutionException e) {
      log.error("ForkJoinPool", e);
    } catch (InterruptedException ie) {
      throw new TaskInterruptException(ie);
    }
  }
}
