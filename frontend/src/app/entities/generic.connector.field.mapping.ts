export class GenericConnectorFieldMapping {
  idFieldMapping: number | null = null;
  targetField: string | null = null;
  sourceExpression: string | null = null;
  csvColumnIndex: number | null = null;
  dividerExpression: string | null = null;
  required: boolean = false;
}
