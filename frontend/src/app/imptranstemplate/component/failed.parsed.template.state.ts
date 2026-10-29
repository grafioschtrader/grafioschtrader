export class ParsedTemplateState {
  constructor(
    public templatePurpose: string | null,
    public validSince: string | Date | null | undefined,
    public localeStr: string | null
  ) {}
}

export class FailedParsedTemplateState extends ParsedTemplateState {
  constructor(
    public lastMatchingProperty: string,
    public errorMessage: string,
    templatePurpose: string | null,
    validSince: string | Date | null | undefined,
    localeStr: string | null
  ) {
    super(templatePurpose, validSince, localeStr);
  }
}
