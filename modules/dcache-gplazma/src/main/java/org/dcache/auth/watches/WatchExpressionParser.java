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
import java.util.function.Predicate;
import org.dcache.auth.EmailAddressPrincipal;
import org.dcache.auth.GidPrincipal;
import org.dcache.auth.GroupNamePrincipal;
import org.dcache.auth.OidcSubjectPrincipal;
import org.dcache.auth.UidPrincipal;
import org.dcache.auth.UserNamePrincipal;
import org.dcache.auth.util.MatchingPrincipalPresent;
import org.globus.gsi.gssapi.jaas.GlobusPrincipal;
import org.parboiled.BaseParser;
import org.parboiled.Rule;
import org.parboiled.annotations.BuildParseTree;
import org.parboiled.errors.ParserRuntimeException;

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
        return sequence(andExpression(), zeroOrMore(orLiteral(), andExpression(), push(pop().or(pop()))));
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
        return sequence(term(), zeroOrMore(andLiteral(), term(), push(pop().and(pop()))));
    }

    Rule andLiteral() {
        return firstOf(
                    sequence(trie("and", "AND"), whiteSpace()),
                    sequence("&&", optionalWhiteSpace())
                );
    }

    Rule term() {
        return firstOf(
            sequence(notLiteral(), predicate(), push(pop().negate())),
            predicate());
    }

    Rule notLiteral() {
        return firstOf(
                sequence(trie("not", "NOT"), whiteSpace()),
                sequence(ch('!'), optionalWhiteSpace())
            );
    }

    Rule predicate() {
        // Initially limit ourselves to just <principal>:<literal> here.
        return sequence(principalType(), ch(':'), principalName(), optionalWhiteSpace(),
            push(pop().and(pop())));
    }

    Rule principalType() {
        return sequence(trie(TYPES_BY_LABEL.keySet()), push(hasType(match())));
    }

    Rule principalName() {
        return firstOf(
            sequence(
                ch('\''),
                zeroOrMore(noneOf("\'")),
                push(hasName(match())),
                ch('\'')
            ),
            sequence(
                ch('"'),
                zeroOrMore(firstOf(
                    sequence(ch('\\'), ANY),
                    noneOf("\"")
                )),
                push(hasName(unescape(match()))),
                ch('"')
            ),
            sequence(
                oneOrMore(noneOf(" \t")),
                push(hasName(match()))
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
    static PrincipalPredicate hasType(String label) {
        Class<? extends Principal> type = TYPES_BY_LABEL.get(label);
        MatchingPrincipalPresent hasPrincipalOfType = new MatchingPrincipalPresent(type::isInstance);
        return new PrincipalPredicate(hasPrincipalOfType);
    }

    @VisibleForTesting
    static PrincipalPredicate hasName(String name) {
        MatchingPrincipalPresent hasPrincipalWithName = new MatchingPrincipalPresent(p -> p.getName().equals(name));
        return new PrincipalPredicate(hasPrincipalWithName);
    }
}
