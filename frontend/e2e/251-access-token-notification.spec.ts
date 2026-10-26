import { loginAsFixtureUser } from './helpers';
import { accessTokenScenarios } from './lib/access-token.scenarios';

accessTokenScenarios(loginAsFixtureUser, true);
