package grafioschtrader.reportviews;

import java.util.Map;

public abstract class MapGroup<S, T> {

  protected Map<S, T> groupMap;

  protected abstract T createInstance(S key);

  protected MapGroup(Map<S, T> groupMap) {
    this.groupMap = groupMap;
  }

  public T getOrCreateGroup(S key) {
    return groupMap.computeIfAbsent(key, k -> createInstance(k));
  }

}
