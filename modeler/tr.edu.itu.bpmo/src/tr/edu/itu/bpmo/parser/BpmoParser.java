package tr.edu.itu.bpmo.parser;

import java.io.File;
import java.io.FileReader;

import org.sbpm.bpmo.Process;
import org.sbpm.bpmo.factory.Factory;

public class BpmoParser {

	public static Process parse(File file) {
		Process process = null;
		org.sbpm.bpmo.io.BpmoParser parser = Factory.createParser(null);
		org.sbpm.bpmo.Process[] result = null;
		try {
			System.out.println(parser);
			result = parser.parse(new FileReader(file.getPath()));
			System.out.println("Read " + result.length + " processes");

			process = result[0];
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		return process;
	}

}
