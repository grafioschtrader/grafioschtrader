import * as fs from 'fs';
import * as path from 'path';
import type {
  FullConfig,
  FullResult,
  Reporter,
  Suite,
  TestCase,
  TestResult,
  TestStep
} from '@playwright/test/reporter';

/**
 * Reporter that answers "where did the run time go" for the e2e suite. It runs alongside the
 * built-in `list` reporter and only observes — it never influences test execution.
 *
 * The suite runs with `workers: 1`, so wall-clock time is the sum of the tests plus the harness
 * overhead between them; both are reported separately. Aggregation happens on three levels: per
 * test, per spec file, and per operation (Playwright step). The operation level is the interesting
 * one, because it collapses all `page.waitForTimeout` calls of the whole run into a single row with
 * a call count, which makes the cost of hard sleeps directly comparable to real work.
 */

/** One finished test, flattened for reporting. */
interface TestRecord {
  /** Spec file relative to the Playwright root, e.g. 'e2e/115-taxdata.spec.ts'. */
  file: string;
  project: string;
  /** Full title path without the project name, e.g. 'tax data - … › creates tax country …'. */
  title: string;
  durationMs: number;
  status: string;
  retry: number;
}

/** One finished step, reduced to what the aggregations need. */
interface StepRecord {
  /** 'pw:api', 'expect', 'fixture', 'hook', 'test.step', 'test.attach', … */
  category: string;
  /** Title after {@link normalizeStepTitle}, so equivalent calls group into one row. */
  title: string;
  /** Wall time of the step including its children. */
  durationMs: number;
  /** Wall time excluding direct children; summing this over all steps never double counts. */
  selfMs: number;
}

/** Aggregate of several steps sharing the same key. */
interface StepBucket {
  count: number;
  selfMs: number;
  totalMs: number;
}

const OUTPUT_NAME = path.join('test-results', 'e2e-timing.json');
const SLOWEST_OPERATIONS = 25;

export default class TimingReporter implements Reporter {
  private readonly tests: TestRecord[] = [];
  private readonly steps: StepRecord[] = [];
  private rootDir = process.cwd();
  private outputFile = path.join(this.rootDir, OUTPUT_NAME);
  private startedAt = 0;
  private endedAt = 0;

  /** The `list` reporter prints progress lines; this one must stay silent until the very end. */
  printsToStdio(): boolean {
    return true;
  }

  onBegin(config: FullConfig, _suite: Suite): void {
    this.rootDir = config.rootDir;
    this.outputFile = path.join(this.rootDir, OUTPUT_NAME);
    this.startedAt = Date.now();
  }

  onTestEnd(test: TestCase, result: TestResult): void {
    const titlePath = test.titlePath();
    this.tests.push({
      file: this.relativeFile(test.location.file),
      // titlePath() is [root, project, file, ...describes, test]; drop the first three.
      project: titlePath[1] ?? '',
      title: titlePath.slice(3).join(' › '),
      durationMs: result.duration,
      status: result.status,
      retry: result.retry
    });
  }

  onStepEnd(_test: TestCase, _result: TestResult, step: TestStep): void {
    const childrenMs = step.steps.reduce((sum, child) => sum + child.duration, 0);
    this.steps.push({
      category: step.category,
      title: normalizeStepTitle(step.title),
      durationMs: step.duration,
      // A parent can finish marginally before a child's duration is accounted for; clamp at 0.
      selfMs: Math.max(0, step.duration - childrenMs)
    });
  }

  async onEnd(result: FullResult): Promise<void> {
    this.endedAt = Date.now();
    if (this.tests.length === 0) {
      // `--list` and a fully filtered-out run end up here; keep the previous report on disk.
      return;
    }
    this.writeJson(result.status);
    const lines: string[] = [];
    this.appendWallClock(lines);
    this.appendSlowestTests(lines);
    this.appendPerSpecFile(lines);
    this.appendStepCategories(lines);
    this.appendSlowestOperations(lines);
    lines.push('', `Raw data: ${this.relativeFile(this.outputFile)}`, '');
    console.log(lines.join('\n'));
  }

  // ---------------------------------------------------------------- report blocks

  private appendWallClock(lines: string[]): void {
    const wallMs = this.endedAt - this.startedAt;
    const testMs = sum(this.tests.map((t) => t.durationMs));
    const overheadMs = Math.max(0, wallMs - testMs);
    lines.push('', banner('e2e timing — wall clock'));
    lines.push(`  total run           ${formatMs(wallMs).padStart(8)}`);
    lines.push(
      `  inside tests        ${formatMs(testMs).padStart(8)}  ${share(testMs, wallMs)}` +
        `  (${plural(this.tests.length, 'test')}, mean ${formatMs(testMs / this.tests.length)})`
    );
    lines.push(
      `  harness overhead    ${formatMs(overheadMs).padStart(8)}  ${share(overheadMs, wallMs)}` +
        '  (global setup, worker + browser startup, teardown)'
    );
  }

  private appendSlowestTests(lines: string[]): void {
    const testMs = sum(this.tests.map((t) => t.durationMs));
    const sorted = [...this.tests].sort((a, b) => b.durationMs - a.durationMs);
    // File and title as two columns: truncating the concatenation would drop whichever end is cut.
    const fileWidth = Math.max(...sorted.map((t) => t.file.length));
    const titleWidth = Math.min(80, Math.max(...sorted.map((t) => t.title.length)));
    lines.push('', banner('e2e timing — tests, slowest first'));
    for (const test of sorted) {
      const flag = test.status === 'passed' ? '' : `  [${test.status}]`;
      lines.push(
        `  ${test.file.padEnd(fileWidth)}  ${truncate(test.title, titleWidth).padEnd(titleWidth)}` +
          `  ${formatMs(test.durationMs).padStart(8)}  ${share(test.durationMs, testMs)}${flag}`
      );
    }
  }

  private appendPerSpecFile(lines: string[]): void {
    const testMs = sum(this.tests.map((t) => t.durationMs));
    const byFile = new Map<string, { count: number; ms: number }>();
    for (const test of this.tests) {
      const entry = byFile.get(test.file) ?? { count: 0, ms: 0 };
      entry.count += 1;
      entry.ms += test.durationMs;
      byFile.set(test.file, entry);
    }
    const sorted = [...byFile.entries()].sort((a, b) => b[1].ms - a[1].ms);
    const labelWidth = Math.max(...sorted.map(([file]) => file.length));
    lines.push('', banner('e2e timing — spec files, slowest first'));
    for (const [file, entry] of sorted) {
      lines.push(
        `  ${file.padEnd(labelWidth)}  ${formatMs(entry.ms).padStart(8)}` +
          `  ${share(entry.ms, testMs)}  ${plural(entry.count, 'test').padStart(8)}` +
          `  mean ${formatMs(entry.ms / entry.count)}`
      );
    }
  }

  private appendStepCategories(lines: string[]): void {
    const byCategory = this.bucketSteps((step) => step.category);
    const totalSelfMs = sum([...byCategory.values()].map((b) => b.selfMs));
    const sorted = [...byCategory.entries()].sort((a, b) => b[1].selfMs - a[1].selfMs);
    const labelWidth = Math.max(...sorted.map(([category]) => category.length), 8);
    lines.push('', banner('e2e timing — step categories (self time)'));
    for (const [category, bucket] of sorted) {
      lines.push(
        `  ${category.padEnd(labelWidth)}  ${formatMs(bucket.selfMs).padStart(8)}` +
          `  ${share(bucket.selfMs, totalSelfMs)}  ${plural(bucket.count, 'call').padStart(11)}`
      );
    }
  }

  private appendSlowestOperations(lines: string[]): void {
    const byOperation = this.bucketSteps((step) => `${step.category} :: ${step.title}`);
    const totalSelfMs = sum([...byOperation.values()].map((b) => b.selfMs));
    const sorted = [...byOperation.entries()].sort((a, b) => b[1].selfMs - a[1].selfMs).slice(0, SLOWEST_OPERATIONS);
    const labelWidth = Math.min(80, Math.max(...sorted.map(([key]) => key.length)));
    lines.push('', banner(`e2e timing — top ${sorted.length} operations (self time)`));
    for (const [key, bucket] of sorted) {
      lines.push(
        `  ${truncate(key, labelWidth).padEnd(labelWidth)}` +
          `  ${formatMs(bucket.selfMs).padStart(8)}  ${share(bucket.selfMs, totalSelfMs)}` +
          `  ${plural(bucket.count, 'call').padStart(11)}` +
          `  mean ${formatMs(bucket.selfMs / bucket.count)}`
      );
    }
  }

  // ---------------------------------------------------------------- helpers

  private bucketSteps(keyOf: (step: StepRecord) => string): Map<string, StepBucket> {
    const buckets = new Map<string, StepBucket>();
    for (const step of this.steps) {
      const key = keyOf(step);
      const bucket = buckets.get(key) ?? { count: 0, selfMs: 0, totalMs: 0 };
      bucket.count += 1;
      bucket.selfMs += step.selfMs;
      bucket.totalMs += step.durationMs;
      buckets.set(key, bucket);
    }
    return buckets;
  }

  private writeJson(status: string): void {
    try {
      fs.mkdirSync(path.dirname(this.outputFile), { recursive: true });
      fs.writeFileSync(
        this.outputFile,
        JSON.stringify(
          {
            status,
            startedAt: new Date(this.startedAt).toISOString(),
            endedAt: new Date(this.endedAt).toISOString(),
            wallMs: this.endedAt - this.startedAt,
            tests: this.tests,
            steps: [...this.bucketSteps((step) => `${step.category} :: ${step.title}`).entries()]
              .map(([key, bucket]) => ({ operation: key, ...bucket }))
              .sort((a, b) => b.selfMs - a.selfMs)
          },
          null,
          2
        ),
        'utf-8'
      );
    } catch (error) {
      console.log(`e2e timing: could not write ${this.outputFile}: ${(error as Error).message}`);
    }
  }

  /** Playwright reports absolute paths; the report is easier to read with repo-relative ones. */
  private relativeFile(file: string): string {
    return path.relative(this.rootDir, file).split(path.sep).join('/');
  }
}

/**
 * Collapses the argument of a step title so that equivalent calls land in the same bucket, while
 * keeping the part that distinguishes them. `page.goto(http://localhost:4200/login)` becomes
 * `page.goto(/login)` — all logins group together but stay separable from other navigations —
 * and selector arguments are dropped entirely, because they are unique per call site and would
 * otherwise fragment `locator.click` into hundreds of one-hit rows.
 */
function normalizeStepTitle(title: string): string {
  const match = /^([\w.:]+)\((.*)\)$/s.exec(title.trim());
  if (!match) {
    return title.trim();
  }
  const [, api, argument] = match;
  const url = /^https?:\/\/[^/]+(\/\S*)?$/.exec(argument.trim());
  if (url) {
    return `${api}(${url[1] ?? '/'})`;
  }
  return `${api}()`;
}

function sum(values: number[]): number {
  return values.reduce((total, value) => total + value, 0);
}

/** `m:ss.s` above a minute, `s.s` below — durations here span 100 ms to 30 minutes. */
function formatMs(ms: number): string {
  const seconds = ms / 1000;
  if (seconds < 60) {
    return `${seconds.toFixed(1)}s`;
  }
  const minutes = Math.floor(seconds / 60);
  return `${minutes}:${(seconds - minutes * 60).toFixed(1).padStart(4, '0')}`;
}

function share(part: number, total: number): string {
  return total > 0 ? `${((part / total) * 100).toFixed(1).padStart(5)}%` : '    —';
}

function plural(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? '' : 's'}`;
}

function truncate(text: string, width: number): string {
  return text.length <= width ? text : `…${text.slice(text.length - width + 1)}`;
}

function banner(text: string): string {
  return `========== ${text} ==========`;
}
