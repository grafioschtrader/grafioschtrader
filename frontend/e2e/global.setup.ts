/**
 * Playwright global setup. Runs exactly once before any project (including the 'setup' project that registers
 * e2e users). Verifies that the backend is connected to the expected test database (grafioschtrader_t) by
 * calling the public /api/gtinfo endpoint. Throwing here aborts the whole run cleanly, before any browser
 * context is created.
 *
 * The backend URL can be overridden with the E2E_BACKEND_URL environment variable; default is localhost:8080.
 */
const EXPECTED_DB = 'grafioschtrader_t';

export default async function globalSetup(): Promise<void> {
  const base = process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';
  const url = `${base}/api/gtinfo`;

  let response: Response;
  try {
    response = await fetch(url);
  } catch (err) {
    throw new Error(
      `ABORTING E2E: could not reach backend at ${url}. ` +
      `Start it with:  mvn -pl grafioschtrader-server spring-boot:test-run -Dspring-boot.run.profiles=e2e\n` +
      `Underlying error: ${(err as Error).message}`,
    );
  }

  if (!response.ok) {
    throw new Error(`ABORTING E2E: ${url} returned HTTP ${response.status}`);
  }

  const info = await response.json() as { activeProfile?: string; databaseName?: string };

  if (info.databaseName !== EXPECTED_DB) {
    throw new Error(
      `ABORTING E2E: backend is connected to database '${info.databaseName ?? ''}' ` +
      `(activeProfile='${info.activeProfile ?? ''}'), expected '${EXPECTED_DB}'. ` +
      `Restart backend with:  mvn -pl grafioschtrader-server spring-boot:test-run -Dspring-boot.run.profiles=e2e`,
    );
  }
}
