package com.cloudbees.jenkins.plugins.file_leak_detector;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Failure;
import hudson.model.ManagementLink;
import hudson.remoting.Which;
import hudson.util.ArgumentListBuilder;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.kohsuke.file_leak_detector.Listener;
import org.kohsuke.file_leak_detector.Main;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class FileHandleDump extends ManagementLink {
    private static final Logger LOGGER = Logger.getLogger(FileHandleDump.class.getName());

    @Override
    public String getIconFileName() {
        return "help.png";
    }

    @Override
    public String getDisplayName() {
        return "Open File Handles";
    }

    @Override
    public String getUrlName() {
        return "file-handles";
    }

    @Override
    public String getDescription() {
        return "Monitor the current open file handles on the master JVM";
    }

    @NonNull
    @Override
    public Category getCategory() {
        return Category.TROUBLESHOOTING;
    }

    /**
     * Dumps the currently opened files.
     */
    @SuppressWarnings("lgtm[jenkins/csrf]")
    public HttpResponse doIndex(StaplerResponse2 response) throws Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        Class<?> listener = loadListener();

        if (listener == null) {
            return HttpResponses.forwardToView(this, "_notRunning");
        }

        response.setContentType("text/plain;charset=UTF-8");
        listener.getMethod("dump", Writer.class).invoke(null, response.getWriter());
        return null;
    }

    /**
     * Activates the file leak detector.
     */
    @RequirePOST
    public HttpResponse doActivate(@QueryParameter String opts) throws Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if (loadListener() != null) {
            return HttpResponses.text("File leak detector is already activated");
        }

        // to activate, we need to use the JVM attach API, which internally uses JNI.
        // so if someone else tries to do the same (by creating a new classloader that loads tools.jar),
        // either we or they will fail. To avoid it, we'll launch a separate process and have that install
        // the agent
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(new File(System.getProperty("java.home"), "bin/java"))
                .add("-jar")
                .add(Which.jarFile(Main.class))
                .add(ProcessHandle.current().pid())
                .add(Util.fixEmpty(opts));

        Process p = new ProcessBuilder(args.toCommandArray())
                .redirectErrorStream(true)
                .start();

        p.getOutputStream().close();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(p.getInputStream(), baos);
        IOUtils.closeQuietly(p.getInputStream());
        IOUtils.closeQuietly(p.getErrorStream());

        int exitCode = p.waitFor();

        if (exitCode != 0) {
            /*
            There are 2 high-level ways the process can fail:
            1. The process can fail in org.kohsuke.file_leak_detector.Main#main. For example, this can happen if Jenkins
               is running with a JRE instead of a full JDK and so the instrumentation API doesn't load. In this case,
               the output of the process we created here contains the root cause of the failure. Because it is a
               separate process, its output will _not_ be sent to Jenkins' logs/stdout/stderr by default, and so we
               _must_ include its output in this error message or that information will be lost forever.
            2. The process can fail in org.kohsuke.file_leak_detector.AgentMain#agentmain. For example, this can happen
               if an invalid option is passed to the agent. In this case, the root cause of the issue will be printed
               out to the stderr of the Jenkins process, and so the user needs to look there to find out  what went wrong.
            We use Failure to omit the stack trace when it is shown to the user. Otherwise, the exception gets wrapped in
            ServletException, and the message is duplicated, which is confusing when we have such a large message like this.
            */
            Exception e = new Failure(
                    "Failed to activate file leak detector. Perhaps the parameters were incorrect. "
                            + "Look for 'Agent failed to start!' in stderr logs for more info. Additional logs:\n"
                            + baos,
                    true);
            // Print the messsage to the logs so we have a timestamp and the error message in case we need it later.
            // If we use a different exception type, this happens automatically, but we use Failure for reasons
            // described above.
            LOGGER.log(Level.WARNING, e.getMessage());
            throw e;
        }

        return HttpResponses.text("Successfully activated file leak detector");
    }

    /**
     * Obtains the reference to the {@link Listener} class.
     *
     * We need to use reflection and load it from the system classloader, because we bundle it as a dependency.
     * Servlet's "child first" classloading means non-reflective use will resolve to our own copy in plugin
     * classloader, and not from the running agent.
     *
     * @return
     *      null if the agent isn't running.
     */
    private Class<?> loadListener() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        try {
            Class<?> listener = ClassLoader.getSystemClassLoader().loadClass("org.kohsuke.file_leak_detector.Listener");
            boolean isAgentInstalled =
                    (Boolean) listener.getMethod("isAgentInstalled").invoke(null);
            if (!isAgentInstalled) {
                return null;
            }
            return listener;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
