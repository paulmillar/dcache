/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2017 Deutsches Elektronen-Synchrotron
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
package org.dcache.util;

import static org.hamcrest.Matchers.*;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Optional;

import org.dcache.vehicles.FileAttributes;

import static org.junit.Assert.*;

public class ChecksumsTest
{
    private static final Checksum ADLER32_HELLO_WORLD
            = ChecksumType.ADLER32.calculate("Hello, world".getBytes(StandardCharsets.UTF_8));

    @Test
    public void shouldFindAdler32AsSingleEntry()
    {
        Optional<ChecksumType> type = Checksums.parseWantDigest("adler32");
        assertThat(type.isPresent(), is(equalTo(true)));
        assertThat(type.get(), is(equalTo(ChecksumType.ADLER32)));
    }

    @Test
    public void shouldFindMd5AsSingleEntry()
    {
        Optional<ChecksumType> type = Checksums.parseWantDigest("md5");
        assertThat(type.isPresent(), is(equalTo(true)));
        assertThat(type.get(), is(equalTo(ChecksumType.MD5_TYPE)));
    }

    @Test
    public void shouldFindSingleGoodEntryWithQ()
    {
        Optional<ChecksumType> type = Checksums.parseWantDigest("adler32;q=0.5");
        assertThat(type.isPresent(), is(equalTo(true)));
        assertThat(type.get(), is(equalTo(ChecksumType.ADLER32)));
    }

    @Test
    public void shouldSelectSecondAsBestByInternalPreference()
    {
        Optional<ChecksumType> type = Checksums.parseWantDigest("adler32,md5");
        assertThat(type.isPresent(), is(equalTo(true)));
        assertThat(type.get(), is(equalTo(ChecksumType.MD5_TYPE)));
    }

    @Test
    public void shouldSelectFirstAsBestByInternalPreference()
    {
        Optional<ChecksumType> type = Checksums.parseWantDigest("md5,adler32");
        assertThat(type.isPresent(), is(equalTo(true)));
        assertThat(type.get(), is(equalTo(ChecksumType.MD5_TYPE)));
    }

    @Test
    public void shouldSelectBestByExplicitQ()
    {
        Optional<ChecksumType> type = Checksums.parseWantDigest("adler32;q=0.5,md5;q=1");
        assertThat(type.isPresent(), is(equalTo(true)));
        assertThat(type.get(), is(equalTo(ChecksumType.MD5_TYPE)));
    }

    @Test
    public void shouldSelectBestByImplicitQ()
    {
        Optional<ChecksumType> type = Checksums.parseWantDigest("adler32;q=0.5,md5");
        assertThat(type.isPresent(), is(equalTo(true)));
        assertThat(type.get(), is(equalTo(ChecksumType.MD5_TYPE)));
    }

    @Test
    public void shouldIgnoreUnknownAlgorithm()
    {
        Optional<ChecksumType> type = Checksums.parseWantDigest("adler32;q=0.5,UNKNOWN;q=1");
        assertThat(type.isPresent(), is(equalTo(true)));
        assertThat(type.get(), is(equalTo(ChecksumType.ADLER32)));
    }

    @Test
    public void shouldGenerateNoHeaderIfNoWantDigest()
    {
        Optional<String> value = Checksums.digestHeader(null, FileAttributes.ofChecksum(ADLER32_HELLO_WORLD));
        assertThat(value.isPresent(), is(equalTo(false)));
    }

    @Test
    public void shouldGenerateNHeaderIfWantDigestOfAvailableChecksum()
    {
        Optional<String> value = Checksums.digestHeader("adler32", FileAttributes.ofChecksum(ADLER32_HELLO_WORLD));
        assertThat(value.isPresent(), is(equalTo(true)));
        assertThat(value.get(), startsWith("adler32="));
    }

    @Test
    public void shouldGenerateNoHeaderIfWantDigestOfUnavailableChecksum()
    {
        Optional<String> value = Checksums.digestHeader("md5", FileAttributes.ofChecksum(ADLER32_HELLO_WORLD));
        assertThat(value.isPresent(), is(equalTo(false)));
    }

    @Test
    public void shouldGenerateNoHeaderIfWantDigestButNoChecksumAvailable()
    {
        Optional<String> value = Checksums.digestHeader("adler32", new FileAttributes());
        assertThat(value.isPresent(), is(equalTo(false)));
    }
}
