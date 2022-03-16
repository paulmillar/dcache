/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2023 Deutsches Elektronen-Synchrotron
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.dcache.auth.watches;

import java.util.function.Predicate;
import org.dcache.gplazma.monitor.LoginResult;

/**
 * A class that describes which LoginResults are of interest.  It is based on a single-line
 * text input that describes which parts are of interest.
 */
class LoginResultMatcher implements Predicate<LoginResultObservation> {

  private final String description;

  private LoginResultMatcher(String input) {
    this.description = input; // truncate input if too long.
    /*
    <EXP> = <PREDICATE>
    <PREDICATE> = "(" <PREDICATE> ")"
    |  <PREDICATE> "&&" <PREDICATE>
    |  <PREDICATE> "||" <PREDICATE>
    |  "!" <PREDICATE>
    <PREDICATE> = "in" <IN_PREDICATE>
    |  "auth" <AUTH_PREDICATE>
    |  "auth." <PLUGIN> <AUTH_PLUGIN_PREDICATE>
    |  ...
    <IN_PREDICATE> = "HAS" <CREDENTIAL_PREDICATE> -- true iff one credential matches
    | "NO" <CREDENTIAL_PREDICATE> -- true if no credential matches
    <CREDENTIAL_PREDICATE> = "(" <CREDENTIAL_PREDICATE> ")"
    | <CREDENTIAL_PREDICATE> "&&" <CREDENTIAL_PREDICATE>
    | <CREDENTIAL_PREDICATE> "||" <CREDENTIAL_PREDICATE>
    | "!" <CREDENTIAL_PREDICATE>
    | "X.509" ( "WITH" <X509_CREDENTIAL_PREDICATE> )?
    | "token" ( "WITH" <TOKEN_CREDENTIAL_PREDICATE> )?
    | "JWT" ( "WITH" <JWT_CREDENTIAL_PREDICATE> )?
    | "username+password" ( "WITH" <USERNAME_CREDENTIAL_PREDICATE> )?
    <X509_CREDENTIAL_TYPE_PREDICATE> = "(" <X509_CREDENTIAL_TYPE_PREDICATE> ")"
    | <X509_CREDENTIAL_TYPE_PREDICATE> "&&" <X509_CREDENTIAL_TYPE_PREDICATE>
    | <X509_CREDENTIAL_TYPE_PREDICATE> "||" <X509_CREDENTIAL_TYPE_PREDICATE>
    | !<X509_CREDENTIAL_TYPE_PREDICATE>
    | "subject" <STRING_PREDICATE>
    | "issuer" <STRING_PREDICATE>
    | "embargoed"              -- true if notBefore is in the future
    | "expired" (!expired)     -- true if notAfter is in the past.
    | "good" (equivaletnt to "!emabrgeod && !expired")
    | "policy" x.y.z
    | "extension" x.y.x
    | "SAN" <STRING_PREDICATE>
    | "voms_proxy" ( "WITH" <VOMS_PROXY_PREDICATE> )?
    <VOMS_PROXY_PREDICATE> = "embargeod"
    | "expired"
    | "good" (equivaletnt to "!emabrgeod && !expired")
    | "vo" <STRING_PREDICATE>
    | "fqan" <STRING_PREDICATE>
    BearerToken_CREDENTIAL_TYPE_PREDICATE = <STRING_PREDICATE>
    JWT_CREDENTIAL_TYPE_PREDICATE =
    claim.X             -- true if claim X is defined
    | claim.X IS A String|Boolean|Null|Number|Object
    | claim.X <BOOLEAN_PREDICATE>
    | claim.X <INTEGER_PREDICATE>
    | claim.X <STRING_PREDICATE> -- only for String
    | signature <STRING_LITERAL>   -- algorithm used for signature (do we need this?  Isn't it a header.alg value?)
    | header.X            -- true if header contains claim X
    | header.X IS A String|Boolean|..
    | header.X <STRING_PREDICATE>
    | header.X LIKE <GLOB>
    | header.X ~= <RE>
    <BOOLEAN_PREDICATE> = IS true | false
    <INTEGER_PREDICATE> = == <VALUE>
    === "result" overall result of login.===
    result = <RESULT_PREDICATE>  -- match logins with this result.
    <RESULT_PREDICATE> = SUCCEEDS | FAILS
    === "auth" targing the Authentication phase ===
    "auth" is the Auth phase, in general
    "auth.oidc" is all oidc plugins in the Auth phase (true if any plugin matches)
    "auth.oidc[1]" is the first oidc plugin in the Auth phase
    "auth.oidc[2]" is the second oidc plugin in the Auth phase
    "auth EMITS <PRINCIPAL_PREDICATE>  -- true if at least one principal matches
    "auth.oidc EMIT <PRINCIPAL_PREDICATE>" -- true if plugin emits at least one principal matching.
    "auth.oidc <RESULT_PREDICATE>"
    "auth.oidc FAILS WITH <STRING_PREDICATE>" -- true if plugin fails with matching error message
    "auth EMITS { <PRINCPAL_PREDICATE_1>, <PRINCIPAL_PREDICATE_2> } -- true if each predicate matches.
    "auth <RESULT_PREDICATE>"
    <STRING_PREDICATE>
    "== <STRING_LITERAL>"
    "LIKE <STRING_LITERAL>"
    "~= <STRING_LITERAL>"
    <STRING_LITERAL>
    ' <TEXT_WITH_OUT_SINGLE_QUOTE> '
    " <BACKSLASH_MARKUP_TEXT> "
    <BACKSLASH_MARKUP_TEXT>
    <PrincipalPredicate>
    "dn"             any DN principal,
    "dn == <VALUE>"  a DN principal matching string exactly,
    "dn LIKE <GLOB>" a DN principal matching glob pattern,
    "dn ~= <RE>"     a DN principal matching regular expression.
    <VALUE> <GLOB> <RE> can be bare word or in quote marks.
    <AttributePredicate>
    "root"  emits any root directory attribute
    "home"  emits any home directory attribute
    "root = <VALUE>" exact match.
    login operations
    foo AND bar  True iff foo is true and bar is true.
    foo OR bar   True if foo is true or bar is true.
    !foo         True iff foo is false.
    parenthesis
    foo AND (bar OR !baz)
    implied
    foo --> bar  Equivalent to (!foo OR bar)
    !(foo --> bar)  Equivalent to (foo AND !bar)
    foo -|-> bar  Equivalent to !(foo --> bar), equvalent to (foo AND !bar)
    (A AND B ...) -/-> bar  Equivalent to !(foo --> bar), equvalent to (foo AND !bar)
    EXAMPLE:
    auth EMITS dn && !(session EMITS root like /peter/**)
    in HAS x.509 && !(session EMITS home)
     */
    /* parse input.  Language for building this based on:
    credentials presented?
    For each phase:
    for each plugin:
    name of plugin
    control of plugin
    result of plugin
    error of plugin
    <phase-specific-plugin-criteria>
    aggregate changes to principals over this phase.
    result of phase
    did the phase happen?
    <phase-specific-criteria>
    validation successful?
    login result: PASS/FAIL
    login attriutes (or is this <phase-specific-criteria> for SessionPhase?)
     */
  } // truncate input if too long.
  /*
  <EXP> = <PREDICATE>
  <PREDICATE> = "(" <PREDICATE> ")"
  |  <PREDICATE> "&&" <PREDICATE>
  |  <PREDICATE> "||" <PREDICATE>
  |  "!" <PREDICATE>
  <PREDICATE> = "in" <IN_PREDICATE>
  |  "auth" <AUTH_PREDICATE>
  |  "auth." <PLUGIN> <AUTH_PLUGIN_PREDICATE>
  |  ...
  <IN_PREDICATE> = "HAS" <CREDENTIAL_PREDICATE> -- true iff one credential matches
  | "NO" <CREDENTIAL_PREDICATE> -- true if no credential matches
  <CREDENTIAL_PREDICATE> = "(" <CREDENTIAL_PREDICATE> ")"
  | <CREDENTIAL_PREDICATE> "&&" <CREDENTIAL_PREDICATE>
  | <CREDENTIAL_PREDICATE> "||" <CREDENTIAL_PREDICATE>
  | "!" <CREDENTIAL_PREDICATE>
  | "X.509" ( "WITH" <X509_CREDENTIAL_PREDICATE> )?
  | "token" ( "WITH" <TOKEN_CREDENTIAL_PREDICATE> )?
  | "JWT" ( "WITH" <JWT_CREDENTIAL_PREDICATE> )?
  | "username+password" ( "WITH" <USERNAME_CREDENTIAL_PREDICATE> )?
  <X509_CREDENTIAL_TYPE_PREDICATE> = "(" <X509_CREDENTIAL_TYPE_PREDICATE> ")"
  | <X509_CREDENTIAL_TYPE_PREDICATE> "&&" <X509_CREDENTIAL_TYPE_PREDICATE>
  | <X509_CREDENTIAL_TYPE_PREDICATE> "||" <X509_CREDENTIAL_TYPE_PREDICATE>
  | !<X509_CREDENTIAL_TYPE_PREDICATE>
  | "subject" <STRING_PREDICATE>
  | "issuer" <STRING_PREDICATE>
  | "embargoed"              -- true if notBefore is in the future
  | "expired" (!expired)     -- true if notAfter is in the past.
  | "good" (equivaletnt to "!emabrgeod && !expired")
  | "policy" x.y.z
  | "extension" x.y.x
  | "SAN" <STRING_PREDICATE>
  | "voms_proxy" ( "WITH" <VOMS_PROXY_PREDICATE> )?
  <VOMS_PROXY_PREDICATE> = "embargeod"
  | "expired"
  | "good" (equivaletnt to "!emabrgeod && !expired")
  | "vo" <STRING_PREDICATE>
  | "fqan" <STRING_PREDICATE>
  BearerToken_CREDENTIAL_TYPE_PREDICATE = <STRING_PREDICATE>
  JWT_CREDENTIAL_TYPE_PREDICATE =
  claim.X             -- true if claim X is defined
  | claim.X IS A String|Boolean|Null|Number|Object
  | claim.X <BOOLEAN_PREDICATE>
  | claim.X <INTEGER_PREDICATE>
  | claim.X <STRING_PREDICATE> -- only for String
  | signature <STRING_LITERAL>   -- algorithm used for signature (do we need this?  Isn't it a header.alg value?)
  | header.X            -- true if header contains claim X
  | header.X IS A String|Boolean|..
  | header.X <STRING_PREDICATE>
  | header.X LIKE <GLOB>
  | header.X ~= <RE>
  <BOOLEAN_PREDICATE> = IS true | false
  <INTEGER_PREDICATE> = == <VALUE>
  === "result" overall result of login.===
  result = <RESULT_PREDICATE>  -- match logins with this result.
  <RESULT_PREDICATE> = SUCCEEDS | FAILS
  === "auth" targing the Authentication phase ===
  "auth" is the Auth phase, in general
  "auth.oidc" is all oidc plugins in the Auth phase (true if any plugin matches)
  "auth.oidc[1]" is the first oidc plugin in the Auth phase
  "auth.oidc[2]" is the second oidc plugin in the Auth phase
  "auth EMITS <PRINCIPAL_PREDICATE>  -- true if at least one principal matches
  "auth.oidc EMIT <PRINCIPAL_PREDICATE>" -- true if plugin emits at least one principal matching.
  "auth.oidc <RESULT_PREDICATE>"
  "auth.oidc FAILS WITH <STRING_PREDICATE>" -- true if plugin fails with matching error message
  "auth EMITS { <PRINCPAL_PREDICATE_1>, <PRINCIPAL_PREDICATE_2> } -- true if each predicate matches.
  "auth <RESULT_PREDICATE>"
  <STRING_PREDICATE>
  "== <STRING_LITERAL>"
  "LIKE <STRING_LITERAL>"
  "~= <STRING_LITERAL>"
  <STRING_LITERAL>
  ' <TEXT_WITH_OUT_SINGLE_QUOTE> '
  " <BACKSLASH_MARKUP_TEXT> "
  <BACKSLASH_MARKUP_TEXT>
  <PrincipalPredicate>
  "dn"             any DN principal,
  "dn == <VALUE>"  a DN principal matching string exactly,
  "dn LIKE <GLOB>" a DN principal matching glob pattern,
  "dn ~= <RE>"     a DN principal matching regular expression.
  <VALUE> <GLOB> <RE> can be bare word or in quote marks.
  <AttributePredicate>
  "root"  emits any root directory attribute
  "home"  emits any home directory attribute
  "root = <VALUE>" exact match.
  login operations
  foo AND bar  True iff foo is true and bar is true.
  foo OR bar   True if foo is true or bar is true.
  !foo         True iff foo is false.
  parenthesis
  foo AND (bar OR !baz)
  implied
  foo --> bar  Equivalent to (!foo OR bar)
  !(foo --> bar)  Equivalent to (foo AND !bar)
  foo -|-> bar  Equivalent to !(foo --> bar), equvalent to (foo AND !bar)
  (A AND B ...) -/-> bar  Equivalent to !(foo --> bar), equvalent to (foo AND !bar)
  EXAMPLE:
  auth EMITS dn && !(session EMITS root like /peter/**)
  in HAS x.509 && !(session EMITS home)
   */
  /* parse input.  Language for building this based on:
  credentials presented?
  For each phase:
  for each plugin:
  name of plugin
  control of plugin
  result of plugin
  error of plugin
  <phase-specific-plugin-criteria>
  aggregate changes to principals over this phase.
  result of phase
  did the phase happen?
  <phase-specific-criteria>
  validation successful?
  login result: PASS/FAIL
  login attriutes (or is this <phase-specific-criteria> for SessionPhase?)
   */

  public String toString() {
    return description;
  }

  @Override
  public boolean test(LoginResultObservation t) {
    return false; // FIXME: should be selective, based on input.
  }
}
