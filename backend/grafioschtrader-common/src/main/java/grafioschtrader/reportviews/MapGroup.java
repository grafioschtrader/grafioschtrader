package grafioschtrader.reportviews;

import java.util.Map;

public abstract class MapGroup<S, T> {

  protected Map<S, T> groupMap;

  protected abstract T createInstance(S key);

  public MapGroup(Map<S, T> groupMap) {
    this.groupMap = groupMap;
  }

  public T getOrCreateGroup(S key) {

    return groupMap.computeIfAbsent(key, k -> createInstance(k));

    /*
     * T groupPosition = groupMap.get(key); if (groupPosition == null) {
     * groupPosition = createInstance(key); groupMap.put(key, groupPosition); }
     * 
     * return groupPosition;
     */
  }

}
