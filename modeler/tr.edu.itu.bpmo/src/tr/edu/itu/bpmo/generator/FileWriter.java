package tr.edu.itu.bpmo.generator;

import java.io.File;
import java.io.IOException;

import org.eclipse.jdt.core.dom.CompilationUnit;

import com.tilab.wade.utils.FileUtils;

public class FileWriter {

	public static void writeToFile(CompilationUnit cu, File targetFolder, String className) {
		File file = getTargetFile(targetFolder, className);
		System.out.println(file.getAbsolutePath());
		try {
			FileUtils.writeFile(file, cu.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static File getTargetFile(File targetFolder, String className) {
		File file = new File(targetFolder, className + ".java");
		return file;
	}

}
