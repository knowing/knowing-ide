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
package de.lmu.ifi.dbs.knowing.debug.ui.launching;

import static de.lmu.ifi.dbs.knowing.debug.core.launching.DPULaunchConfigurationDelegate.DPU_EXECUTION_PATH;
import static de.lmu.ifi.dbs.knowing.debug.core.launching.DPULaunchConfigurationDelegate.DPU_PARAMETERS;
import static de.lmu.ifi.dbs.knowing.debug.core.launching.DPULaunchConfigurationDelegate.DPU_PATH;
import static de.lmu.ifi.dbs.knowing.debug.core.launching.DPULaunchConfigurationDelegate.DPU_PROJECT;
import static de.lmu.ifi.dbs.knowing.debug.core.launching.DPULaunchConfigurationDelegate.findDPUFile;
import static de.lmu.ifi.dbs.knowing.debug.core.launching.DPULaunchConfigurationDelegate.loadDPU;
import static de.lmu.ifi.dbs.knowing.debug.core.launching.DPULaunchConfigurationDelegate.parametersToString;
import static de.lmu.ifi.dbs.knowing.debug.core.launching.DPULaunchConfigurationDelegate.stringToParameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.sapphire.modeling.ResourceStoreException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ResourceListSelectionDialog;

import de.lmu.ifi.dbs.knowing.core.model.IDataProcessingUnit;
import de.lmu.ifi.dbs.knowing.core.model.IParameter;
import de.lmu.ifi.dbs.knowing.debug.ui.editor.ParameterTableViewer;

/**
 * 
 * @author Nepomuk Seiler
 * @version 0.1
 * @since 2011-04-17
 */
public class DPULaunchConfigurationTab extends AbstractLaunchConfigurationTab {

	private Text					txtDPU;
	private Text					txtDPUPath;
	private Text					txtExecutionPath;
	private List<IParameter>		parameters;

	private Group					dpuConfigGroup;
	private ParameterTableViewer	propertyViewer;
	private IFile					dpuFile;
	private IDataProcessingUnit		dpu;

	@Override
	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NONE);
		container.setLayout(new GridLayout(1, false));

		/* === DPU selection === */

		Group dpuComposite = new Group(container, SWT.BORDER);
		dpuComposite.setText("Data Processing Unit");
		dpuComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		dpuComposite.setLayout(new GridLayout(3, false));

		Label lblDPU = new Label(dpuComposite, SWT.NONE);
		lblDPU.setText("Name: ");
		txtDPU = new Text(dpuComposite, SWT.BORDER | SWT.READ_ONLY);
		txtDPU.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		new Label(dpuComposite, SWT.NONE);

		Label lblDPUPath = new Label(dpuComposite, SWT.NONE);
		lblDPUPath.setText("File: ");
		txtDPUPath = new Text(dpuComposite, SWT.BORDER);
		txtDPUPath.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		txtDPUPath.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				validateDPUPath(txtDPUPath.getText());
				setDirty(true);
				updateLaunchConfigurationDialog();
			}

		});

		Button btnDPU = new Button(dpuComposite, SWT.PUSH);
		btnDPU.setText("Browse");
		btnDPU.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				// TODO Use ElementListSelectionDialog and fill with all dpus
				// located in the workspace
				IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
				ResourceListSelectionDialog dialog = new ResourceListSelectionDialog(getShell(), root, IContainer.FILE);
				dialog.setTitle("Select a Data Processing Unit");
				dialog.setMessage("Choose one DPU");
				if (dialog.open() != Dialog.OK)
					return;

				dpuFile = (IFile) dialog.getResult()[0];
				update();
				setDirty(true);
				updateLaunchConfigurationDialog();
			}
		});
		
		/* === Execution Path === */

		Label lblExecutionPath = new Label(dpuComposite, SWT.NONE);
		lblExecutionPath.setText("Executionpath: ");
		txtExecutionPath = new Text(dpuComposite, SWT.BORDER);
		txtExecutionPath.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		txtExecutionPath.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				validateExecutionPath(txtExecutionPath.getText());
				setDirty(true);
				updateLaunchConfigurationDialog();
			}
		});

		Button btnExecutionPath = new Button(dpuComposite, SWT.PUSH);
		btnExecutionPath.setText("Browse");
		btnExecutionPath.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				DirectoryDialog dialog = new DirectoryDialog(getShell());
				dialog.setText("Select Execution Path");
				updateExecutionPath(dialog.open());
			}
		});

		/* === Sapphire DPU Configuration === */

		dpuConfigGroup = new Group(container, SWT.BORDER);
		dpuConfigGroup.setText("Parameters");
		dpuConfigGroup.setLayoutData(new GridData(GridData.FILL_BOTH));
		dpuConfigGroup.setLayout(new FillLayout());

		propertyViewer = new ParameterTableViewer(dpuConfigGroup, SWT.SINGLE);
		propertyViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				// Doesn't do anything
				setDirty(true);
				updateLaunchConfigurationDialog();
			}
		});
		
		setControl(container);
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
		BundlesResolver resolver = new BundlesResolver(configuration);
		if(resolver.isBundleMissing()) {
			//TODO print error
			System.err.println("Bundles Missing: " + resolver.missingBundles());
		}
		configuration.setAttribute("target_bundles", resolver.selectedBundles());
	}

	@Override
	public void initializeFrom(ILaunchConfiguration configuration) {
		try {
			String projectName = configuration.getAttribute(DPU_PROJECT, (String) null);
			String relativePath = configuration.getAttribute(DPU_PATH, (String) null);
			String executionPath = configuration.getAttribute(DPU_EXECUTION_PATH, System.getProperty("user.home"));

			dpuFile = findDPUFile(projectName, relativePath);
			update();
			updateExecutionPath(executionPath);
			syncParameters(stringToParameters(configuration.getAttribute(DPU_PARAMETERS, new HashMap<>())));
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}
	
	/* ============================================= */
	/* =============== Apply changes =============== */
	/* ============================================= */

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		if (dpuFile == null)
			return;
		configuration.setAttribute(DPU_PROJECT, dpuFile.getProject().getName());
		configuration.setAttribute(DPU_PATH, dpuFile.getProjectRelativePath().toOSString());
		configuration.setAttribute(DPU_EXECUTION_PATH, txtExecutionPath.getText());
		configuration.setAttribute(DPU_PARAMETERS, parametersToString(parameters));
		setDirty(false);
	}

	private void update() {
		if (dpuFile == null)
			return;
		try {
			dpu = loadDPU(dpuFile);
			txtDPU.setText(dpu.getName().getContent());
			txtDPUPath.setText(dpuFile.getFullPath().toOSString());
			syncParameters(dpu.getParameters());
		} catch (ResourceStoreException e) {
			e.printStackTrace();
		}

	}

	private void updateExecutionPath(String path) {
		if(!validateExecutionPath(path))
			return;
		txtExecutionPath.setText(path);
		setDirty(true);
		updateLaunchConfigurationDialog();
	}
	
	private void syncParameters(List<IParameter> loadedParameters) {
		if(dpu == null)
			return;
		parameters = dpu.getParameters();
		for (IParameter p : parameters) {
			for (IParameter pLoaded : loadedParameters) {
				if (p.getKey().getContent().equals(pLoaded.getKey().getContent()))
					p.setValue(pLoaded.getValue().getContent());
			}
		}
		propertyViewer.setInput(parameters);
	}
	
	/* ============================================= */
	/* ============ Validation Methods ============= */
	/* ============================================= */
	
	private boolean validateExecutionPath(String path) {
		if (path == null || path.isEmpty())
			return false;
		
		Path executionPath = Paths.get(path);
		if (!Files.exists(executionPath)) {
			setErrorMessage("Execution path doesn't exists.");
			return false;
		}
		if (!Files.isDirectory(executionPath)) {
			setErrorMessage("Execution path must be a directory.");
			return false;
		} 
		setErrorMessage(null);
		return true;			
	}
	
	private boolean validateDPUPath(String path) {
		if (path == null || path.isEmpty())
			return false;
		
		Path dpuPath = Paths.get(path);
		if (!Files.exists(dpuPath)) {
			setErrorMessage("DPU doesn't exist.");
			return false;
		}
		if (Files.isDirectory(dpuPath)) {
			setErrorMessage("DPU must be a file.");
			return false;
		} 
		setErrorMessage(null);
		return true;			
	}
	

	@Override
	public String getName() {
		return "DPU Configuration";
	}

}
