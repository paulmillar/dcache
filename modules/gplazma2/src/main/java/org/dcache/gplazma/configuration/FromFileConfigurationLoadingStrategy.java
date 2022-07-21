package org.dcache.gplazma.configuration;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import org.dcache.gplazma.configuration.parser.ParseException;
import org.dcache.util.Result;
import org.dcache.util.files.ParsableFile;

/**
 * This loading strategy loads the configuration from file, if file has been updated. This class is
 * not thread safe.
 *
 * @author timur
 */
public class FromFileConfigurationLoadingStrategy
      implements ConfigurationLoadingStrategy {

    private final ParsableFile configurationFile;
    private Result<Configuration,String> lastResult;

    public FromFileConfigurationLoadingStrategy(String configurationFileName) {
        checkArgument(configurationFileName != null && !configurationFileName.isBlank(),
                  "configuration file argument wasn't specified correctly");

        Path path = FileSystems.getDefault().getPath(configurationFileName);
        checkArgument(Files.exists(path),
                  "configuration file does not exists at %s", configurationFileName);

        configurationFile = new ParsableFile(new ConfigurationParserBackedParser(), path);

        lastResult = configurationFile.get();
    }

    /**
     * @return true if the configuration file has been modified, false otherwise
     */
    @Override
    public boolean hasUpdated() {
        var newResult = configurationFile.get();

        boolean isSame = lastResult.map(
                lastConfig -> newResult.isSuccessful() && newResult.getSuccess().get() == lastConfig,
                lastError -> newResult.isFailure() && newResult.getFailure().get().equals(lastError));

        lastResult = newResult;

        return !isSame;
    }

    /**
     * @return configuration loaded from the configuration file
     */
    @Override
    public Configuration load() throws ParseException {
        lastResult = configurationFile.get();
        return lastResult.orElseThrow(ParseException::new);
    }
}
