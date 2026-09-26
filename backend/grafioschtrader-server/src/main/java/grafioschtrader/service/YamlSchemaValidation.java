package grafioschtrader.service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.serialization.JsonMapperFactory;

/** Classpath-only schema validation, usable without an application context. */
public final class YamlSchemaValidation {
  private static final ConcurrentHashMap<String, Schema> SCHEMAS = new ConcurrentHashMap<>();

  private YamlSchemaValidation() {
  }

  public static List<String> validate(String name, String yaml) {
    try {
      Schema schema = SCHEMAS.computeIfAbsent(name, key -> {
        try (var in = YamlSchemaValidation.class.getResourceAsStream("/schemas/" + key)) {
          if (in == null)
            throw new IllegalStateException("Missing YAML schema: " + key);
          return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(in);
        } catch (Exception e) {
          throw new IllegalStateException("Cannot load YAML schema: " + key, e);
        }
      });
      var tree = new YAMLMapper().readTree(yaml);
      return schema.validate(JsonMapperFactory.getInstance().readTree(tree.toString())).stream()
          // Error messages may contain submitted values. Paths identify the problem without exposing secrets.
          .map(error -> "Schema: " + error.getInstanceLocation() + " (" + error.getKeyword() + ")").toList();
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      return List.of("Invalid YAML structure");
    }
  }
}
