package grafiosch.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.config.ExposedResourceBundleMessageSource;
import grafiosch.nls.NlsPayloadBuilder;

/**
 * Builds and caches the JSON document of translated texts that the client loads before it renders anything.
 *
 * <p>
 * The document is a pure function of the merged message bundles, so it is memoized per locale together with its
 * {@code ETag}. Without the memo every request would re-map and re-hash roughly two thousand entries.
 * </p>
 */
@Service
public class NlsPayloadService {

  private final Map<Locale, NlsPayload> payloadByLocale = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private MessageSource messages;

  /**
   * The rendered texts for one locale together with a validator for conditional requests.
   *
   * @param json the JSON document delivered to the client
   * @param etag a strong validator derived from {@code json}
   */
  public record NlsPayload(String json, String etag) {
  }

  /**
   * Returns the texts for a language, building them on first use.
   *
   * @param language a language tag such as {@code en} or {@code de}
   * @return the memoized payload for that language
   */
  public NlsPayload getPayload(String language) {
    Locale locale = Locale.forLanguageTag(language);
    if (!((ExposedResourceBundleMessageSource) messages).isCachingForever()) {
      // A finite cache interval means texts are expected to change while the server runs, which is a development
      // setting. Memoizing here would defeat it: the message source would re-read the edited file and this service
      // would keep answering from its own copy.
      return buildPayload(locale);
    }
    return payloadByLocale.computeIfAbsent(locale, this::buildPayload);
  }

  /**
   * Discards the memoized payloads, so the next request rebuilds them. Used together with a message source cache clear
   * when texts are edited while the server keeps running.
   */
  public void evict() {
    payloadByLocale.clear();
  }

  private NlsPayload buildPayload(Locale locale) {
    Properties properties = ((ExposedResourceBundleMessageSource) messages).getMessages(locale);
    Map<String, String> rawEntries = new HashMap<>();
    for (Entry<Object, Object> entry : properties.entrySet()) {
      rawEntries.put(entry.getKey().toString(), entry.getValue() == null ? null : entry.getValue().toString());
    }
    // A SortedMap, serialized by Jackson: the order has to be deterministic because it decides the ETag and because
    // the migration diffs this document against a baseline.
    SortedMap<String, Object> payload = NlsPayloadBuilder.build(rawEntries);
    try {
      String json = objectMapper.writeValueAsString(payload);
      return new NlsPayload(json, DigestUtils.md5Hex(json));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize the NLS payload for locale " + locale, e);
    }
  }
}
