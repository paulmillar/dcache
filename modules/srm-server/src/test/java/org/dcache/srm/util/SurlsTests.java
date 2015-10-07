package org.dcache.srm.util;

import org.junit.Test;

import java.net.URI;

import org.dcache.srm.SRMInvalidPathException;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 *  Tests to verify Surls utility class.
 */
public class SurlsTests
{
    @Test
    public void shouldRemoveLastEntryWithoutSlash() throws Exception
    {
        URI surl = URI.create("srm://srm.example.org/path/dir/file");

        URI parent = Surls.getParent(surl);

        assertThat(parent, is(equalTo(URI.create("srm://srm.example.org/path/dir"))));
    }

    @Test
    public void shouldRemoveLastEntryWithSlash() throws Exception
    {
        URI surl = URI.create("srm://srm.example.org/path/dir/subdir/");

        URI parent = Surls.getParent(surl);

        assertThat(parent, is(equalTo(URI.create("srm://srm.example.org/path/dir"))));
    }

    @Test
    public void shouldRemoveOnlyEntryWithoutSlash() throws Exception
    {
        URI surl = URI.create("srm://srm.example.org/path");

        URI parent = Surls.getParent(surl);

        assertThat(parent, is(equalTo(URI.create("srm://srm.example.org/"))));
    }

    @Test
    public void shouldRemoveOnlyEntryWithSlash() throws Exception
    {
        URI surl = URI.create("srm://srm.example.org/path/");

        URI parent = Surls.getParent(surl);

        assertThat(parent, is(equalTo(URI.create("srm://srm.example.org/"))));
    }

    @Test(expected=SRMInvalidPathException.class)
    public void shouldFailForRoot() throws Exception
    {
        URI surl = URI.create("srm://srm.example.org/");

        URI parent = Surls.getParent(surl);
    }

    @Test(expected=SRMInvalidPathException.class)
    public void shouldFailForRootDoubleSlash() throws Exception
    {
        URI surl = URI.create("srm://srm.example.org//");

        URI parent = Surls.getParent(surl);
    }

    @Test
    public void shouldHandleDoubleSlash() throws Exception
    {
        URI surl = URI.create("srm://srm.example.org/path/dir//file");

        URI parent = Surls.getParent(surl);

        assertThat(parent, is(equalTo(URI.create("srm://srm.example.org/path/dir"))));
    }

    @Test
    public void shouldHandleDoubleFinalSlash() throws Exception
    {
        URI surl = URI.create("srm://srm.example.org/path/dir/subdir//");

        URI parent = Surls.getParent(surl);

        assertThat(parent, is(equalTo(URI.create("srm://srm.example.org/path/dir"))));
    }
}
