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
import java.security.Principal;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Map;
import static java.util.Objects.requireNonNull;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.dcache.auth.EmailAddressPrincipal;
import org.dcache.auth.GidPrincipal;
import org.dcache.auth.GroupNamePrincipal;
import org.dcache.auth.OidcSubjectPrincipal;
import org.dcache.auth.UidPrincipal;
import org.dcache.auth.UserNamePrincipal;
import org.dcache.auth.util.DescriptivePredicate;
import static org.dcache.auth.util.DescriptivePredicate.decorate;
import org.dcache.auth.util.MatchingPrincipalPresent;
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

    protected static final Map<String,Class<? extends Principal>> TYPES_BY_LABEL = Map.of(
        "dn", GlobusPrincipal.class,
        "sub", OidcSubjectPrincipal.class,
        "email", EmailAddressPrincipal.class,
        "groupname", GroupNamePrincipal.class,
        "username", UserNamePrincipal.class,
        "uid", UidPrincipal.class,
        "gid", GidPrincipal.class);

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
        StringVar principalType = new StringVar();
        StringVar principalName = new StringVar();

        return sequence(
            principalType(principalType),
            firstOf(
                sequence(
                    ch(':'),
                    principalName(principalName),
                    optionalWhiteSpace(),
                    push(hasTypeAndExactName(principalType.get(), principalName.get()))
                ),
                sequence(
                    ch('~'),
                    principalName(principalName),
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
        return sequence(trie(TYPES_BY_LABEL.keySet()), principalType.set(match()));
    }

    Rule principalName(StringVar principalName) {
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
    static PrincipalPredicate hasTypeAndExactName(String typeLabel, String name) {
        Class<? extends Principal> type = TYPES_BY_LABEL.get(typeLabel);
        if (type == null) {
            throw new ParserRuntimeException("Unknown principal type \"" + typeLabel + "\"");
        }
        requireNonNull(name, "hasName with null argument");
        var predicate = decorate((Principal p) -> type.isInstance(p) && p.getName().equals(name))
                .withDescription("is " + typeLabel + " and name is \"" + name + "\"");
        MatchingPrincipalPresent hasPrincipalWithName = new MatchingPrincipalPresent(predicate);
        return new PrincipalPredicate(hasPrincipalWithName);
    }

    @VisibleForTesting
    static PrincipalPredicate hasTypeAndGlobMatchingName(String typeLabel, String globPattern) {
        Class<? extends Principal> type = TYPES_BY_LABEL.get(typeLabel);
        if (type == null) {
            throw new ParserRuntimeException("Unknown principal type \"" + typeLabel + "\"");
        }
        requireNonNull(globPattern, "hasGlobMatchingName with null argument");
        return hasTypeAndMatchingName("is " + typeLabel + " and name matching glob \""
            + globPattern + "\"", type, new Glob(globPattern).toPattern());
    }

    @VisibleForTesting
    static PrincipalPredicate hasTypeAndRegExpMatchingName(String typeLabel, String pattern) {
        Class<? extends Principal> type = TYPES_BY_LABEL.get(typeLabel);
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
        MatchingPrincipalPresent hasPrincipalWithName = new MatchingPrincipalPresent(predicate);
        return new PrincipalPredicate(hasPrincipalWithName);
    }

}
