package tr.edu.itu.bpmo.generator;

import java.io.File;

import org.sbpm.bpmo.Process;

import tr.edu.itu.bpmo.parser.BpmoParser;

import junit.framework.TestCase;

public class WadeWfGeneratorTest extends TestCase {

	public void testSimpleProcess() throws Exception {
//		File wsmlFile = new File("./testdata/SimpleFlow.wsml");
//		File wsmlFile = new File("./testdata/SimpleBranch.wsml");
//		File wsmlFile = new File("./testdata/SubProcess.wsml");
//		File wsmlFile = new File("./testdata/GoalProcess.wsml");
		File wsmlFile = new File("./testdata/LoopProcess.wsml");
		Process process = BpmoParser.parse(wsmlFile);

		WadeWorkflowGenerator.generateProcessCode(process, "/TestProject/src/tr/edu/itu/sample");
	}

}
