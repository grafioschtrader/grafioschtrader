import { describe, expect, it, vi } from 'vitest';
import { Subject, throwError } from 'rxjs';
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

describe('YAML validation before submission', () => {
  it('rejects syntax errors synchronously and accepts a correction', () => {
    const component = new YamlEditorComponent();
    component.value = 'rules: [';
    expect(component.checkSyntax()).toBe(false);
    expect(component.diagnostics[0].line).toBeGreaterThan(0);
    component.value = 'rules: []';
    expect(component.checkSyntax()).toBe(true);
    expect(component.diagnostics).toEqual([]);
  });

  it('never authorizes a save or displays errors from an older text revision', async () => {
    const response = new Subject<any[]>();
    const component = new YamlEditorComponent({ post: () => response } as any);
    component.getHeaders = () => ({ headers: null });
    component.format = 'FEES';
    component.value = 'rules: []';
    const validation = component.validateForSubmit();
    component.value = 'rules: [{name: Revised}]';
    response.next([{ category: 'DOMAIN', message: 'Old result' }]);
    expect(await validation).toBe(false);
    expect(component.diagnostics).toEqual([]);
    expect(component.validated).toBe(false);
  });

  it('does not send invalid syntax to the server and keeps request failures visible', async () => {
    const post = vi.fn();
    const component = new YamlEditorComponent({ post } as any);
    component.format = 'TOKENS';
    component.value = 'seed: [';
    expect(await component.validateForSubmit()).toBe(false);
    expect(post).not.toHaveBeenCalled();
    component.getHeaders = () => ({ headers: null });
    component.value = 'seed: {}';
    post.mockReturnValue(throwError(() => new Error('offline')));
    expect(await component.validateForSubmit()).toBe(false);
    expect(component.validating).toBe(false);
    expect(component.diagnostics).toEqual([{ category: 'REQUEST', message: 'YAML_VALIDATION_UNAVAILABLE' }]);
  });

  it('resolves account-name mappings and distinguishes custody expression paths', () => {
    const component = new YamlEditorComponent() as any;
    component.schema = { type: 'object', additionalProperties: { properties: { accruedFees: { type: 'number' } } } };
    expect(component.findSchemaContext(model("'My account':", '  '), 2, 2).properties).toHaveProperty('accruedFees');
    expect(
      component.fieldPath(
        model(
          'custody:',
          '  periods:',
          '    - validFrom: 2026-01-01',
          '      valueRules:',
          '        - condition: true',
          '          expression: '
        ),
        6,
        'expression'
      )
    ).toBe('custody.periods.valueRules.expression');
  });
});
