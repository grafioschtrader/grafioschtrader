import { describe, expect, it } from 'vitest';
import { YamlEditorComponent } from './yaml-editor.component';

const TAX_SCHEMA = {
  properties: {
    version: { const: 1 },
    transactionTaxes: { $ref: '#/definitions/section' }
  },
  definitions: {
    rule: {
      properties: {
        name: { type: 'string' },
        condition: { type: 'string' },
        expression: { type: 'string' }
      }
    },
    section: {
      properties: {
        requiredInputs: {
          type: 'array',
          items: { enum: ['issuerCountry', 'dealerCountry'] }
        },
        rules: {
          type: 'array',
          items: { $ref: '#/definitions/rule' }
        }
      }
    }
  }
};

function model(...lines: string[]): { getLineContent(line: number): string } {
  return { getLineContent: (line: number) => lines[line - 1] };
}

describe('YamlEditorComponent schema assistance', () => {
  function editor(): any {
    const editor = new YamlEditorComponent() as any;
    editor.schema = TAX_SCHEMA;
    return editor;
  }

  it('resolves draft-7 definitions while retaining nested rule context', () => {
    const component = editor();
    expect(component.resolveRef('#/definitions/section')).toBe(TAX_SCHEMA.definitions.section);

    const context = component.findSchemaContext(
      model('transactionTaxes:', '  rules:', '    - name: Purchase tax', '      '),
      4,
      6
    );
    expect(component.getEffectiveProperties(context)).toHaveProperty('condition');

    component.schema = { $defs: { rule: TAX_SCHEMA.definitions.rule } };
    expect(component.resolveRef('#/$defs/rule')).toBe(TAX_SCHEMA.definitions.rule);
  });

  it('finds enum values for scalar sequence items such as requiredInputs', () => {
    const component = editor();
    const itemSchema = component.findArrayItemSchema(
      model('transactionTaxes:', '  requiredInputs:', '    - issuer'),
      3,
      4
    );
    expect(itemSchema.enum).toEqual(['issuerCountry', 'dealerCountry']);
  });

  it('offers schema constants and enum values with their documentation', () => {
    const component = editor();
    const suggestions: any[] = [];
    const monaco = { languages: { CompletionItemKind: { EnumMember: 13 } } };
    const range = { startColumn: 1, endColumn: 1 };

    component.addSchemaValueSuggestions(monaco, suggestions, { const: 1, description: 'Version' }, range);
    component.addSchemaValueSuggestions(
      monaco,
      suggestions,
      TAX_SCHEMA.definitions.section.properties.requiredInputs.items,
      range
    );

    expect(suggestions.map((suggestion) => suggestion.label)).toEqual(['1', 'issuerCountry', 'dealerCountry']);
    expect(suggestions[0]).toMatchObject({ insertText: '1', documentation: 'Version', range });
  });
});
