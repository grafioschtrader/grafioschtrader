import { loginAsFixtureUser } from './helpers';
import { entityLimitScenarios } from './lib/entity-limit.scenarios';

entityLimitScenarios(loginAsFixtureUser);
