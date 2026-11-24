package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.entities.InstrumentLogo;

public interface InstrumentLogoJpaRepository extends JpaRepository<InstrumentLogo, Integer>,
    InstrumentLogoJpaRepositoryCustom, UpdateCreateJpaRepository<InstrumentLogo> {

  boolean existsByIsinIgnoreCase(String isin);

  boolean existsByNameIgnoreCase(String name);

  boolean existsByIsinIgnoreCaseAndIdInstrumentLogoNot(String isin, Integer idInstrumentLogo);

  boolean existsByNameIgnoreCaseAndIdInstrumentLogoNot(String name, Integer idInstrumentLogo);

  /**
   * Loads lightweight catalog metadata for every {@link InstrumentLogo} row without reading the potentially large
   * {@code svg_content} CLOB, ordered deterministically by primary key so issuer host matching preserves catalog order.
   */
  @Query("""
      SELECT l.idInstrumentLogo AS idInstrumentLogo, l.isin AS isin, l.name AS name, l.logoScope AS logoScope,
        l.assetPath AS assetPath, l.contentSha256 AS contentSha256, l.namePatterns AS namePatterns,
        l.hosts AS hosts, l.hostSuffixes AS hostSuffixes, l.createdBy AS createdBy,
        CASE WHEN l.svgContent IS NOT NULL THEN true ELSE false END AS hasSvgContent
      FROM InstrumentLogo l ORDER BY l.idInstrumentLogo ASC""")
  List<InstrumentLogoMetadataProjection> findAllMetadataOrderByIdAsc();

  /** Projection carrying all metadata needed for logo resolution and catalog search without {@code svg_content}. */
  interface InstrumentLogoMetadataProjection {

    Integer getIdInstrumentLogo();

    String getIsin();

    String getName();

    byte getLogoScope();

    String getAssetPath();

    String getContentSha256();

    String getNamePatterns();

    String getHosts();

    String getHostSuffixes();

    Integer getCreatedBy();

    boolean getHasSvgContent();
  }
}
