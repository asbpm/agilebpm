package tr.edu.itu.bpmo;

import java.io.File;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.model.BaseWorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.sbpm.bpmo.Process;

import tr.edu.itu.bpmo.generator.FileWriter;
import tr.edu.itu.bpmo.generator.GeneratorUtils;
import tr.edu.itu.bpmo.generator.WadeWorkflowGenerator;
import tr.edu.itu.bpmo.parser.BpmoParser;

public class CodeGenarationAction implements IObjectActionDelegate {

	private Shell shell;

	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		shell = targetPart.getSite().getShell();
	}

	public void run(IAction action) {
		IFile selectedWsdl = identifyClickedWsdl();
		IFolder iselectedFolder = selectFolder();
		if (iselectedFolder != null) {
			System.out.println(selectedWsdl.getFullPath());
			System.out.println(iselectedFolder.getFullPath());
			File wsmlFile = selectedWsdl.getRawLocation().makeAbsolute().toFile();
			File selectedFolder = iselectedFolder.getRawLocation().makeAbsolute().toFile();
			System.out.println(wsmlFile.getAbsolutePath());
			Process process = BpmoParser.parse(wsmlFile);
			System.out.println(process.getName());
			CompilationUnit cu = WadeWorkflowGenerator.generateProcessCode(process, iselectedFolder.getFullPath()
					.toString());
			FileWriter.writeToFile(cu, selectedFolder, GeneratorUtils.getProcessName(process));
		}
	}

	private IFile identifyClickedWsdl() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IStructuredSelection selection = (IStructuredSelection) window.getSelectionService().getSelection(
				"org.eclipse.jdt.ui.PackageExplorer");
		return (IFile) selection.getFirstElement();
	}

	private IFolder selectFolder() {
		IFolder folder = null;
		ElementTreeSelectionDialog dialog = new ElementTreeSelectionDialog(shell, new WorkbenchLabelProvider(),
				new BaseWorkbenchContentProvider());
		dialog.setTitle("Code Generation Folder");
		dialog.setMessage("Select the process code generation folder:");
		dialog.setInput(ResourcesPlugin.getWorkspace().getRoot());
		dialog.addFilter(new ViewerFilter() {

			public boolean select(Viewer viewer, Object parentElement, Object element) {
				if (element instanceof IProject || element instanceof IFolder) {
					return true;
				}
				return false;
			}

		});

		if (dialog.open() == Window.OK) {
			Object[] results = dialog.getResult();
			if (results != null) {
				folder = (IFolder) results[0];
			}
		}
		return folder;
	}

	public void selectionChanged(IAction action, ISelection selection) {
		// TODO Auto-generated method stub

	}

}
