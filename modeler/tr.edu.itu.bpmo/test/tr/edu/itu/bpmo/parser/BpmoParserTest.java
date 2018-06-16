package tr.edu.itu.bpmo.parser;

import java.io.File;

import junit.framework.TestCase;

import org.sbpm.bpmo.Process;
import org.sbpm.bpmo.Workflow;

import com.ontotext.sbpm.bpmo.StartEventImpl;

public class BpmoParserTest extends TestCase {

	private File wsmlFile = new File("./testdata/SimpleProcess.wsml");

	public void testSimpleProcess() throws Exception {
		Process process = BpmoParser.parse(wsmlFile);
		assertNotNull(process);
		assertEquals("SampleProcess", process.getName());

		Workflow workflow = process.getWorkflow();
		assertNotNull(workflow);
		assertEquals(StartEventImpl.class, workflow.getWorkflowElement().getClass());
		assertEquals(2, process.listConnectors().size());
	}

}
