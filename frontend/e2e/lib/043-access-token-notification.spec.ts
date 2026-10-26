import { libHelpers } from './helpers';
import { accessTokenScenarios } from './access-token.scenarios';

accessTokenScenarios(libHelpers.loginAsFixtureUser, false);
