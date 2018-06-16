package tr.edu.itu.bpmo.generator;

import org.apache.commons.lang3.text.WordUtils;
import org.sbpm.bpmo.Condition;
import org.sbpm.bpmo.DeferredChoiceMerge;
import org.sbpm.bpmo.Event;
import org.sbpm.bpmo.ExclusiveChoiceMerge;
import org.sbpm.bpmo.GoalTask;
import org.sbpm.bpmo.MultipleChoiceMultiMerge;
import org.sbpm.bpmo.ParallelSplitSynchronise;
import org.sbpm.bpmo.Process;
import org.sbpm.bpmo.Repeat;
import org.sbpm.bpmo.SubProcess;
import org.sbpm.bpmo.Task;
import org.sbpm.bpmo.WorkflowElement;

import com.ontotext.sbpm.bpmo.ConditionImpl;

public class GeneratorUtils {

	public static String[] getPackageName(String targetPath) {
		String[] pathParts = targetPath.substring(1).split("/");
		String[] packageNameParts = new String[pathParts.length - 2];
		for (int i = 2; i < pathParts.length; i++) {
			packageNameParts[i - 2] = pathParts[i];
		}
		return packageNameParts;
	}

	public static String getProcessName(Process process) {
		String processName = process.getName();
		processName = WordUtils.capitalize(processName);
		return processName.replace(" ", "");
	}

	public static String getNameOfElement(WorkflowElement workflowElement) {
		String name = "";
		if (workflowElement instanceof Event) {
			name = ((Event) workflowElement).getName();
		} else if (workflowElement instanceof SubProcess) {
			name = ((SubProcess) workflowElement).getName();
		} else if (workflowElement instanceof Task) {
			name = ((Task) workflowElement).getName();
		} else {
			name = workflowElement.getIdentifier().toString().split("#")[1];
		}
		name = getJavaCompliantPropName(name);
		return name;
	}
	public static String getNameOfElementCapitalized(WorkflowElement workflowElement) {
		String name = "";
		if (workflowElement instanceof Event) {
			name = ((Event) workflowElement).getName();
		} else if (workflowElement instanceof SubProcess) {
			name = ((SubProcess) workflowElement).getName();
		} else if (workflowElement instanceof Task) {
			name = ((Task) workflowElement).getName();
		} else {
			name = workflowElement.getIdentifier().toString().split("#")[1];
		}
		name = getJavaCompliantTypeName(name);
		return name;
	}

	public static String getJavaCompliantPropName(String name) {
		name = WordUtils.capitalize(name);
		name = name.replace(" ", "");
		name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
		return name;
	}

	public static String getJavaCompliantTypeName(String name) {
		name = WordUtils.capitalize(name);
		name = name.replace(" ", "");
		name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
		return name;
	}

	public static String convertToWadeWfElementType(WorkflowElement workflowElement) {
		String elementType = "UnknownType";

		if (workflowElement instanceof GoalTask) {
			elementType = "SubflowDelegationBehaviour";
		} else if (workflowElement instanceof Task) {
			elementType = "CodeExecutionBehaviour";
		} else if (workflowElement instanceof SubProcess) {
			elementType = "SubflowDelegationBehaviour";
		}
		return elementType;
	}

	public static boolean isBlockElement(WorkflowElement workflowElement) {
		return workflowElement instanceof ExclusiveChoiceMerge || workflowElement instanceof MultipleChoiceMultiMerge
				|| workflowElement instanceof DeferredChoiceMerge
				|| workflowElement instanceof ParallelSplitSynchronise || workflowElement instanceof Repeat;
	}

	public static ConditionImpl createNegateCondition(Condition baseCondition) {
		ConditionImpl negateCondition = new ConditionImpl(null);
		String negateConditionStr;
		if (!baseCondition.getExpression().startsWith("not")) {
			negateConditionStr = "not" + GeneratorUtils.getJavaCompliantTypeName(baseCondition.getExpression());
		} else {
			negateConditionStr = baseCondition.getExpression().substring(3).trim();
			negateConditionStr = Character.toLowerCase(negateConditionStr.charAt(0)) + negateConditionStr.substring(1);
		}
		negateCondition.setExpression(negateConditionStr);
		return negateCondition;
	}

}
