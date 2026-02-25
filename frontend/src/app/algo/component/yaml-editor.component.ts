import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  Output,
  ViewChild
} from '@angular/core';
import * as yaml from 'js-yaml';

/**
 * Wrapper component for the Monaco Editor with YAML language support.
 * Provides syntax highlighting, code folding, YAML syntax validation,
 * and JSON Schema-based autocompletion and hover documentation.
 *
 * Monaco is loaded lazily via its AMD loader from the /vs/ assets directory
 * on first use and cached globally for subsequent instances.
 */
@Component({
  selector: 'yaml-editor',
  standalone: true,
  template: `
    <div #editorContainer [style.height]="height" style="border: 1px solid #dee2e6; border-radius: 4px;"></div>
  `
})
export class YamlEditorComponent implements AfterViewInit, OnDestroy {

  @ViewChild('editorContainer', {static: true}) editorContainer: ElementRef<HTMLDivElement>;

  @Input() height = '500px';

  @Input() set value(val: string) {
    this._value = val || '';
    if (this.editor && this.editor.getValue() !== this._value) {
      this.editor.setValue(this._value);
    }
  }

  get value(): string {
    return this._value;
  }

  @Output() valueChange = new EventEmitter<string>();

  /** JSON Schema object for autocompletion and hover documentation */
  @Input() schema: any;

  /**
   * Optional map of YAML field names to completion items for inline value autocompletion.
   * When the cursor is positioned in the value portion of a matching field (after the colon),
   * these completions are offered in addition to schema-based suggestions.
   *
   * Each entry maps a field name (e.g., 'condition', 'expression') to an array of
   * {@link YamlFieldCompletion} objects describing variables, functions, or keywords.
   */
  @Input() fieldCompletions: { [fieldName: string]: YamlFieldCompletion[] };

  private _value = '';
  private editor: any;
  private validationTimer: any;
  private completionDisposable: any;
  private hoverDisposable: any;

  /** Tracks whether Monaco AMD loader has been loaded globally */
  private static monacoLoadPromise: Promise<any> | null = null;

  ngAfterViewInit(): void {
    this.initMonaco();
  }

  private async initMonaco(): Promise<void> {
    const monaco = await YamlEditorComponent.ensureMonacoLoaded();
    this.createEditor(monaco);
  }

  /**
   * Loads Monaco via its AMD loader from /vs/ assets. The loader and all Monaco modules
   * are served from the assets directory configured in angular.json. This avoids bundler
   * compatibility issues with Monaco's web workers.
   */
  private static ensureMonacoLoaded(): Promise<any> {
    if ((window as any).monaco) {
      return Promise.resolve((window as any).monaco);
    }
    if (YamlEditorComponent.monacoLoadPromise) {
      return YamlEditorComponent.monacoLoadPromise;
    }
    YamlEditorComponent.monacoLoadPromise = new Promise<any>((resolve, reject) => {
      const win = window as any;
      if (win.require?.config) {
        YamlEditorComponent.requireMonaco(resolve);
        return;
      }
      const loaderUrl = new URL('vs/loader.js', document.baseURI).toString();
      const script = document.createElement('script');
      script.src = loaderUrl;
      script.onload = () => YamlEditorComponent.requireMonaco(resolve);
      script.onerror = () => reject(new Error('Failed to load Monaco loader from ' + loaderUrl));
      document.head.appendChild(script);
    });
    return YamlEditorComponent.monacoLoadPromise;
  }

  private static requireMonaco(resolve: (m: any) => void): void {
    const req = (window as any).require;
    const basePath = new URL('.', document.baseURI).toString();
    req.config({paths: {vs: basePath + 'vs'}});
    req(['vs/editor/editor.main'], (monaco: any) => {
      (window as any).monaco = monaco;
      resolve(monaco);
    });
  }

  private createEditor(monaco: any): void {
    this.editor = monaco.editor.create(this.editorContainer.nativeElement, {
      value: this._value,
      language: 'yaml',
      theme: 'vs',
      minimap: {enabled: false},
      wordWrap: 'on',
      lineNumbers: 'on',
      scrollBeyondLastLine: false,
      automaticLayout: true,
      tabSize: 2,
      fontSize: 13,
      renderWhitespace: 'selection',
      folding: true,
      foldingStrategy: 'indentation',
      suggest: {showWords: false},
      quickSuggestions: {other: true, comments: false, strings: true}
    });

    this.editor.onDidChangeModelContent(() => {
      this._value = this.editor.getValue();
      this.valueChange.emit(this._value);
      this.scheduleValidation(monaco);
    });

    this.registerCompletionProvider(monaco);
    this.registerHoverProvider(monaco);
    this.scheduleValidation(monaco);
  }

  /**
   * Debounced YAML validation using js-yaml. Parses the editor content and sets
   * Monaco error markers for any syntax errors found.
   */
  private scheduleValidation(monaco: any): void {
    clearTimeout(this.validationTimer);
    this.validationTimer = setTimeout(() => this.validateYaml(monaco), 500);
  }

  private validateYaml(monaco: any): void {
    const model = this.editor?.getModel();
    if (!model) return;

    const content = model.getValue();
    const markers: any[] = [];

    if (content.trim()) {
      try {
        yaml.load(content);
      } catch (e: any) {
        if (e.mark) {
          markers.push({
            severity: monaco.MarkerSeverity.Error,
            message: e.reason || 'YAML syntax error',
            startLineNumber: (e.mark.line || 0) + 1,
            startColumn: (e.mark.column || 0) + 1,
            endLineNumber: (e.mark.line || 0) + 1,
            endColumn: model.getLineMaxColumn((e.mark.line || 0) + 1)
          });
        }
      }
    }

    monaco.editor.setModelMarkers(model, 'yaml-validation', markers);
  }

  /**
   * Registers a completion provider for YAML that suggests property names and enum values
   * based on the provided JSON Schema. Context is determined by analyzing indentation
   * and parent keys in the YAML structure.
   */
  private registerCompletionProvider(monaco: any): void {
    const editorModel = this.editor.getModel();
    this.completionDisposable = monaco.languages.registerCompletionItemProvider('yaml', {
      triggerCharacters: ['\n', ' ', ':'],
      provideCompletionItems: (model: any, position: any) => {
        if (model !== editorModel) return {suggestions: []};
        const suggestions: any[] = [];
        const lineContent = model.getLineContent(position.lineNumber);
        const indent = lineContent.search(/\S|$/);
        const word = model.getWordUntilPosition(position);
        const range = {
          startLineNumber: position.lineNumber,
          endLineNumber: position.lineNumber,
          startColumn: word.startColumn,
          endColumn: word.endColumn
        };

        // Schema-based property suggestions (checked at invocation time, not registration)
        const properties = this.schema?.$defs ? this.getEffectiveProperties(
          this.findSchemaContext(model, position.lineNumber, indent)) : null;

        if (properties) {
          for (const [key, prop] of Object.entries<any>(properties)) {
            const detail = this.getPropertyDetail(prop);
            suggestions.push({
              label: key,
              kind: monaco.languages.CompletionItemKind.Property,
              insertText: this.getInsertText(key, prop),
              detail,
              documentation: prop.description || this.resolveRefDescription(prop),
              range
            });
          }
        }

        // If we're after a colon, suggest enum values and field completions
        if (lineContent.includes(':')) {
          const keyMatch = lineContent.match(/^\s*(\w+)\s*:/);
          if (keyMatch) {
            const fieldName = keyMatch[1];
            if (properties) {
              const fieldSchema = properties[fieldName] || this.findFieldInSchema(fieldName);
              if (fieldSchema) {
                const resolved = fieldSchema.$ref ? this.resolveRef(fieldSchema.$ref) : fieldSchema;
                const enumVals = resolved?.enum || resolved?.properties?.[fieldName]?.enum;
                if (enumVals) {
                  for (const val of enumVals) {
                    suggestions.push({
                      label: String(val),
                      kind: monaco.languages.CompletionItemKind.EnumMember,
                      insertText: String(val),
                      range
                    });
                  }
                }
              }
            }

            // Offer field-specific inline completions (e.g., EvalEx variables/functions)
            const completions = this.fieldCompletions?.[fieldName];
            if (completions) {
              for (const c of completions) {
                const kindKey = c.kind || 'Variable';
                suggestions.push({
                  label: c.label,
                  kind: monaco.languages.CompletionItemKind[kindKey] ?? monaco.languages.CompletionItemKind.Variable,
                  insertText: c.insertText,
                  insertTextRules: c.isSnippet ? monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet : undefined,
                  detail: c.detail,
                  documentation: c.documentation,
                  range
                });
              }
            }
          }
        }

        return {suggestions};
      }
    });
  }

  /**
   * Walks up the YAML structure from the current line to determine which schema
   * definition corresponds to the current cursor position.
   */
  private findSchemaContext(model: any, lineNumber: number, currentIndent: number): any {
    if (currentIndent === 0) {
      return this.schema;
    }

    // Walk upward to find parent keys
    const parentKeys: string[] = [];
    let targetIndent = currentIndent;
    for (let i = lineNumber - 1; i >= 1; i--) {
      const line = model.getLineContent(i);
      const lineIndent = line.search(/\S|$/);
      if (lineIndent < targetIndent && line.trim()) {
        const keyMatch = line.match(/^\s*(\w[\w_]*)\s*:/);
        if (keyMatch) {
          parentKeys.unshift(keyMatch[1]);
          targetIndent = lineIndent;
          if (lineIndent === 0) break;
        }
      }
    }

    // Traverse schema following parent keys
    let context: any = this.schema;
    for (const key of parentKeys) {
      const prop = this.getEffectiveProperties(context)?.[key];
      if (!prop) break;
      if (prop.$ref) {
        context = this.resolveRef(prop.$ref);
      } else if (prop.properties) {
        context = prop;
      } else if (prop.type === 'array' && prop.items?.$ref) {
        context = this.resolveRef(prop.items.$ref);
      } else {
        break;
      }
    }

    return context;
  }

  /**
   * Extracts the effective properties map from a schema node. If the node has direct
   * properties, returns them. Otherwise merges properties from oneOf/anyOf branches,
   * which is needed for schemas like the fee model that use oneOf at the root level.
   */
  private getEffectiveProperties(schemaNode: any): { [key: string]: any } | null {
    if (!schemaNode) return null;
    if (schemaNode.properties) return schemaNode.properties;

    const branches = schemaNode.oneOf || schemaNode.anyOf;
    if (Array.isArray(branches)) {
      const merged: { [key: string]: any } = {};
      for (const branch of branches) {
        const resolved = branch.$ref ? this.resolveRef(branch.$ref) : branch;
        if (resolved?.properties) {
          Object.assign(merged, resolved.properties);
        }
      }
      return Object.keys(merged).length > 0 ? merged : null;
    }

    return null;
  }

  private resolveRef(ref: string): any {
    if (!ref?.startsWith('#/$defs/')) return null;
    const defName = ref.substring('#/$defs/'.length);
    return this.schema?.$defs?.[defName];
  }

  private resolveRefDescription(prop: any): string {
    if (prop.$ref) {
      const resolved = this.resolveRef(prop.$ref);
      return resolved?.description || '';
    }
    return '';
  }

  private getPropertyDetail(prop: any): string {
    if (prop.$ref) {
      const name = prop.$ref.replace('#/$defs/', '');
      return `object (${name})`;
    }
    if (prop.enum) return `enum: [${prop.enum.join(', ')}]`;
    if (prop.type === 'array') return 'array';
    if (prop.type) return Array.isArray(prop.type) ? prop.type.filter((t: string) => t !== 'null').join(' | ') : prop.type;
    return '';
  }

  private getInsertText(key: string, prop: any): string {
    if (prop.$ref || prop.properties) return key + ':\n  ';
    if (prop.type === 'array') return key + ':\n  - ';
    if (prop.type === 'boolean') return key + ': ';
    return key + ': ';
  }

  private findFieldInSchema(fieldName: string): any {
    // Search all $defs for a property matching the field name
    if (!this.schema?.$defs) return null;
    for (const def of Object.values<any>(this.schema.$defs)) {
      if (def.properties?.[fieldName]) {
        return def.properties[fieldName];
      }
    }
    return null;
  }

  /**
   * Registers a hover provider that shows JSON Schema descriptions
   * for YAML keys when the user hovers over them.
   */
  private registerHoverProvider(monaco: any): void {
    const editorModel = this.editor.getModel();
    this.hoverDisposable = monaco.languages.registerHoverProvider('yaml', {
      provideHover: (model: any, position: any) => {
        if (model !== editorModel || !this.schema) return null;
        const lineContent = model.getLineContent(position.lineNumber);
        const keyMatch = lineContent.match(/^\s*(\w[\w_]*)\s*:/);
        if (!keyMatch) return null;

        const key = keyMatch[1];
        const word = model.getWordAtPosition(position);
        if (!word || word.word !== key) return null;

        const indent = lineContent.search(/\S|$/);
        const context = this.findSchemaContext(model, position.lineNumber, indent);
        const prop = this.getEffectiveProperties(context)?.[key];
        if (!prop) return null;

        const resolved = prop.$ref ? this.resolveRef(prop.$ref) : prop;
        const description = prop.description || resolved?.description || '';
        const detail = this.getPropertyDetail(prop);

        const contents = [];
        if (detail) {
          contents.push({value: '```\n' + key + ': ' + detail + '\n```'});
        }
        if (description) {
          contents.push({value: description});
        }
        if (resolved?.enum) {
          contents.push({value: 'Values: `' + resolved.enum.join('` | `') + '`'});
        }

        return contents.length > 0 ? {
          range: new monaco.Range(
            position.lineNumber, word.startColumn,
            position.lineNumber, word.endColumn
          ),
          contents
        } : null;
      }
    });
  }

  ngOnDestroy(): void {
    clearTimeout(this.validationTimer);
    this.completionDisposable?.dispose();
    this.hoverDisposable?.dispose();
    this.editor?.dispose();
  }
}

/**
 * Describes a single completion item for inline value autocompletion in YAML string fields.
 * Used with YamlEditorComponent's fieldCompletions input to provide domain-specific suggestions
 * (e.g., EvalEx variables and functions inside condition/expression fields).
 */
export interface YamlFieldCompletion {
  /** Display label shown in the completion list */
  label: string;
  /** Text inserted when the completion is accepted. Use '$0' for cursor placement. */
  insertText: string;
  /** Short detail string shown next to the label (e.g., 'variable', 'function') */
  detail: string;
  /** Longer documentation shown in the detail pane */
  documentation?: string;
  /** Monaco CompletionItemKind name: 'Variable', 'Function', 'Keyword'. Defaults to 'Variable'. */
  kind?: string;
  /** If true, insertText uses snippet syntax (e.g., tab stops with $1, $0) */
  isSnippet?: boolean;
}
