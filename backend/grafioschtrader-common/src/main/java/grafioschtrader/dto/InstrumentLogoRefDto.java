package grafioschtrader.dto;

import java.io.Serializable;

import grafioschtrader.types.LogoScope;

/**
 * Lightweight metadata of a resolved or searchable {@link grafioschtrader.entities.InstrumentLogo}, carried on
 * watchlists, treemap nodes and security dialogs without the raw SVG payload.
 *
 * @param idInstrumentLogo primary key of the catalog entry
 * @param name             unique display name of the company or issuer
 * @param isin             optional unique ISIN, {@code null} for issuer wordmarks or ISIN-less entries
 * @param logoScope        company or issuer scope
 * @param assetPath        relative path beneath {@code assets/company-logos/} for deployed SVGs, {@code null} for
 *                         DB-stored SVGs
 * @param contentSha256    lowercase hex SHA-256 of the SVG content, used for cache busting
 * @param explicit         {@code true} when assigned explicitly via {@code security.id_instrument_logo}, {@code false}
 *                         when resolved automatically by ISIN or issuer rules
 * @param createdBy        user ID of the creator ({@code 0} for migration-deployed entries), used for edit/delete
 *                         rights checks in the UI
 */
public record InstrumentLogoRefDto(Integer idInstrumentLogo, String name, String isin, LogoScope logoScope,
    String assetPath, String contentSha256, boolean explicit, Integer createdBy) implements Serializable {
}
