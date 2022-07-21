/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2022 Deutsches Elektronen-Synchrotron
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
package org.dcache.gplazma.configuration;

import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Path;
import java.util.function.Function;

import org.dcache.gplazma.configuration.parser.ConfigurationParser;
import org.dcache.gplazma.configuration.parser.ConfigurationParserFactory;
import org.dcache.gplazma.configuration.parser.FactoryConfigurationException;
import org.dcache.gplazma.configuration.parser.ParseException;
import org.dcache.util.Result;

/**
 * Simple wrapper class that converts the (gPlazma) ConfigurationParser to the
 * equivalent parser for ParsableFile.
 */
public class ConfigurationParserBackedParser
        implements Function<Path, Result<Configuration,String>> {

    private final ConfigurationParserFactory parserFactory;

    public ConfigurationParserBackedParser() {
        ConfigurationParserFactory factory;
        try {
            factory = ConfigurationParserFactory.getInstance();
        } catch (FactoryConfigurationException e) {
            factory = new ConfigurationParserFactory(){
                @Override
                public ConfigurationParser newConfigurationParser() {
                    return new ConfigurationParser() {
                        @Override
                        public Configuration parse(String configuration) throws ParseException {
                            throw new ParseException(e.getMessage(), e);
                        }

                        @Override
                        public Configuration parse(File configurationFile) throws ParseException {
                            throw new ParseException(e.getMessage(), e);
                        }

                        @Override
                        public Configuration parse(BufferedReader bufferedReader) throws ParseException {
                            throw new ParseException(e.getMessage(), e);
                        }
                    };
                }
            };
        }
        parserFactory = factory;
    }

    @Override
    public Result<Configuration, String> apply(Path target)
    {
        ConfigurationParser parser = parserFactory.newConfigurationParser();

        try {
            Configuration config = parser.parse(target.toFile());
            return Result.success(config);
        } catch (ParseException e) {
            return Result.failure(e.getMessage());
        }
    }
}
