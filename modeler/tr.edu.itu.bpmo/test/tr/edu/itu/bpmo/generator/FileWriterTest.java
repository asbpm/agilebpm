package tr.edu.itu.bpmo.generator;

import java.io.File;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sbpm.bpmo.Process;

import tr.edu.itu.bpmo.parser.BpmoParser;
import junit.framework.TestCase;

public class FileWriterTest extends TestCase {

	public void testWriteFile() throws Exception {
		File wsmlFile = new File("./testdata/LoopProcess.wsml");
		Process process = BpmoParser.parse(wsmlFile);

		CompilationUnit cu = WadeWorkflowGenerator.generateProcessCode(process, "/TestProject/src/tr/edu/itu/sample");
//		FileWriter.writeToFile(cu, new File("/TestProject/src/tr/edu/itu/sample"), GeneratorUtils.getProcessName(process));
	}
	
}
