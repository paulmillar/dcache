package org.dcache.srm.request.sql;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.sql.SQLException;
import java.util.Collections;

import static com.google.common.collect.Sets.newHashSet;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyLong;
import static org.mockito.MockitoAnnotations.initMocks;

public class DatabaseFileRequestStorageTests
{
    private static final long DUMMY_REQUEST_ID = -1;

    @Mock
    private DatabaseFileRequestStorage _storage;

    @Before
    public void setup() throws SQLException
    {
        initMocks(this);
        given(_storage.ordinalForRequest(anyLong(), anyLong())).willCallRealMethod();
    }

    @Test
    public void shouldProvideCorrectOrdinalForFirstInSequentialRequest()
            throws SQLException
    {
        given(_storage.queryIdsForRequest(anyLong())).
                willReturn(newHashSet(5L, 6L, 7L));

        long index = _storage.ordinalForRequest(DUMMY_REQUEST_ID, 5);

        assertThat(index, is(1L));
    }

    @Test
    public void shouldProvideCorrectOrdinalForSecondInSequentialRequest()
            throws SQLException
    {
        given(_storage.queryIdsForRequest(anyLong())).
                willReturn(newHashSet(5L, 6L, 7L));

        long index = _storage.ordinalForRequest(DUMMY_REQUEST_ID, 6);

        assertThat(index, is(2L));
    }

    @Test
    public void shouldProvideCorrectOrdinalForThirdInSequentialRequest()
            throws SQLException
    {
        given(_storage.queryIdsForRequest(anyLong())).
                willReturn(newHashSet(5L, 6L, 7L));

        long index = _storage.ordinalForRequest(DUMMY_REQUEST_ID, 7);

        assertThat(index, is(3L));
    }

    @Test
    public void shouldProvideCorrectOrdinalForFirstInNonSequentialRequest()
            throws SQLException
    {
        given(_storage.queryIdsForRequest(anyLong())).
                willReturn(newHashSet(6L, 10L, 12L));

        long index = _storage.ordinalForRequest(DUMMY_REQUEST_ID, 6);

        assertThat(index, is(1L));
    }

    @Test
    public void shouldProvideCorrectOrdinalForSecondInNonSequentialRequest()
            throws SQLException
    {
        given(_storage.queryIdsForRequest(anyLong())).
                willReturn(newHashSet(6L, 10L, 12L));

        long index = _storage.ordinalForRequest(DUMMY_REQUEST_ID, 10);

        assertThat(index, is(2L));
    }

    @Test
    public void shouldProvideCorrectOrdinalForThirdInNonSequentialRequest()
            throws SQLException
    {
        given(_storage.queryIdsForRequest(anyLong())).
                willReturn(newHashSet(6L, 10L, 12L));

        long index = _storage.ordinalForRequest(DUMMY_REQUEST_ID, 12);

        assertThat(index, is(3L));
    }

    @Test
    public void shouldProvideCorrectOrdinalForFirstInWrapAfterFirst()
            throws SQLException
    {
        given(_storage.queryIdsForRequest(anyLong())).
                willReturn(newHashSet(Long.MAX_VALUE, Long.MIN_VALUE,
                Long.MIN_VALUE+1));

        long index = _storage.ordinalForRequest(DUMMY_REQUEST_ID, Long.MAX_VALUE);

        assertThat(index, is(1L));
    }

    @Test
    public void shouldProvideCorrectOrdinalForSecondInWrapAfterFirst()
            throws SQLException
    {
        given(_storage.queryIdsForRequest(anyLong())).
                willReturn(newHashSet(Long.MAX_VALUE, Long.MIN_VALUE,
                Long.MIN_VALUE+1));

        long index = _storage.ordinalForRequest(DUMMY_REQUEST_ID, Long.MIN_VALUE);

        assertThat(index, is(2L));
    }

    @Test
    public void shouldProvideCorrectOrdinalForThirdInWrapAfterFirst()
            throws SQLException
    {
        given(_storage.queryIdsForRequest(anyLong())).
                willReturn(newHashSet(Long.MAX_VALUE, Long.MIN_VALUE,
                Long.MIN_VALUE+1));

        long index = _storage.ordinalForRequest(DUMMY_REQUEST_ID, Long.MIN_VALUE+1);

        assertThat(index, is(3L));
    }

    @Test
    public void shouldProvideCorrectOrdinalForFirstInWrapAfterSecond()
            throws SQLException
    {
        given(_storage.queryIdsForRequest(anyLong())).
                willReturn(newHashSet(Long.MAX_VALUE-3, Long.MAX_VALUE-1,
                Long.MIN_VALUE+4));

        long index = _storage.ordinalForRequest(DUMMY_REQUEST_ID, Long.MAX_VALUE-3);

        assertThat(index, is(1L));
    }

    @Test
    public void shouldProvideCorrectOrdinalForSecondInWrapAfterSecond()
            throws SQLException
    {
        given(_storage.queryIdsForRequest(anyLong())).
                willReturn(newHashSet(Long.MAX_VALUE-3, Long.MAX_VALUE-1,
                Long.MIN_VALUE+4));

        long index = _storage.ordinalForRequest(DUMMY_REQUEST_ID, Long.MAX_VALUE-1);

        assertThat(index, is(2L));
    }

    @Test
    public void shouldProvideCorrectOrdinalForThirdInWrapAfterSecond()
            throws SQLException
    {
        given(_storage.queryIdsForRequest(anyLong())).
                willReturn(newHashSet(Long.MAX_VALUE-3, Long.MAX_VALUE-1,
                Long.MIN_VALUE+4));

        long index = _storage.ordinalForRequest(DUMMY_REQUEST_ID, Long.MIN_VALUE+4);

        assertThat(index, is(3L));
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldThrowErrorIfRequestUnknown()
            throws SQLException
    {
        given(_storage.queryIdsForRequest(anyLong())).
                willReturn(Collections.<Long>emptySet());

        _storage.ordinalForRequest(DUMMY_REQUEST_ID, 5);
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldThrowErrorIfFileRequestUnknown()
            throws SQLException
    {
        given(_storage.queryIdsForRequest(anyLong())).
                willReturn(newHashSet(5L, 6L, 7L));

        _storage.ordinalForRequest(DUMMY_REQUEST_ID, 1);
    }
}
