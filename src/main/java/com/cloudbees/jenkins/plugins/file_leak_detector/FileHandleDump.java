package com.cloudbees.jenkins.plugins.file_leak_detector;

import hudson.Extension;
import hudson.Util;
import hudson.model.Hudson;
import hudson.model.ManagementLink;
import hudson.os.PosixAPI;
import org.kohsuke.file_leak_detector.Listener;
import org.kohsuke.file_leak_detector.Main;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class FileHandleDump extends ManagementLink {
    public String getIconFileName() {
        return "help.png";
    }

    public String getDisplayName() {
        return "Open File Handles";
    }

    public String getUrlName() {
        return "file-handles";
    }

    @Override
    public String getDescription() {
        return "Monitor the current open file handles on the master JVM";
    }

    /**
     * Dumps the currently opened files.
     */
    public HttpResponse doIndex(StaplerResponse response) throws Exception {
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);

        Class<?> listener = loadListener();

        if (listener==null) {
            return HttpResponses.forwardToView(this,"_notRunning");
        }

        response.setContentType("text/plain;charset=UTF-8");
        listener.getMethod("dump", Writer.class).invoke(null, response.getWriter());
        return null;
    }
    
    /**
     * Activates the file leak detector.
     */
    public HttpResponse doActivate(@QueryParameter String opts) throws Exception {
        requirePOST();
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);

        if (loadListener()!=null)
            return HttpResponses.plainText("File leak detector is already activated");

        Main main = new Main();
        main.pid = Integer.toString(PosixAPI.get().getpid());
        main.options = Util.fixEmpty(opts);
        main.run();
        return HttpResponses.plainText("Successfully activated file leak detector");
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
            boolean isAgentInstalled = (Boolean)listener.getMethod("isAgentInstalled").invoke(null);
            if (!isAgentInstalled)  return null;
            return listener;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
    protected final void requirePOST() throws ServletException {
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req==null)  return; // invoked outside the context of servlet
        String method = req.getMethod();
        if(!method.equalsIgnoreCase("POST"))
            throw new ServletException("Must be POST, Can't be "+method);
    }

    private static final Logger LOGGER = Logger.getLogger(FileHandleDump.class.getName());
}
