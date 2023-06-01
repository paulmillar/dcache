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

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.security.Principal;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Map;
import static java.util.Objects.requireNonNull;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.dcache.auth.BearerTokenCredential;
import org.dcache.auth.EmailAddressPrincipal;
import org.dcache.auth.GidPrincipal;
import org.dcache.auth.GroupNamePrincipal;
import org.dcache.auth.OidcSubjectPrincipal;
import org.dcache.auth.PasswordCredential;
import org.dcache.auth.UidPrincipal;
import org.dcache.auth.UserNamePrincipal;
import org.dcache.auth.util.DescriptivePredicate;
import static org.dcache.auth.util.DescriptivePredicate.decorate;
import org.dcache.auth.util.HasMatching;
import org.dcache.gplazma.util.CertPaths;
import org.dcache.gplazma.util.JsonWebToken;
import org.dcache.util.Glob;
import org.globus.gsi.gssapi.jaas.GlobusPrincipal;
import org.parboiled.BaseParser;
import org.parboiled.Rule;
import org.parboiled.annotations.BuildParseTree;
import org.parboiled.errors.ParserRuntimeException;
import org.parboiled.support.StringVar;

/**
 * A class responsible for parsing a watch expression.
 */
@BuildParseTree
public class WatchExpressionParser extends BaseParser<Predicate<LoginResultObservation>> {

    protected static final Map<String,Class<? extends Principal>> PRINCIPAL_TYPES_BY_LABEL = Map.of(
        "dn", GlobusPrincipal.class,
        "sub", OidcSubjectPrincipal.class,
        "email", EmailAddressPrincipal.class,
        "groupname", GroupNamePrincipal.class,
        "username", UserNamePrincipal.class,
        "uid", UidPrincipal.class,
        "gid", GidPrincipal.class);

    private static final Predicate<Object> IS_JWT_CREDENTIAL = c -> c instanceof BearerTokenCredential
                && JsonWebToken.isCompatibleFormat(((BearerTokenCredential)c).getToken());

    protected static final Map<String,Predicate<Object>> CREDENTIAL_TYPE_PREDICATE_BY_LABEL = Map.of(
        "in.jwt", IS_JWT_CREDENTIAL,
        "in.password", c -> c instanceof PasswordCredential,
        "in.x509", CertPaths::isX509CertPath
    );

    Rule input() {
        return sequence(orExpression(), EOI);
    }

    Rule orExpression() {
        return sequence(
            andExpression(),
            zeroOrMore(
                orLiteral(),
                andExpression(),
                push(pop().or(pop()))
            )
        );
    }

    Rule orLiteral() {
        return firstOf(
            sequence(trie("or", "OR"), whiteSpace()),
            sequence("||", optionalWhiteSpace())
        );
    }

    Rule optionalWhiteSpace() {
        return zeroOrMore(anyOf(" \t"));
    }

    Rule whiteSpace() {
        return oneOrMore(anyOf(" \t"));
    }

    Rule andExpression() {
        return sequence(negatable(), zeroOrMore(andLiteral(), negatable(), push(pop().and(pop()))));
    }

    Rule andLiteral() {
        return firstOf(
            sequence(trie("and", "AND"), whiteSpace()),
            sequence("&&", optionalWhiteSpace())
        );
    }

    Rule negatable() {
        return firstOf(
            sequence(notLiteral(), term(), push(pop().negate())),
            term()
        );
    }

    Rule term() {
        return firstOf(
            sequence(ch('('), optionalWhiteSpace(), orExpression(), ch(')'), optionalWhiteSpace()),
            predicate()
        );
    }

    Rule notLiteral() {
        return firstOf(
            sequence(trie("not", "NOT"), whiteSpace()),
            sequence(ch('!'), optionalWhiteSpace())
        );
    }

    Rule predicate() {
        return firstOf(
            principalTypeAndNamePredicate(),
            principalTypePredicate(),
            credentialTypeAndIntrospection(),
            credentialTypePredicate()
            /* TODO Add predicates for:
                login-attributes,
                overall login result (success/failure)
                whether a phase ran (and with what result).
                whether a plugin ran (and with what results).
            */
        );
    }

    Rule credentialTypeAndIntrospection() {
        StringVar credentialType = new StringVar();

        return sequence(
                string("in.jwt WITH "),
                parseJwtCredentialIntrospection()
            );

        /* For when supporting introspection on multiple credential types
        return firstOf(
            sequence(
                string("jwt."),
                parseJwtCredentialIntrospection()
            ),
            sequence(
                string("x509."),
                parseX509CredentialIntrospection()
            ),
            sequence(
                string("password."),
                parsePasswordCredentialIntrospection()
            ));*/
    }

    Rule parseJwtCredentialIntrospection() {
        StringVar claimNameCapture = new StringVar();
        StringVar claimValueCapture = new StringVar();

        return firstOf(
            sequence( // Match claim exists and has a matching string value
                oneOrMore(noneOf("/~: &|")),
                claimNameCapture.set(match()),

                firstOf(
                    sequence(
                        ch(':'),
                        stringLiteral(claimValueCapture),
                        optionalWhiteSpace(),
                        push(hasJwtClaimWithExactStringValue(claimNameCapture.get(), claimValueCapture.get()))
                    ),
                    sequence(
                        ch('~'),
                        stringLiteral(claimValueCapture),
                        optionalWhiteSpace(),
                        push(hasJwtClaimWithValueMatchingGlob(claimNameCapture.get(), claimValueCapture.get()))
                    ),
                    sequence(
                        ch('/'),
                        zeroOrMore(noneOf("/")), // REVISIT what if we want '/' in the RE?
                        push(hasJwtClaimWithValueMatchingRegularExpression(claimNameCapture.get(), match())),
                        ch('/'),
                        optionalWhiteSpace()
                    )
                )
            ),
            sequence( // Check existence of claim, ignoring the value
                oneOrMore(noneOf(" &|")),
                claimNameCapture.set(match()),
                push(hasJwtClaim(claimNameCapture.get()))
            ));
    }

    Rule credentialTypePredicate() {
        StringVar credentialType = new StringVar();
        return sequence(
            credentialType(credentialType),
            optionalWhiteSpace(),
            push(hasCredentialOfType(credentialType.get()))
        );
    }

    Rule credentialType(StringVar type) {
        return sequence(trie(CREDENTIAL_TYPE_PREDICATE_BY_LABEL.keySet()), type.set(match()));
    }

    Rule principalTypePredicate() {
        StringVar principalType = new StringVar();
        return sequence(
            principalType(principalType),
            optionalWhiteSpace(),
            push(hasType(principalType.get()))
        );
    }

    Rule principalTypeAndNamePredicate() {
        StringVar principalType = new StringVar();
        StringVar principalName = new StringVar();

        return sequence(
            principalType(principalType),
            firstOf(
                sequence(
                    ch(':'),
                    stringLiteral(principalName),
                    optionalWhiteSpace(),
                    push(hasTypeAndExactName(principalType.get(), principalName.get()))
                ),
                sequence(
                    ch('~'),
                    stringLiteral(principalName),
                    optionalWhiteSpace(),
                    push(hasTypeAndGlobMatchingName(principalType.get(), principalName.get()))
                ),
                sequence(
                    ch('/'),
                    zeroOrMore(noneOf("/")), // REVISIT what if we want '/' in the RE?
                    push(hasTypeAndRegExpMatchingName(principalType.get(), match())),
                    ch('/'),
                    optionalWhiteSpace()
                )
            )
        );
    }

    Rule principalType(StringVar principalType) {
        return sequence(trie(PRINCIPAL_TYPES_BY_LABEL.keySet()), principalType.set(match()));
    }

    Rule stringLiteral(StringVar principalName) {
        return firstOf(
            sequence(
                ch('\''),
                zeroOrMore(noneOf("\'")),
                principalName.set(match()),
                ch('\'')
            ),
            sequence(
                ch('"'),
                zeroOrMore(firstOf(
                    sequence(ch('\\'), ANY),
                    noneOf("\"")
                )),
                principalName.set(unescape(match())),
                ch('"')
            ),
            sequence(
                oneOrMore(noneOf(" \t)&|")),
                principalName.set(match())
            )
        );
    }

    @VisibleForTesting
    static String unescape(String input) {
        StringBuilder sb = new StringBuilder();
        CharacterIterator ci = new StringCharacterIterator(input);
        char c = ci.first();
        boolean slash = false;
        while (c != CharacterIterator.DONE) {
            if (slash) {
                slash = false;
                switch (c) {
                case 't':
                    sb.append('\t'); // tab
                    break;
                case 'b':
                    sb.append('\b'); // backspace
                    break;
                case 'n':
                    sb.append('\n'); // new line
                    break;
                case 'r':
                    sb.append('\r'); // carriage return
                    break;
                case 'f':
                    sb.append('\f'); // form feed
                    break;
                case '\'':
                    sb.append('\''); // single quote
                    break;
                case '\"':
                    sb.append('\"'); // double quote
                    break;
                case '\\':
                    sb.append('\\'); // double quote
                    break;
                case '/':
                    sb.append('/'); // forward slash
                    break;
                default:
                    throw new ParserRuntimeException("Bad escape sequence \"\\" + c + "\" inside double-quote text");
                }
            } else {
                if (c == '\\') {
                    slash = true;
                } else {
                    sb.append(c);
                }
            }
            c = ci.next();

        }
        return sb.toString();
    }

    @VisibleForTesting
    static CredentialPredicate hasCredentialOfType(String typeLabel) {
        Predicate<Object> check = CREDENTIAL_TYPE_PREDICATE_BY_LABEL.get(typeLabel);
        if (check == null) {
            throw new ParserRuntimeException("Unknown principal type \"" + typeLabel + "\"");
        }
        var predicate = decorate(check)
                .withDescription("door supplied a " + typeLabel);
        var hasPrincipalOfType = new HasMatching(predicate);
        return new CredentialPredicate(hasPrincipalOfType);
    }

    @VisibleForTesting
    static CredentialPredicate hasJwtClaim(String claimName) {
        Predicate<Object> check = IS_JWT_CREDENTIAL.and(c -> {
                try {
                    var jwt = new JsonWebToken(((BearerTokenCredential)c).getToken());
                    return jwt.getPayloadValueAsString(claimName)
                        .map(s -> Boolean.TRUE)
                        .orElse(Boolean.FALSE);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        var predicate = decorate(check)
                .withDescription("door supplied a jwt with claim" + claimName);
        var hasCredentialOfType = new HasMatching(predicate);
        return new CredentialPredicate(hasCredentialOfType);
    }

    static CredentialPredicate hasJwtClaimWithExactStringValue(String claimName, String claimValue) {
        Predicate<Object> check = IS_JWT_CREDENTIAL.and(c -> {
                try {
                    var jwt = new JsonWebToken(((BearerTokenCredential)c).getToken());
                    return jwt.getPayloadString(claimName)
                        .map(value -> value.equals(claimValue))
                        .orElse(Boolean.FALSE);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        var predicate = decorate(check)
                .withDescription("door supplied a jwt with claim" + claimName + " with value \"" + claimValue + "\"");
        var hasCredentialOfType = new HasMatching(predicate);
        return new CredentialPredicate(hasCredentialOfType);
    }

    static CredentialPredicate hasJwtClaimWithValueMatchingGlob(String claimName, String globPattern) {
        Pattern pattern = new Glob(globPattern).toPattern();
        Predicate<Object> check = IS_JWT_CREDENTIAL.and(c -> {
                try {
                    var jwt = new JsonWebToken(((BearerTokenCredential)c).getToken());
                    return jwt.getPayloadString(claimName)
                        .map(value -> pattern.matcher(value).matches())
                        .orElse(Boolean.FALSE);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        var predicate = decorate(check)
                .withDescription("door supplied a jwt with claim" + claimName + " with value matching glob \"" + globPattern + "\"");
        var hasCredentialOfType = new HasMatching(predicate);
        return new CredentialPredicate(hasCredentialOfType);
    }

    static CredentialPredicate hasJwtClaimWithValueMatchingRegularExpression(String claimName, String rePattern) {
        Pattern pattern = Pattern.compile(rePattern);
        Predicate<Object> check = CREDENTIAL_TYPE_PREDICATE_BY_LABEL.get("in.jwt")
            .and(c -> {
                try {
                    var jwt = new JsonWebToken(((BearerTokenCredential)c).getToken());
                    return jwt.getPayloadString(claimName)
                        .map(value -> pattern.matcher(value).matches())
                        .orElse(Boolean.FALSE);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        var predicate = decorate(check)
                .withDescription("door supplied a jwt with claim" + claimName + " with value matching RE \"" + rePattern + "\"");
        var hasCredentialOfType = new HasMatching(predicate);
        return new CredentialPredicate(hasCredentialOfType);
    }

    @VisibleForTesting
    static PrincipalPredicate hasType(String typeLabel) {
        Class<? extends Principal> type = PRINCIPAL_TYPES_BY_LABEL.get(typeLabel);
        if (type == null) {
            throw new ParserRuntimeException("Unknown principal type \"" + typeLabel + "\"");
        }
        var predicate = decorate((Principal p) -> type.isInstance(p))
                .withDescription("is " + typeLabel);
        var hasPrincipalOfType = new HasMatching(predicate);
        return new PrincipalPredicate(hasPrincipalOfType);
    }

    @VisibleForTesting
    static PrincipalPredicate hasTypeAndExactName(String typeLabel, String name) {
        Class<? extends Principal> type = PRINCIPAL_TYPES_BY_LABEL.get(typeLabel);
        if (type == null) {
            throw new ParserRuntimeException("Unknown principal type \"" + typeLabel + "\"");
        }
        requireNonNull(name, "hasName with null argument");
        var predicate = decorate((Principal p) -> type.isInstance(p) && p.getName().equals(name))
                .withDescription("is " + typeLabel + " and name is \"" + name + "\"");
        var hasPrincipalWithName = new HasMatching(predicate);
        return new PrincipalPredicate(hasPrincipalWithName);
    }

    @VisibleForTesting
    static PrincipalPredicate hasTypeAndGlobMatchingName(String typeLabel, String globPattern) {
        Class<? extends Principal> type = PRINCIPAL_TYPES_BY_LABEL.get(typeLabel);
        if (type == null) {
            throw new ParserRuntimeException("Unknown principal type \"" + typeLabel + "\"");
        }
        requireNonNull(globPattern, "hasGlobMatchingName with null argument");
        return hasTypeAndMatchingName("is " + typeLabel + " and name matching glob \""
            + globPattern + "\"", type, new Glob(globPattern).toPattern());
    }

    @VisibleForTesting
    static PrincipalPredicate hasTypeAndRegExpMatchingName(String typeLabel, String pattern) {
        Class<? extends Principal> type = PRINCIPAL_TYPES_BY_LABEL.get(typeLabel);
        if (type == null) {
            throw new ParserRuntimeException("Unknown principal type \"" + typeLabel + "\"");
        }
        requireNonNull(pattern, "hasRegExpMatchingName with null argument");
        try {
            return hasTypeAndMatchingName("is " + typeLabel + " and name matching regular"
                + " expression \"" + pattern + "\"", type, Pattern.compile(pattern));
        } catch (PatternSyntaxException e) {
            throw new ParserRuntimeException("Bad regular expression \"" + pattern + "\": "
                + e.getMessage());
        }
    }

    @VisibleForTesting
    static PrincipalPredicate hasTypeAndMatchingName(String description, Class<? extends Principal> type, Pattern pattern) {
        var predicate = decorate((Principal p) -> type.isInstance(p) && pattern.matcher(p.getName()).matches())
                .withDescription(description);
        var hasPrincipalWithName = new HasMatching(predicate);
        return new PrincipalPredicate(hasPrincipalWithName);
    }

}
