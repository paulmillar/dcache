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

import org.parboiled.BaseParser;
import org.parboiled.Rule;
import org.parboiled.annotations.BuildParseTree;

/**
 * A class responsible for parsing a watch expression.
 */
@BuildParseTree
public class WatchExpressionParser extends BaseParser<Object> {

    Rule input() {
        return sequence(orExpression(), EOI);
    }

    Rule orExpression() {
        return sequence(andExpression(), zeroOrMore(orLiteral(), andExpression()));
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
        return sequence(term(), zeroOrMore(andLiteral(), term()));
    }

    Rule andLiteral() {
        return firstOf(
                    sequence(trie("and", "AND"), whiteSpace()),
                    sequence("&&", optionalWhiteSpace())
                );
    }

    Rule term() {
        return sequence(optional(notLiteral()), predicate());
    }

    Rule notLiteral() {
        return firstOf(
                sequence(trie("not", "NOT"), whiteSpace()),
                sequence(ch('!'), optionalWhiteSpace())
            );
    }

    Rule predicate() {
        // Initially limit ourselves to just <principal>:<literal> here.
        return sequence(principalType(), ch(':'), simpleWord(), optionalWhiteSpace());
    }

    Rule principalType() {
        return trie("dn", "sub", "email", "groupname", "username", "uid", "gid");
    }

    Rule simpleWord() {
        return oneOrMore(noneOf(" \t"));
    }
}
