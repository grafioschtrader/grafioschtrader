package grafioschtrader.mcp;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

import grafiosch.types.AccessTokenScope;
import grafiosch.types.Language;

/**
 * Everything a tool needs to know about who calls it, built once when the tool is entered and passed down to every
 * report. The tool beans are singletons and never hold a user object themselves.
 *
 * @param idUser            the owner of the access token
 * @param idTenant          the home tenant of that user; a token never reaches another tenant
 * @param idUserAccessToken the token that authenticated the request
 * @param scope             what the token may do
 * @param locale            the locale of the user, for translated messages
 * @param language          the language of the user, for multilingual names
 * @param localeStr         the locale string the reports expect
 * @param today             the current day in the client's time zone
 * @param zoneId            the client's time zone, from the header {@code x-time-zone}, otherwise UTC
 */
public record McpCaller(Integer idUser, Integer idTenant, Integer idUserAccessToken, AccessTokenScope scope,
    Locale locale, Language language, String localeStr, LocalDate today, ZoneId zoneId) {
}
