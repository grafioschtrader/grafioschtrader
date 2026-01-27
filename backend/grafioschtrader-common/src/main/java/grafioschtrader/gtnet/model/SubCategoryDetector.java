package grafioschtrader.gtnet.model;

import java.util.Map;
import java.util.Set;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;

/**
 * Detects the categorization scheme (regional vs sector) from subCategoryNLS values using fuzzy keyword matching. Also
 * provides similarity-based text comparison for matching subcategory values between systems.
 */
public class SubCategoryDetector {

  /** Similarity threshold for fuzzy text matching (85%) */
  public static final double SIMILARITY_THRESHOLD = 0.85;

  private static final JaroWinklerSimilarity SIMILARITY = new JaroWinklerSimilarity();

  // Regional keywords (multilingual: EN/DE)
  private static final Set<String> REGIONAL_KEYWORDS = Set.of(
      // English
      "world", "global", "emerging", "markets", "usa", "europe", "asia", "pacific",
      "americas", "africa", "latin", "north", "japan", "china", "uk", "germany",
      "switzerland", "international", "developed", "frontier",
      // German
      "welt", "schwellenländer", "schwellenlaender", "europa", "asien", "pazifik",
      "lateinamerika", "nordamerika", "afrika", "schweiz", "deutschland");

  // Sector keywords (multilingual: EN/DE)
  private static final Set<String> SECTOR_KEYWORDS = Set.of(
      // English
      "finance", "financial", "banking", "bank", "industry", "industrial",
      "technology", "tech", "healthcare", "health", "energy", "utilities",
      "consumer", "materials", "real estate", "communication", "telecom",
      "telecommunications", "insurance", "pharmaceutical", "biotech",
      // German
      "finanzen", "banken", "industrie", "technologie", "gesundheit", "energie",
      "versorger", "konsum", "immobilien", "kommunikation", "versicherung", "pharma");

  private SubCategoryDetector() {
  }

  /**
   * Detects the categorization scheme from a single subCategoryNLS map.
   *
   * @param subCategoryNLS map of language codes to subcategory text values
   * @return the detected scheme (REGIONAL, SECTOR, or UNKNOWN)
   */
  public static SubCategoryScheme detect(Map<String, String> subCategoryNLS) {
    if (subCategoryNLS == null || subCategoryNLS.isEmpty()) {
      return SubCategoryScheme.UNKNOWN;
    }

    int regionalScore = 0;
    int sectorScore = 0;

    for (String value : subCategoryNLS.values()) {
      if (value == null) {
        continue;
      }
      String lower = value.toLowerCase();

      for (String keyword : REGIONAL_KEYWORDS) {
        if (lower.contains(keyword)) {
          regionalScore++;
          break;
        }
      }

      for (String keyword : SECTOR_KEYWORDS) {
        if (lower.contains(keyword)) {
          sectorScore++;
          break;
        }
      }
    }

    if (regionalScore > sectorScore) {
      return SubCategoryScheme.REGIONAL;
    } else if (sectorScore > regionalScore) {
      return SubCategoryScheme.SECTOR;
    }
    return SubCategoryScheme.UNKNOWN;
  }

  /**
   * Compares two subcategory text values using Jaro-Winkler similarity. Returns true if similarity is >=
   * SIMILARITY_THRESHOLD (85%). Handles typos, plurals, and slight variations.
   *
   * @param text1 first text value
   * @param text2 second text value
   * @return true if texts are similar enough to be considered a match
   */
  public static boolean isSimilar(String text1, String text2) {
    if (text1 == null || text2 == null) {
      return false;
    }
    double score = SIMILARITY.apply(text1.toLowerCase(), text2.toLowerCase());
    return score >= SIMILARITY_THRESHOLD;
  }

  /**
   * Returns the Jaro-Winkler similarity score between two texts (0.0 to 1.0).
   *
   * @param text1 first text value
   * @param text2 second text value
   * @return similarity score between 0.0 and 1.0
   */
  public static double getSimilarity(String text1, String text2) {
    if (text1 == null || text2 == null) {
      return 0.0;
    }
    return SIMILARITY.apply(text1.toLowerCase(), text2.toLowerCase());
  }
}
