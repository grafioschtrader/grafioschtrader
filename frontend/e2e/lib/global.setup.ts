/** Reject a wrong backend before the portable suite creates or changes any data. */
export default async function globalSetup(): Promise<void> {
  const backend = process.env.LIB_E2E_BACKEND_URL ?? 'http://localhost:8081';
  const url = `${backend}/api/integration-info`;
  let response: Response;
  try {
    response = await fetch(url);
  } catch (error) {
    throw new Error(
      `ABORTING LIB E2E: could not reach ${url}. Start grafiosch-test-integration with the e2e profile. `
      + `Underlying error: ${(error as Error).message}`,
    );
  }
  if (!response.ok) {
    throw new Error(`ABORTING LIB E2E: ${url} returned HTTP ${response.status}`);
  }
  const info = await response.json() as {activeProfiles?: string[]; databaseName?: string};
  if (info.databaseName !== 'grafiosch_t' || !info.activeProfiles?.includes('e2e')) {
    throw new Error(
      `ABORTING LIB E2E: expected profile e2e and database grafiosch_t, got `
      + `profiles=${JSON.stringify(info.activeProfiles)} database=${info.databaseName ?? ''}`,
    );
  }

  const mailApi = process.env.LIB_E2E_MAIL_API_URL ?? 'http://localhost:8025';
  try {
    const mailResponse = await fetch(`${mailApi}/api/v2/messages?limit=1`);
    if (!mailResponse.ok) {
      throw new Error(`HTTP ${mailResponse.status}`);
    }
  } catch (error) {
    throw new Error(
      `ABORTING LIB E2E: MailHog/Mailpit API is unavailable at ${mailApi}. `
      + `Underlying error: ${(error as Error).message}`,
    );
  }
}
