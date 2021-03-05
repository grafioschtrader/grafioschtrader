package grafioschtrader.common;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadHelper {

  private static final Logger log = LoggerFactory.getLogger(ThreadHelper.class);

  public static void executeForkJoinPool(final Runnable task, int threatMultiplier) {
    final int numberOfThreads = Runtime.getRuntime().availableProcessors() * threatMultiplier;
    final ForkJoinPool customThreadPool = new ForkJoinPool(numberOfThreads);
    try {
      customThreadPool.submit(task).get();
    } catch (InterruptedException | ExecutionException e) {
      log.error("ForkJoinPool", e);

    }
  }
}
