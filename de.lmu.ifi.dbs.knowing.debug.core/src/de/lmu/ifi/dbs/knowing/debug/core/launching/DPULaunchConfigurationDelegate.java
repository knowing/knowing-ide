/*                                                               *\
 ** |¯¯|/¯¯/|¯¯ \|¯¯| /¯¯/\¯¯\'|¯¯|  |¯¯||¯¯||¯¯ \|¯¯| /¯¯/|__|  **
 ** | '| '( | '|\  '||  |  | '|| '|/\| '|| '|| '|\  '||  | ,---, **
 ** |__|\__\|__|'|__| \__\/__/'|__,/\'__||__||__|'|__| \__\/__|  **
 **                                                              **
 ** Knowing Framework                                            **
 ** Apache License - http://www.apache.org/licenses/             **
 ** LMU Munich - Database Systems Group                          **
 ** http://www.dbs.ifi.lmu.de/                                   **
\*                                                               */
package de.lmu.ifi.dbs.knowing.debug.core.launching;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.pde.launching.OSGiLaunchConfigurationDelegate;
import org.eclipse.sapphire.modeling.ResourceStoreException;
import org.eclipse.sapphire.modeling.xml.RootXmlResource;
import org.eclipse.sapphire.modeling.xml.XmlResourceStore;
import org.eclipse.sapphire.workspace.WorkspaceFileResourceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import de.lmu.ifi.dbs.knowing.core.model.IDataProcessingUnit;
import de.lmu.ifi.dbs.knowing.core.model.IParameter;
import de.lmu.ifi.dbs.knowing.launcher.LaunchConfiguration;

/**
 * 
 * @author Nepomuk Seiler
 * 
 */
public class DPULaunchConfigurationDelegate extends OSGiLaunchConfigurationDelegate {

    public static final String PLUGIN_ID = "de.lmu.ifi.dbs.knowing.debug.core";
    public static final String LAUNCH_TYPE_ID = "de.lmu.ifi.dbs.knowing.debug.core.DPULaunchConfigurationType";
    public static final String LAUNCH_TYPE_CATEGORY = "Knowing";

    private static final Logger log = LoggerFactory.getLogger(PLUGIN_ID);

    /* ======================================= */
    /* = Constants are only internally used = */
    /* = see knowing.launcher/reference.conf = */
    /* ======================================= */

    public static final String DPU_PROJECT = "knowing.dpu.project";

    /** Relative to project */
    public static final String DPU_PATH = "knowing.dpu.path";

    public static final String DPU_EXECUTION_PATH = "knowing.dpu.executionpath";

    public static final String DPU_PARAMETERS = "knowing.dpu.parameters";

    @Override
    public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor) throws CoreException {
        monitor.subTask("Loading Data Processing Unit");
        String projectName = configuration.getAttribute(DPU_PROJECT, (String) null);
        String relativePath = configuration.getAttribute(DPU_PATH, (String) null);
        String execPath = configuration.getAttribute(DPU_EXECUTION_PATH, System.getProperty("user.home"));
        String vmArguments = configuration.getAttribute(LaunchConfiguration.VM_ARGUMENTS_KEY(), (String) null);
        IFile dpuFile = findDPUFile(projectName, relativePath);
        if (!dpuFile.exists())
            throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, "DPU doesn't exist! " + dpuFile.getLocationURI()));

        IDataProcessingUnit dpu = null;
        try {
            dpu = loadDPU(dpuFile);
        } catch (ResourceStoreException e) {
            throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, "Error loading DPU!", e));
        }

        Path executionPath = Paths.get(execPath);
        if (!Files.isDirectory(executionPath))
            throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, "Execution path is a file!"));
        if (!Files.isWritable(executionPath))
            throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, "Execution path is not writeable!"));

        // Typesafe config which is written to file
        Map<String, Object> confMap = new HashMap<>();
        confMap.put("dpu.name", dpu.getName().getContent());
        confMap.put("dpu.uri", dpuFile.getLocationURI().toString());
        confMap.put("dpu.executionpath", execPath);

        List<IParameter> parameters = stringToParameters(configuration.getAttribute(DPU_PARAMETERS, Collections.EMPTY_MAP));
        for (IParameter p : parameters) {
            confMap.put("dpu.parameters." + p.getKey().getContent(), p.getValue().getContent());
        }
        Config config = ConfigFactory.parseMap(confMap);

        // Write application.conf to execution path root
        Path applicationConf = executionPath.resolve("application.conf");
        try (Writer w = Files.newBufferedWriter(applicationConf, Charset.defaultCharset())) {
            w.write(config.root().render());
        } catch (IOException e) {
            log.error("IOError on writing application.conf to " + applicationConf, e);
        }

        // Set application.conf path
        String arguments = vmArguments + " -D" + LaunchConfiguration.APPLICATION_CONF_FILE() + "=" + applicationConf.toAbsolutePath();
        ILaunchConfigurationWorkingCopy copy = configuration.getWorkingCopy();
        copy.setAttribute(LaunchConfiguration.VM_ARGUMENTS_KEY(), arguments);
        super.launch(copy, mode, launch, monitor);

    }

    public static IFile findDPUFile(String projectName, String relativePath) {
        if (projectName == null || relativePath == null)
            return null;
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject project = root.getProject(projectName);
        return (IFile) project.findMember(relativePath);
    }

    public static IDataProcessingUnit loadDPU(IFile dpuFile) throws ResourceStoreException {
        XmlResourceStore store = new XmlResourceStore(new WorkspaceFileResourceStore(dpuFile));
        RootXmlResource resource = new RootXmlResource(store);
        IDataProcessingUnit original = IDataProcessingUnit.TYPE.instantiate(resource);
        IDataProcessingUnit dpu = IDataProcessingUnit.TYPE.instantiate();
        dpu.copy(original);
        return dpu;
    }

    public static List<IParameter> stringToParameters(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty())
            return new ArrayList<>();
        ArrayList<IParameter> result = new ArrayList<>(parameters.size());
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            IParameter p = IParameter.TYPE.instantiate();
            p.setKey(entry.getKey());
            p.setValue(entry.getValue());
            result.add(p);
        }
        return result;
    }

    public static Map<String, String> parametersToString(List<IParameter> parameters) {
        if (parameters == null || parameters.isEmpty())
            return new HashMap<>();
        Map<String, String> result = new HashMap<>();
        for (IParameter p : parameters) {
            result.put(p.getKey().getContent(), p.getValue().getContent());
        }
        return result;
    }

    public static Map<String, String> parameterMap(List<String> tokens) {
        Map<String, String> results = new HashMap<>();
        for (String token : tokens) {
            String[] keyValue = token.split("=");
            results.put(keyValue[0], keyValue[1]);
        }
        return results;
    }

}
