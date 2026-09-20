package grafioschtrader.service;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.beans.BeanUtils;

import grafioschtrader.entities.*;

/**
 * Frozen parent-relative simulation weights. Applying them creates detached views, never edits the shared hierarchy.
 */
public record AlgoReplayAllocation(Integer topId, Float topPercentage, Map<Integer, Float> originalClasses,
    Map<Integer, Float> originalMembers, Map<Integer, Float> classes, Map<Integer, Float> members) {

  public AlgoReplayAllocation {
    originalClasses = Map.copyOf(originalClasses);
    originalMembers = Map.copyOf(originalMembers);
    classes = Map.copyOf(classes);
    members = Map.copyOf(members);
  }

  /** Removes excluded members, redistributing within each surviving class before normalizing class weights. */
  public static AlgoReplayAllocation capture(AlgoTop top, List<AlgoAssetclass> buckets,
      Function<AlgoAssetclass, List<AlgoSecurity>> children, Predicate<Security> excluded) {
    Map<Integer, Float> originalClasses = new LinkedHashMap<>(), originalMembers = new LinkedHashMap<>();
    Map<Integer, Float> classes = new LinkedHashMap<>(), members = new LinkedHashMap<>();
    boolean removed = false;
    boolean removedClass = false;
    boolean hasPermittedAllocation = false;
    for (AlgoAssetclass bucket : buckets) {
      List<AlgoSecurity> assigned = children.apply(bucket);
      originalClasses.put(bucket.getId(), weight(bucket.getPercentage()));
      assigned.forEach(m -> originalMembers.put(m.getId(), weight(m.getPercentage())));
      List<AlgoSecurity> permitted = assigned.stream()
          .filter(m -> m.getSecurity() != null && !excluded.test(m.getSecurity())).toList();
      boolean changed = permitted.size() != assigned.size();
      removed |= changed;
      double sum = permitted.stream().mapToDouble(m -> weight(m.getPercentage())).sum();
      hasPermittedAllocation |= sum > 0 && weight(bucket.getPercentage()) > 0;
      if (changed && sum == 0) {
        removedClass = true;
        continue;
      }
      classes.put(bucket.getId(), weight(bucket.getPercentage()));
      for (AlgoSecurity member : permitted)
        members.put(member.getId(),
            changed ? (float) (weight(member.getPercentage()) * 100.0 / sum) : weight(member.getPercentage()));
    }
    double sum = classes.values().stream().mapToDouble(Float::doubleValue).sum();
    if (removed && !hasPermittedAllocation && weight(top.getPercentage()) > 0)
      throw new IllegalArgumentException("REPLAY_NO_PERMITTED_ALLOCATION");
    if (removedClass && sum > 0)
      classes.replaceAll((_, weight) -> (float) (weight * 100.0 / sum));
    return new AlgoReplayAllocation(top.getId(), top.getPercentage(), originalClasses, originalMembers, classes,
        members);
  }

  private static float weight(Float weight) {
    if (weight == null || !Float.isFinite(weight) || weight < 0 || weight > 100)
      throw new IllegalArgumentException("MEAN_REVERSION_ALLOCATION_REQUIRED");
    return weight;
  }

  public AlgoTop top(AlgoTop original) {
    AlgoTop copy = new AlgoTop();
    BeanUtils.copyProperties(original, copy);
    copy.setPercentage(topPercentage);
    return copy;
  }

  public List<AlgoAssetclass> buckets(List<AlgoAssetclass> originals) {
    return originals.stream().filter(b -> classes.containsKey(b.getId())).map(b -> {
      AlgoAssetclass copy = new AlgoAssetclass();
      BeanUtils.copyProperties(b, copy, "addedPercentage", "algoSecurityList");
      copy.setPercentage(classes.get(b.getId()));
      return copy;
    }).toList();
  }

  public List<AlgoSecurity> securities(List<AlgoSecurity> originals) {
    return originals.stream().filter(m -> members.containsKey(m.getId())).map(m -> {
      AlgoSecurity copy = new AlgoSecurity();
      BeanUtils.copyProperties(m, copy);
      copy.setPercentage(members.get(m.getId()));
      return copy;
    }).toList();
  }
}
