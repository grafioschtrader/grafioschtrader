import type { LogoScopeName } from '../types/logo.scope';
import { SpecialInvestmentInstruments } from '../types/special.investment.instruments';

/** Maximum upload size accepted by LogoSvgSanitizer on the backend (32 KB). */
export const MAX_LOGO_SVG_BYTES = 32_768;

/** Cell style of the company logo column. Each watchlist table includes it in its own component styles. */
export const WATCHLIST_LOGO_CELL_STYLE = `
  .watchlist-logo-preview {
    display: block;
    margin: auto;
    box-sizing: content-box;
    padding-inline: 2px;
    border-radius: 3px;
    background: white;
    object-fit: contain;
  }
`;

/** Lightweight metadata of a resolved or searchable InstrumentLogo entry from the backend. */
export interface InstrumentLogoRefDto {
  idInstrumentLogo: number;
  name: string;
  isin: string | null;
  logoScope: LogoScopeName;
  assetPath: string | null;
  contentSha256: string | null;
  explicit: boolean;
  createdBy?: number | null;
}

/** Request payload sent to POST /api/instrumentlogo/validate and POST /api/instrumentlogo. */
export interface InstrumentLogoUploadPayload {
  name: string;
  isin: string | null;
  logoScope: LogoScopeName;
  svgContent: string;
}

/** Request payload sent to PUT /api/instrumentlogo when updating an existing catalog logo. */
export interface InstrumentLogoUpdatePayload {
  idInstrumentLogo: number;
  name: string;
  isin: string | null;
  logoScope: LogoScopeName;
  svgContent?: string | null;
}

/** Validated drag-and-drop draft kept in memory in the security dialog until the user saves. */
export interface InstrumentLogoUploadDraft extends InstrumentLogoUploadPayload {
  previewUrl: string;
}

/** Input passed via DynamicDialogConfig.data when opening InstrumentLogoSelectDialogComponent. */
export interface InstrumentLogoDialogData {
  securityName: string | null;
  isin: string | null;
  idInstrumentLogo: number | null;
  productLink: string | null;
  specialInvestmentInstrument: string | null;
  isTenantPrivate: boolean;
  resolvedLogo: InstrumentLogoRefDto | null;
}

/** Result returned via DynamicDialogRef.close(...) when the user confirms a logo in Dialog (D). */
export type InstrumentLogoDialogResult =
  { kind: 'existing'; logo: InstrumentLogoRefDto } | { kind: 'draft'; draft: InstrumentLogoUploadDraft };

/** Query parameters for GET /api/instrumentlogo/search. */
export interface InstrumentLogoSearchParams {
  name?: string | null;
  isin?: string | null;
  scope?: LogoScopeName | null;
  idInstrumentLogo?: number | null;
  productLink?: string | null;
  specialInvestmentInstrument?: string | null;
}

/** Query parameters for GET /api/instrumentlogo/resolve. */
export interface InstrumentLogoResolveParams {
  idInstrumentLogo?: number | null;
  isin?: string | null;
  name?: string | null;
  productLink?: string | null;
  specialInvestmentInstrument?: string | null;
}

/** Computed initial values for a newly dropped SVG draft row in Dialog (D). */
export interface LogoDraftPrefill {
  name: string;
  isin: string | null;
  logoScope: LogoScopeName;
  isinInUseHint: boolean;
}

/** Flat row shape bound to InstrumentLogoSelectTableComponent (ConfigurableTableComponent). */
export interface LogoCatalogTableRow {
  rowKey: string;
  isDraft: boolean;
  isEditable: boolean;
  name: string;
  isin: string | null;
  logoScope: LogoScopeName;
  logoScopeKey: 'LOGO_SCOPE_COMPANY' | 'LOGO_SCOPE_ISSUER';
  sourceKey: 'INSTRUMENT_LOGO_SOURCE_DRAFT' | 'INSTRUMENT_LOGO_SOURCE_DEPLOYED' | 'INSTRUMENT_LOGO_SOURCE_DB';
  logo: InstrumentLogoRefDto | null;
  previewUrl: string | null;
}

/** Converts a SpecialInvestmentInstruments enum value or string name into its backend enum name string. */
export function toSpecialInvestmentInstrumentName(
  value: SpecialInvestmentInstruments | string | null | undefined
): string | null {
  if (value == null) {
    return null;
  }
  return typeof value === 'number' ? (SpecialInvestmentInstruments[value] ?? null) : value;
}

const ISSUER_SCOPE_INSTRUMENTS = new Set<string>([
  SpecialInvestmentInstruments[SpecialInvestmentInstruments.ETF],
  SpecialInvestmentInstruments[SpecialInvestmentInstruments.MUTUAL_FUND],
  SpecialInvestmentInstruments[SpecialInvestmentInstruments.ISSUER_RISK_PRODUCT]
]);

/** Returns true for investment instruments whose automatic logo fallback uses issuer wordmarks. */
export function isIssuerScopeInstrument(specialInvestmentInstrument: string | null | undefined): boolean {
  return specialInvestmentInstrument != null && ISSUER_SCOPE_INSTRUMENTS.has(specialInvestmentInstrument);
}

/** Trims and uppercases an ISIN string, returning null when blank. */
export function normalizeIsinInput(isin: string | null | undefined): string | null {
  const trimmed = isin?.trim();
  return trimmed ? trimmed.toUpperCase() : null;
}

/** Checks whether a dropped or selected file looks like an SVG file by extension or MIME type. */
export function isSvgFile(fileName: string, mimeType: string): boolean {
  const lowerName = fileName.trim().toLowerCase();
  const lowerMime = mimeType.trim().toLowerCase();
  return lowerName.endsWith('.svg') || lowerMime === 'image/svg+xml';
}

/** Measures the UTF-8 byte length of a string (matches the backend 32 KB byte limit). */
export function utf8ByteLength(text: string): number {
  return new TextEncoder().encode(text).length;
}

/**
 * Computes the prefilled Name, ISIN and Scope for a newly dropped SVG draft in Dialog (D):
 * - ETF / Mutual Fund / Issuer-Risk Product -> ISSUER scope, empty ISIN, resolved issuer name (or security/search name).
 * - Other instruments -> COMPANY scope, security/search name, ISIN from security dialog (or search field) unless the
 *   security is tenant-private or that ISIN is already occupied in the catalog (in which case ISIN stays null with a
 *   hint).
 */
export function computeLogoDraftPrefill(options: {
  securityName: string | null | undefined;
  searchName: string | null | undefined;
  securityIsin: string | null | undefined;
  searchIsin: string | null | undefined;
  specialInvestmentInstrument: string | null | undefined;
  isTenantPrivate: boolean;
  resolvedLogo: InstrumentLogoRefDto | null | undefined;
  catalogResults: readonly InstrumentLogoRefDto[];
}): LogoDraftPrefill {
  const fallbackName = options.securityName?.trim() || options.searchName?.trim() || '';
  if (isIssuerScopeInstrument(options.specialInvestmentInstrument)) {
    const issuerName =
      options.resolvedLogo?.logoScope === 'ISSUER' && options.resolvedLogo.name.trim()
        ? options.resolvedLogo.name.trim()
        : fallbackName;
    return {
      name: issuerName,
      isin: null,
      logoScope: 'ISSUER',
      isinInUseHint: false
    };
  }

  const candidateIsin = options.isTenantPrivate
    ? null
    : (normalizeIsinInput(options.securityIsin) ?? normalizeIsinInput(options.searchIsin));

  const occupiedIsin =
    candidateIsin != null &&
    (normalizeIsinInput(options.resolvedLogo?.isin) === candidateIsin ||
      options.catalogResults.some((item) => normalizeIsinInput(item.isin) === candidateIsin));

  return {
    name: fallbackName,
    isin: occupiedIsin ? null : candidateIsin,
    logoScope: 'COMPANY',
    isinInUseHint: occupiedIsin
  };
}
