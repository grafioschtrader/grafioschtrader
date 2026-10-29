export class ParsedTemplateState {
  constructor(
    public templatePurpose: string,
    public validSince: string | Date,
    public localeStr: string
  ) {}
}

export class FailedParsedTemplateState extends ParsedTemplateState {
  constructor(
    public lastMatchingProperty: string,
    public errorMessage: string,
    templatePurpose: string,
    validSince: string | Date,
    localeStr: string
  ) {
    super(templatePurpose, validSince, localeStr);
  }
}
