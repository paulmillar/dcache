/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2016 Deutsches Elektronen-Synchrotron
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

import org.junit.Test;

import org.dcache.util.SiPrefix.Prefix;
import org.dcache.util.SiPrefix.Type;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class SiPrefixTests
{
    @Test
    public void shouldShowNonPrefixBinary()
    {
        String result = SiPrefix.singleValue(1L, Type.BINARY, "B");

        assertThat(result, equalTo("1 B"));
    }

    @Test
    public void shouldShowNonPrefixDecimal()
    {
        String result = SiPrefix.singleValue(1L, Type.DECIMAL, "B");

        assertThat(result, equalTo("1 B"));
    }

    @Test
    public void shouldShowExactKiloPrefix()
    {
        String result = SiPrefix.singleValue(3_000L, Type.DECIMAL, "B");

        assertThat(result, equalTo("3 kB"));
    }

    @Test
    public void shouldShowExactKibiPrefix()
    {
        String result = SiPrefix.singleValue(3_072L, Type.BINARY, "B");

        assertThat(result, equalTo("3 kiB"));
    }

    @Test
    public void shouldShowApproxKiloPrefix()
    {
        String result = SiPrefix.singleValue(3_600L, Type.DECIMAL, "B");

        assertThat(result, equalTo("3 kB"));
    }


    @Test
    public void shouldShowApproxKibiPrefix()
    {
        String result = SiPrefix.singleValue(3_100L, Type.BINARY, "B");

        assertThat(result, equalTo("3 kiB"));
    }

    @Test
    public void shouldShowExactMegaPrefix()
    {
        String result = SiPrefix.singleValue(3_000_000L, Type.DECIMAL, "B");

        assertThat(result, equalTo("3 MB"));
    }

    @Test
    public void shouldShowExactMebiPrefix()
    {
        String result = SiPrefix.singleValue(3_145_728L, Type.BINARY, "B");

        assertThat(result, equalTo("3 MiB"));
    }

    @Test
    public void shouldShowApproxMegaPrefix()
    {
        String result = SiPrefix.singleValue(3_600_000L, Type.DECIMAL, "B");

        assertThat(result, equalTo("3 MB"));
    }


    @Test
    public void shouldShowApproxMebiPrefix()
    {
        String result = SiPrefix.singleValue(3_200_000L, Type.BINARY, "B");

        assertThat(result, equalTo("3 MiB"));
    }

    @Test
    public void shouldShowExactGigaPrefix()
    {
        String result = SiPrefix.singleValue(3_000_000_000L, Type.DECIMAL, "B");

        assertThat(result, equalTo("3 GB"));
    }

    @Test
    public void shouldShowExactGibiPrefix()
    {
        String result = SiPrefix.singleValue(3_221_225_472L, Type.BINARY, "B");

        assertThat(result, equalTo("3 GiB"));
    }

    @Test
    public void shouldShowApproxGigaPrefix()
    {
        String result = SiPrefix.singleValue(3_600_000_000L, Type.DECIMAL, "B");

        assertThat(result, equalTo("3 GB"));
    }

    @Test
    public void shouldShowApproxGibiPrefix()
    {
        String result = SiPrefix.singleValue(3_300_000_000L, Type.BINARY, "B");

        assertThat(result, equalTo("3 GiB"));
    }

    @Test
    public void shouldShowExactTeraPrefix()
    {
        String result = SiPrefix.singleValue(3_000_000_000_000L, Type.DECIMAL, "B");

        assertThat(result, equalTo("3 TB"));
    }

    @Test
    public void shouldShowExactTebiPrefix()
    {
        String result = SiPrefix.singleValue(3_298_534_883_328L, Type.BINARY, "B");

        assertThat(result, equalTo("3 TiB"));
    }

    @Test
    public void shouldShowApproxTeraPrefix()
    {
        String result = SiPrefix.singleValue(3_600_000_000_000L, Type.DECIMAL, "B");

        assertThat(result, equalTo("3 TB"));
    }

    @Test
    public void shouldShowApproxTebiPrefix()
    {
        String result = SiPrefix.singleValue(3_300_000_000_000L, Type.BINARY, "B");

        assertThat(result, equalTo("3 TiB"));
    }

    @Test
    public void shouldShowExactPetaPrefix()
    {
        String result = SiPrefix.singleValue(3_000_000_000_000_000L, Type.DECIMAL, "B");

        assertThat(result, equalTo("3 PB"));
    }

    @Test
    public void shouldShowExactPebiPrefix()
    {
        String result = SiPrefix.singleValue(3_377_699_720_527_872L, Type.BINARY, "B");

        assertThat(result, equalTo("3 PiB"));
    }

    @Test
    public void shouldShowApproxPetaPrefix()
    {
        String result = SiPrefix.singleValue(3_600_000_000_000_000L, Type.DECIMAL, "B");

        assertThat(result, equalTo("3 PB"));
    }


    @Test
    public void shouldShowApproxPebiPrefix()
    {
        String result = SiPrefix.singleValue(3_400_000_000_000_000L, Type.BINARY, "B");

        assertThat(result, equalTo("3 PiB"));
    }

    @Test
    public void shouldConvertNoneToKilo()
    {
        long result = Prefix.KILO.convert(12_345L, Prefix.NONE);

        assertThat(result, equalTo(12L));
    }

    @Test
    public void shouldConvertNoneToKibi()
    {
        long result = Prefix.KIBI.convert(2_480L, Prefix.NONE);

        assertThat(result, equalTo(2L));
    }

    @Test
    public void shouldConvertNoneToMega()
    {
        long result = Prefix.MEGA.convert(12_345_678L, Prefix.NONE);

        assertThat(result, equalTo(12L));
    }

    @Test
    public void shouldConvertNoneToMebi()
    {
        long result = Prefix.MEBI.convert(12_345_678L, Prefix.NONE);

        assertThat(result, equalTo(11L));
    }


    @Test
    public void shouldConvertKiloToNone()
    {
        long result = Prefix.NONE.convert(12L, Prefix.KILO);

        assertThat(result, equalTo(12_000L));
    }

    @Test
    public void shouldConvertKibiToNone()
    {
        long result = Prefix.NONE.convert(2L, Prefix.KIBI);

        assertThat(result, equalTo(2_048L));
    }

    @Test
    public void shouldConvertMegaToNone()
    {
        long result = Prefix.NONE.convert(12L, Prefix.MEGA);

        assertThat(result, equalTo(12_000_000L));
    }

    @Test
    public void shouldConvertMebiToNone()
    {
        long result = Prefix.NONE.convert(2L, Prefix.MEBI);

        assertThat(result, equalTo(2_097_152L));
    }
}
