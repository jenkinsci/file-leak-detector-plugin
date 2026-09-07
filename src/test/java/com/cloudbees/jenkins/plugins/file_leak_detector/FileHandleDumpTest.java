/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.jenkins.plugins.file_leak_detector;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hudson.Functions;
import hudson.model.ManagementLink;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.channels.Pipe;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.List;
import org.htmlunit.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FileHandleDumpTest {

    @TempDir
    private File f;

    @Test
    void detectFileLeak(JenkinsRule r) throws Exception {
        activateFileLeakDetector();
        File leakyFile = File.createTempFile("leaky-file", null, f);
        try (InputStream unused = new FileInputStream(leakyFile)) {
            assertThat(
                    "There should be a file opened by this method",
                    getOpenDescriptors(r),
                    hasItem(allOf(
                            containsString(leakyFile.getPath() + " by thread:"),
                            containsString("FileHandleDumpTest.detectFileLeak("))));
        }
        assertThat(
                "There should not be a file opened by this method",
                getOpenDescriptors(r),
                not(hasItem(allOf(
                        containsString(leakyFile.getPath() + " by thread:"),
                        containsString("FileHandleDumpTest.detectFileLeak(")))));
    }

    @Test
    void detectPipeLeak(JenkinsRule r) throws Exception {
        // TODO: https://github.com/kohsuke/file-leak-detector/issues/36
        assumeFalse(Functions.isWindows(), "Pipes are not detected correctly on Windows");
        activateFileLeakDetector();
        Pipe p = Pipe.open();
        try {
            assertThat(
                    "There should be a pipe sink and source channel opened by this method",
                    getOpenDescriptors(r),
                    allOf(
                            hasItem(allOf(
                                    containsString("Pipe Sink Channel by thread:"),
                                    containsString("FileHandleDumpTest.detectPipeLeak("))),
                            hasItem(allOf(
                                    containsString("Pipe Source Channel by thread:"),
                                    containsString("FileHandleDumpTest.detectPipeLeak(")))));
        } finally {
            p.sink().close();
            p.source().close();
        }
        assertThat(
                "There should not be a pipe sink or source channel opened by this method",
                getOpenDescriptors(r),
                not(anyOf(
                        hasItem(allOf(
                                containsString("Pipe Sink Channel by thread:"),
                                containsString("FileHandleDumpTest.detectPipeLeak("))),
                        hasItem(allOf(
                                containsString("Pipe Source Channel by thread:"),
                                containsString("FileHandleDumpTest.detectPipeLeak("))))));
    }

    @Test
    void detectSelectorLeak(JenkinsRule r) throws Exception {
        activateFileLeakDetector();
        try (Selector unused = Selector.open()) {
            assertThat(
                    "There should be a selector opened by this method",
                    getOpenDescriptors(r),
                    hasItem(allOf(
                            containsString("selector by thread:"),
                            containsString("FileHandleDumpTest.detectSelectorLeak("))));
        }
        assertThat(
                "There should not be a selector opened by this method",
                getOpenDescriptors(r),
                not(hasItem(allOf(
                        containsString("selector by thread:"),
                        containsString("FileHandleDumpTest.detectSelectorLeak(")))));
    }

    /**
     * Activates the file-leak-detector library. Only activates once for the
     * whole test suite, but is called in every test so they can run in any
     * order, and cannot not be called until Jenkins starts.
     */
    private static void activateFileLeakDetector() throws Exception {
        FileHandleDump fileHandleDump = ManagementLink.all().get(FileHandleDump.class);
        fileHandleDump.doActivate("");
    }

    /**
     * @return a list of open file detectors as detected by the file-leak-detector
     * library. Items in the list have the following format:
     * <pre>{@code
     * <descriptor type> by thread:<thread name> on <date descriptor was opened>
     *     <stack trace of call that opened the descriptor>
     * }</pre>
     */
    private static List<String> getOpenDescriptors(JenkinsRule r) throws Exception {
        FileHandleDump fileHandleDump = ManagementLink.all().get(FileHandleDump.class);
        try (WebClient wc = r.createWebClient()) {
            Page p = wc.goTo(fileHandleDump.getUrlName(), "text/plain");
            String descriptorDump = p.getWebResponse().getContentAsString();
            String[] descriptors = descriptorDump.split("#\\d+ ");
            // First element is similar to "6 descriptors are open", so we exclude it.
            return Arrays.asList(descriptors).subList(1, descriptors.length);
        }
    }
}
