package tr.edu.itu.bpmo.generator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.text.WordUtils;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.sbpm.bpmo.BlockPattern;
import org.sbpm.bpmo.Condition;
import org.sbpm.bpmo.ConditionalBranch;
import org.sbpm.bpmo.ControlflowConnector;
import org.sbpm.bpmo.DataTypeEntity;
import org.sbpm.bpmo.EndEvent;
import org.sbpm.bpmo.ExclusiveChoiceMerge;
import org.sbpm.bpmo.GoalTask;
import org.sbpm.bpmo.MultipleChoiceMultiMerge;
import org.sbpm.bpmo.ParallelSplitSynchronise;
import org.sbpm.bpmo.Process;
import org.sbpm.bpmo.Repeat;
import org.sbpm.bpmo.StartEvent;
import org.sbpm.bpmo.SubProcess;
import org.sbpm.bpmo.Task;
import org.sbpm.bpmo.UnconditionalBranch;
import org.sbpm.bpmo.WorkflowElement;

import com.ontotext.sbpm.bpmo.GoalTaskImpl;

public class WadeWorkflowGenerator {

	private static List<WorkflowElement> lastTasks = new ArrayList<WorkflowElement>();
	private static List<WorkflowElement> workflowElements = new ArrayList<WorkflowElement>();

	public static CompilationUnit generateProcessCode(Process process, String targetPath) {
		lastTasks = new ArrayList<WorkflowElement>();
		workflowElements = new ArrayList<WorkflowElement>();

		AST ast = AST.newAST(AST.JLS3);
		CompilationUnit cu = ast.newCompilationUnit();

		setPackageName(targetPath, ast, cu);
		addStaticImports(ast, cu);

		TypeDeclaration classDeclaration = createClass(process, ast, cu);
		// declareField(ast, td);
		declareBlacklistField(ast, classDeclaration);

		Block defineActivitiesMethod = createDefineActivitiesMethod(ast, classDeclaration);
		Block defineTransitionsMethod = createDefineTransitionsMethod(ast, classDeclaration);

		WorkflowElement firstTask = null;
		// ChoiceNode - nextTask
		Map<WorkflowElement, WorkflowElement> blockSubsequentsMap = new HashMap<WorkflowElement, WorkflowElement>();
		Map<BlockPattern, List<WorkflowElement>> blockMembers = new HashMap<BlockPattern, List<WorkflowElement>>();

		for (ControlflowConnector controlflow : process.listConnectors()) {
			if (GeneratorUtils.isBlockElement(controlflow.getTarget())) {
				identifyBlockMembers(blockMembers, controlflow.getTarget());
			}
		}

		for (ControlflowConnector controlflow : process.listConnectors()) {
			if (controlflow.getSource() instanceof StartEvent) {
				firstTask = controlflow.getTarget();
			}
			if (controlflow.getTarget() instanceof EndEvent) {
				lastTasks.add(controlflow.getSource());
			}

			if (!workflowElements.contains(controlflow.getTarget())) {
				if (!(controlflow.getTarget() instanceof EndEvent || GeneratorUtils.isBlockElement(controlflow
						.getTarget()))) {
					workflowElements.add(controlflow.getTarget());
				}
			}

			if (GeneratorUtils.isBlockElement(controlflow.getSource())) {
				blockSubsequentsMap.put(controlflow.getSource(), controlflow.getTarget());

			}
			if (GeneratorUtils.isBlockElement(controlflow.getTarget())) {
				redirectBranchIncominglink(ast, classDeclaration, defineTransitionsMethod, controlflow, controlflow
						.getTarget());
			} else {
				if (!(controlflow.getSource() instanceof StartEvent || controlflow.getTarget() instanceof EndEvent || GeneratorUtils
						.isBlockElement(controlflow.getSource()))) {
					registerTransition(ast, classDeclaration, defineTransitionsMethod, controlflow);
					for (BlockPattern blockPattern : blockMembers.keySet()) {
						List<WorkflowElement> blockMemberList = blockMembers.get(blockPattern);
						if (blockMemberList.contains(controlflow.getSource())) {
							blockMemberList.add(controlflow.getTarget());
						}
					}
				}
			}
		}

		handleDanglingBlockElements(process, ast, classDeclaration, defineTransitionsMethod, blockSubsequentsMap,
				blockMembers);

		for (WorkflowElement workflowElement : workflowElements) {
			registerActivity(ast, defineActivitiesMethod, workflowElement, workflowElement.equals(firstTask), lastTasks
					.contains(workflowElement));

			if (workflowElement instanceof GoalTask) {
				createGoalExecutionMethod(ast, classDeclaration, workflowElement);
			} else if (workflowElement instanceof Task) {
				createTaskExecutionMethod(ast, classDeclaration, workflowElement);
			} else if (workflowElement instanceof SubProcess) {
				createSubFlowExecutionMethod(ast, classDeclaration, workflowElement);
			}
		}

		System.out.println(cu);
		return cu;
	}

	private static void declareBlacklistField(AST ast, TypeDeclaration classDeclaration) {
		VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName("blackList"));
		ClassInstanceCreation newClassInstance = ast.newClassInstanceCreation();
		newClassInstance.setType(ast.newSimpleType(ast.newSimpleName("ArrayList")));
		fragment.setInitializer(newClassInstance);
		FieldDeclaration newFieldDeclaration = ast.newFieldDeclaration(fragment);
		newFieldDeclaration.setType(ast.newSimpleType(ast.newSimpleName("List")));
		newFieldDeclaration.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
		classDeclaration.bodyDeclarations().add(newFieldDeclaration);
	}

	private static void handleDanglingBlockElements(Process process, AST ast, TypeDeclaration classDeclaration,
			Block defineTransitionsMethod, Map<WorkflowElement, WorkflowElement> blockSubsequentsMap,
			Map<BlockPattern, List<WorkflowElement>> blockMembers) {
		for (WorkflowElement blockElement : blockSubsequentsMap.keySet()) {
			for (WorkflowElement blockMember : blockMembers.get(blockElement)) {
				boolean isDangling = true;
				for (ControlflowConnector controlflow : process.listConnectors()) {
					if (controlflow.getSource().equals(blockMember)) {
						isDangling = false;
						break;
					}
				}
				if (isDangling) {
					if (blockElement instanceof Repeat) {
						Repeat repeat = (Repeat) blockElement;
						Condition repeatCondition = repeat.getCondition();
						registerTransition(ast, classDeclaration, defineTransitionsMethod, blockMember, blockMember,
								repeatCondition);

						registerTransition(ast, classDeclaration, defineTransitionsMethod, blockMember,
								blockSubsequentsMap.get(blockElement), GeneratorUtils
										.createNegateCondition(repeatCondition));
					} else {
						registerTransition(ast, classDeclaration, defineTransitionsMethod, blockMember,
								blockSubsequentsMap.get(blockElement));
					}
				}
			}
		}
	}

	private static void redirectBranchIncominglink(AST ast, TypeDeclaration classDeclaration,
			Block defineTransitionsMethod, ControlflowConnector controlflow, WorkflowElement blockElement) {
		if (blockElement instanceof ExclusiveChoiceMerge) {
			ExclusiveChoiceMerge ecm = (ExclusiveChoiceMerge) blockElement;
			ConditionalBranch defaultBranch = ecm.getDeafultBranch();
			if (defaultBranch != null) {
				registerTransition(ast, classDeclaration, defineTransitionsMethod, controlflow.getSource(),
						defaultBranch.getElement(), defaultBranch.getCondition());
				workflowElements.add(defaultBranch.getElement());
			}
			for (ConditionalBranch conditionalBranch : ecm.listConditionalBranches()) {
				registerTransition(ast, classDeclaration, defineTransitionsMethod, controlflow.getSource(),
						conditionalBranch.getElement(), conditionalBranch.getCondition());
				workflowElements.add(conditionalBranch.getElement());
			}
		} else if (blockElement instanceof ParallelSplitSynchronise) {
			ParallelSplitSynchronise pss = (ParallelSplitSynchronise) blockElement;
			for (UnconditionalBranch unconditionalBranch : pss.listBranches()) {
				registerTransition(ast, classDeclaration, defineTransitionsMethod, controlflow.getSource(),
						unconditionalBranch.getElement());
				workflowElements.add(unconditionalBranch.getElement());
			}
		} else if (blockElement instanceof MultipleChoiceMultiMerge) {
			MultipleChoiceMultiMerge mcmm = (MultipleChoiceMultiMerge) blockElement;
			for (ConditionalBranch conditionalBranch : mcmm.listConditionalBranches()) {
				registerTransition(ast, classDeclaration, defineTransitionsMethod, controlflow.getSource(),
						conditionalBranch.getElement(), conditionalBranch.getCondition());
				workflowElements.add(conditionalBranch.getElement());
			}
		} else if (blockElement instanceof Repeat) {
			Repeat repeat = (Repeat) blockElement;
			registerTransition(ast, classDeclaration, defineTransitionsMethod, controlflow.getSource(), repeat
					.getExecutes());
			workflowElements.add(repeat.getExecutes());
		}
	}

	private static void identifyBlockMembers(Map<BlockPattern, List<WorkflowElement>> blockMembers,
			WorkflowElement wfElement) {
		if (wfElement instanceof ExclusiveChoiceMerge) {
			ExclusiveChoiceMerge ecm = (ExclusiveChoiceMerge) wfElement;
			ConditionalBranch defaultBranch = ecm.getDeafultBranch();
			Set<ConditionalBranch> conditionalBranches = ecm.listConditionalBranches();

			if (!blockMembers.containsKey(ecm)) {
				List<WorkflowElement> list = new ArrayList<WorkflowElement>();
				blockMembers.put(ecm, list);
			}
			if (defaultBranch != null) {
				blockMembers.get(ecm).add(defaultBranch.getElement());
			}
			for (ConditionalBranch conditionalBranch : conditionalBranches) {
				blockMembers.get(ecm).add(conditionalBranch.getElement());
			}
		} else if (wfElement instanceof ParallelSplitSynchronise) {
			ParallelSplitSynchronise pss = (ParallelSplitSynchronise) wfElement;
			Set<UnconditionalBranch> unconditionalBranches = pss.listBranches();

			if (!blockMembers.containsKey(pss)) {
				List<WorkflowElement> list = new ArrayList<WorkflowElement>();
				blockMembers.put(pss, list);
			}
			for (UnconditionalBranch unconditionalBranch : unconditionalBranches) {
				blockMembers.get(pss).add(unconditionalBranch.getElement());
			}
		} else if (wfElement instanceof MultipleChoiceMultiMerge) {
			MultipleChoiceMultiMerge mcmm = (MultipleChoiceMultiMerge) wfElement;
			Set<ConditionalBranch> conditionalBranches = mcmm.listConditionalBranches();

			if (!blockMembers.containsKey(mcmm)) {
				List<WorkflowElement> list = new ArrayList<WorkflowElement>();
				blockMembers.put(mcmm, list);
			}
			for (ConditionalBranch conditionalBranch : conditionalBranches) {
				blockMembers.get(mcmm).add(conditionalBranch.getElement());
			}
		} else if (wfElement instanceof Repeat) {
			Repeat repeat = (Repeat) wfElement;
			if (!blockMembers.containsKey(repeat)) {
				List<WorkflowElement> list = new ArrayList<WorkflowElement>();
				blockMembers.put(repeat, list);
			}
			blockMembers.get(repeat).add(repeat.getExecutes());
		}
	}

	@SuppressWarnings("unchecked")
	private static void createTaskExecutionMethod(AST ast, TypeDeclaration td, WorkflowElement workflowElement) {
		MethodDeclaration md = ast.newMethodDeclaration();
		String taskName = GeneratorUtils.getNameOfElement(workflowElement);
		taskName = Character.toUpperCase(taskName.charAt(0)) + taskName.substring(1);
		md.setName(ast.newSimpleName("execute" + taskName));
		md.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PROTECTED_KEYWORD));
		td.bodyDeclarations().add(md);
		// cu.types().add(td);

		Block block = ast.newBlock();
		md.setBody(block);

		addSystemOut(ast, block, "Task executed : " + taskName);
	}

	@SuppressWarnings("unchecked")
	private static void createSubFlowExecutionMethod(AST ast, TypeDeclaration td, WorkflowElement workflowElement) {
		MethodDeclaration md = ast.newMethodDeclaration();
		String taskName = GeneratorUtils.getNameOfElement(workflowElement);
		taskName = Character.toUpperCase(taskName.charAt(0)) + taskName.substring(1);
		md.setName(ast.newSimpleName("execute" + taskName));
		md.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PROTECTED_KEYWORD));
		SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
		param.setName(ast.newSimpleName("sbflw"));
		param.setType(ast.newSimpleType(ast.newSimpleName("Subflow")));
		md.parameters().add(param);
		td.bodyDeclarations().add(md);
		// cu.types().add(td);

		Block block = ast.newBlock();
		md.setBody(block);

		MethodInvocation mi = ast.newMethodInvocation();
		mi.setName(ast.newSimpleName("performSubflow"));
		mi.arguments().add(ast.newSimpleName("sbflw"));
		ExpressionStatement methodCall = ast.newExpressionStatement(mi);
		block.statements().add(methodCall);
	}

	// @SuppressWarnings("unchecked")
	// private static void createGoalExecutionMethod(AST ast, TypeDeclaration td, WorkflowElement workflowElement) {
	// MethodDeclaration md = ast.newMethodDeclaration();
	// String taskName = GeneratorUtils.getNameOfElement(workflowElement);
	// taskName = Character.toUpperCase(taskName.charAt(0)) + taskName.substring(1);
	// md.setName(ast.newSimpleName("execute" + taskName));
	// md.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PROTECTED_KEYWORD));
	// Block block = ast.newBlock();
	// md.setBody(block);
	//		
	// callDiscoveryProcess(ast, taskName, block);
	// callSelectProcess(ast, taskName, block);
	//		
	// SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
	// param.setName(ast.newSimpleName("sbflw"));
	// param.setType(ast.newSimpleType(ast.newSimpleName("Subflow")));
	// md.parameters().add(param);
	// td.bodyDeclarations().add(md);
	//		
	// MethodInvocation mi = ast.newMethodInvocation();
	// mi.setName(ast.newSimpleName("performSubflow"));
	// mi.arguments().add(ast.newSimpleName("process"));
	// ExpressionStatement methodCall = ast.newExpressionStatement(mi);
	// block.statements().add(methodCall);
	// }

	@SuppressWarnings("unchecked")
	private static void createGoalExecutionMethod(AST ast, TypeDeclaration td, WorkflowElement workflowElement) {
		MethodDeclaration md = ast.newMethodDeclaration();
		String taskName = GeneratorUtils.getNameOfElement(workflowElement);
		taskName = Character.toUpperCase(taskName.charAt(0)) + taskName.substring(1);
		md.setName(ast.newSimpleName("execute" + taskName));
		md.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PROTECTED_KEYWORD));
		Block block = ast.newBlock();
		md.setBody(block);

		// init subflow prop
		Assignment assignment = ast.newAssignment();
		VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName("process"));
		VariableDeclarationExpression variableDeclaration = ast.newVariableDeclarationExpression(fragment);
		variableDeclaration.setType(ast.newSimpleType(ast.newSimpleName("String")));
		assignment.setLeftHandSide(variableDeclaration);
		assignment.setRightHandSide(ast.newNullLiteral());
		ExpressionStatement assignmentExp = ast.newExpressionStatement(assignment);
		block.statements().add(assignmentExp);

		// try catch
		TryStatement tryStatement = ast.newTryStatement();
		Block body = ast.newBlock();
		GoalTaskImpl goalTask = (GoalTaskImpl) workflowElement;
		initInputs(ast, body, goalTask);
		initOutputs(ast, body, goalTask);

		callDiscoveryProcess(ast, body, goalTask.getWsmoGoal().getIdentifier().toString());
		callSelectProcess(ast, taskName, body);
		createSubFlow(ast, body);
		callPerformSubFlow(ast, td, md, body);
		tryStatement.setBody(body);

		block.statements().add(tryStatement);

		CatchClause catchClause = ast.newCatchClause();
		SingleVariableDeclaration exDecl = ast.newSingleVariableDeclaration();
		exDecl.setType(ast.newSimpleType(ast.newSimpleName("Exception")));
		exDecl.setName(ast.newSimpleName("e"));
		catchClause.setException(exDecl);

		Block catchBody = ast.newBlock();
		addToBlackList(ast, catchBody);
		MethodInvocation callRecursive = ast.newMethodInvocation();
		callRecursive.setName(ast.newSimpleName("execute" + taskName));
		callRecursive.arguments().add(ast.newSimpleName("sbflw"));
		ExpressionStatement methodCall = ast.newExpressionStatement(callRecursive);
		catchBody.statements().add(methodCall);

		catchClause.setBody(catchBody);

		tryStatement.catchClauses().add(catchClause);
	}

	/**
	 * Subflow subflow = new Subflow(sbflw.getActivity());
	 * subflow.setSubflowId("tr.edu.itu.bpmo.demo.PurchaseCreditCardProcess");
	 * 
	 * @param ast
	 * @param body
	 */
	private static void createSubFlow(AST ast, Block body) {
		Assignment assignment = ast.newAssignment();
		VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName("subflow"));
		VariableDeclarationExpression variableDeclaration = ast.newVariableDeclarationExpression(fragment);
		variableDeclaration.setType(ast.newSimpleType(ast.newSimpleName("Subflow")));
		assignment.setLeftHandSide(variableDeclaration);

		ClassInstanceCreation newSubflowInstance = ast.newClassInstanceCreation();
		newSubflowInstance.setType(ast.newSimpleType(ast.newSimpleName("Subflow")));
		
		MethodInvocation methodInvocation = ast.newMethodInvocation();
		methodInvocation.setExpression(ast.newName("sbflw"));
		methodInvocation.setName(ast.newSimpleName("getActivity"));
		newSubflowInstance.arguments().add(methodInvocation);
		
		assignment.setRightHandSide(newSubflowInstance);
		ExpressionStatement assignmentExp = ast.newExpressionStatement(assignment);
		body.statements().add(assignmentExp);
		
//		 subflow.setSubflowId(process)
		MethodInvocation methodCall = ast.newMethodInvocation();
		methodCall.setExpression(ast.newName("subflow"));
		methodCall.setName(ast.newSimpleName("setSubflowId"));
		methodCall.arguments().add(ast.newSimpleName("process"));
		body.statements().add(ast.newExpressionStatement(methodCall));
	}

	private static void initInputs(AST ast, Block body, GoalTaskImpl goalTask) {
		Assignment assignment = ast.newAssignment();
		VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName("inputs"));
		VariableDeclarationExpression variableDeclaration = ast.newVariableDeclarationExpression(fragment);
		variableDeclaration.setType(ast.newSimpleType(ast.newSimpleName("List")));
		assignment.setLeftHandSide(variableDeclaration);

		ClassInstanceCreation newAlInstance = ast.newClassInstanceCreation();
		newAlInstance.setType(ast.newSimpleType(ast.newSimpleName("ArrayList")));
		assignment.setRightHandSide(newAlInstance);
		ExpressionStatement assignmentExp = ast.newExpressionStatement(assignment);
		body.statements().add(assignmentExp);

		for (DataTypeEntity input : goalTask.listDataInputs()) {
			if (input.getContentType() != null) {
				MethodInvocation methodInvocation = ast.newMethodInvocation();
				methodInvocation.setExpression(ast.newName("inputs"));
				methodInvocation.setName(ast.newSimpleName("add"));
				StringLiteral inputValue = ast.newStringLiteral();
				inputValue.setLiteralValue(input.getContentType().toString());
				methodInvocation.arguments().add(inputValue);
				body.statements().add(ast.newExpressionStatement(methodInvocation));
			}
		}
	}

	private static void initOutputs(AST ast, Block body, GoalTaskImpl goalTask) {
		Assignment assignment = ast.newAssignment();
		VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName("outputs"));
		VariableDeclarationExpression variableDeclaration = ast.newVariableDeclarationExpression(fragment);
		variableDeclaration.setType(ast.newSimpleType(ast.newSimpleName("List")));
		assignment.setLeftHandSide(variableDeclaration);

		ClassInstanceCreation newAlInstance = ast.newClassInstanceCreation();
		newAlInstance.setType(ast.newSimpleType(ast.newSimpleName("ArrayList")));
		assignment.setRightHandSide(newAlInstance);
		ExpressionStatement assignmentExp = ast.newExpressionStatement(assignment);
		body.statements().add(assignmentExp);

		for (DataTypeEntity output : goalTask.listDataOutputs()) {
			if (output.getContentType() != null) {
				MethodInvocation methodInvocation = ast.newMethodInvocation();
				methodInvocation.setExpression(ast.newName("outputs"));
				methodInvocation.setName(ast.newSimpleName("add"));
				StringLiteral outputValue = ast.newStringLiteral();
				outputValue.setLiteralValue(output.getContentType().toString());
				methodInvocation.arguments().add(outputValue);
				ExpressionStatement outputlistAdd = ast.newExpressionStatement(methodInvocation);
				body.statements().add(outputlistAdd);
			}
		}
	}

	private static void addToBlackList(AST ast, Block catchBody) {
		MethodInvocation methodInvocation = ast.newMethodInvocation();
		methodInvocation.setExpression(ast.newName("blackList"));
		methodInvocation.setName(ast.newSimpleName("add"));
		methodInvocation.arguments().add(ast.newSimpleName("process"));
		ExpressionStatement blaclistAdd = ast.newExpressionStatement(methodInvocation);
		catchBody.statements().add(blaclistAdd);
	}

	private static void callPerformSubFlow(AST ast, TypeDeclaration td, MethodDeclaration md, Block block) {
		SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
		param.setName(ast.newSimpleName("sbflw"));
		param.setType(ast.newSimpleType(ast.newSimpleName("Subflow")));
		md.parameters().add(param);
		td.bodyDeclarations().add(md);

		MethodInvocation mi = ast.newMethodInvocation();
		mi.setName(ast.newSimpleName("performSubflow"));
		mi.arguments().add(ast.newSimpleName("subflow"));
		ExpressionStatement methodCall = ast.newExpressionStatement(mi);
		block.statements().add(methodCall);
	}

	@SuppressWarnings("unchecked")
	private static void callSelectProcess(AST ast, String taskName, Block block) {
		Assignment assignment = ast.newAssignment();
		assignment.setLeftHandSide(ast.newSimpleName("process"));

		MethodInvocation selectMi = ast.newMethodInvocation();
		selectMi.setName(ast.newSimpleName("selectProcess"));
		selectMi.setExpression(ast.newSimpleName("Self"));
		selectMi.arguments().add(ast.newSimpleName("processes"));
		assignment.setRightHandSide(selectMi);
		ExpressionStatement assignmentExp = ast.newExpressionStatement(assignment);
		block.statements().add(assignmentExp);
	}

	@SuppressWarnings("unchecked")
	private static void callDiscoveryProcess(AST ast, Block block, String goal) {
		Assignment assignment = ast.newAssignment();
		VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName("processes"));
		VariableDeclarationExpression variableDeclaration = ast.newVariableDeclarationExpression(fragment);
		variableDeclaration.setType(ast.newSimpleType(ast.newSimpleName("List")));
		assignment.setLeftHandSide(variableDeclaration);

		MethodInvocation discoveryMi = ast.newMethodInvocation();
		discoveryMi.setName(ast.newSimpleName("discoverProcesses"));
		discoveryMi.setExpression(ast.newSimpleName("ProcessDiscovery"));
		StringLiteral paramLiteral = ast.newStringLiteral();
		paramLiteral.setLiteralValue(goal);
		discoveryMi.arguments().add(paramLiteral);
		discoveryMi.arguments().add(ast.newSimpleName("inputs"));
		discoveryMi.arguments().add(ast.newSimpleName("outputs"));
		discoveryMi.arguments().add(ast.newSimpleName("blackList"));
		assignment.setRightHandSide(discoveryMi);
		ExpressionStatement assignmentExp = ast.newExpressionStatement(assignment);
		block.statements().add(assignmentExp);
	}

	@SuppressWarnings("unchecked")
	private static void addSystemOut(AST ast, Block block, String msg) {
		MethodInvocation methodInvocation = ast.newMethodInvocation();
		QualifiedName name = ast.newQualifiedName(ast.newSimpleName("System"), ast.newSimpleName("out"));
		methodInvocation.setExpression(name);
		methodInvocation.setName(ast.newSimpleName("println"));
		StringLiteral stringLiteral = ast.newStringLiteral();
		stringLiteral.setLiteralValue(msg);
		methodInvocation.arguments().add(stringLiteral);
		ExpressionStatement methodCall = ast.newExpressionStatement(methodInvocation);
		block.statements().add(methodCall);
	}

	/**
	 * registerTransition(new Transition(),"myTask1","task2");
	 */
	private static void registerTransition(AST ast, TypeDeclaration td, Block defineTransitionsMethod,
			ControlflowConnector controlflow) {
		registerTransition(ast, td, defineTransitionsMethod, controlflow.getSource(), controlflow.getTarget());
	}

	/**
	 * registerTransition(new Transition(),"myTask1","task2");
	 */
	private static void registerTransition(AST ast, TypeDeclaration td, Block defineTransitionsMethod,
			WorkflowElement source, WorkflowElement target) {
		registerTransition(ast, td, defineTransitionsMethod, source, target, null);
	}

	@SuppressWarnings("unchecked")
	private static void registerTransition(AST ast, TypeDeclaration td, Block defineTransitionsMethod,
			WorkflowElement source, WorkflowElement target, Condition condition) {
		if (!(target instanceof EndEvent)) {
			MethodInvocation mi = ast.newMethodInvocation();
			mi.setName(ast.newSimpleName("registerTransition"));
			ClassInstanceCreation newClassInstance = ast.newClassInstanceCreation();
			newClassInstance.setType(ast.newSimpleType(ast.newSimpleName("Transition")));
			if (condition != null) {
				String conditionStr = condition.getExpression();
				conditionStr = WordUtils.capitalize(conditionStr);
				conditionStr = conditionStr.replace(" ", "");
				conditionStr = Character.toUpperCase(conditionStr.charAt(0)) + conditionStr.substring(1);

				StringLiteral stringLiteral = ast.newStringLiteral();
				stringLiteral.setLiteralValue(conditionStr);
				newClassInstance.arguments().add(stringLiteral);
				newClassInstance.arguments().add(ast.newThisExpression());

				createConditionControlMethod(ast, td, conditionStr);
			}
			mi.arguments().add(newClassInstance);
			StringLiteral sourceName = ast.newStringLiteral();
			sourceName.setLiteralValue(GeneratorUtils.getNameOfElementCapitalized(source));
			mi.arguments().add(sourceName);
			StringLiteral targetName = ast.newStringLiteral();
			targetName.setLiteralValue(GeneratorUtils.getNameOfElementCapitalized(target));
			mi.arguments().add(targetName);
			ExpressionStatement methodCall = ast.newExpressionStatement(mi);
			defineTransitionsMethod.statements().add(methodCall);
		} else {
			lastTasks.add(source);
		}
	}

	@SuppressWarnings("unchecked")
	private static void createConditionControlMethod(AST ast, TypeDeclaration td, String conditionStr) {
		MethodDeclaration md = ast.newMethodDeclaration();

		md.setName(ast.newSimpleName("check" + conditionStr));
		md.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PROTECTED_KEYWORD));
		md.setReturnType2(ast.newPrimitiveType(PrimitiveType.BOOLEAN));
		td.bodyDeclarations().add(md);
		// cu.types().add(td);

		Block block = ast.newBlock();
		md.setBody(block);
	}

	/**
	 * CodeExecutionBehaviour myTask1=new CodeExecutionBehaviour("myTask1",this);
	 * <p>
	 * registerActivity(myTask1,INITIAL);
	 */
	@SuppressWarnings("unchecked")
	private static void registerActivity(AST ast, Block block, WorkflowElement workflowElement, boolean isFirstTask,
			boolean isLastTask) {
		String elementName = GeneratorUtils.getNameOfElement(workflowElement);
		String elementType = GeneratorUtils.convertToWadeWfElementType(workflowElement);

		Assignment assignment = ast.newAssignment();
		VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName(elementName));
		VariableDeclarationExpression variableDeclaration = ast.newVariableDeclarationExpression(fragment);
		variableDeclaration.setType(ast.newSimpleType(ast.newSimpleName(elementType)));
		assignment.setLeftHandSide(variableDeclaration);
		ClassInstanceCreation newClassInstance = ast.newClassInstanceCreation();
		newClassInstance.setType(ast.newSimpleType(ast.newSimpleName(elementType)));
		StringLiteral stringLiteral = ast.newStringLiteral();
		stringLiteral.setLiteralValue(GeneratorUtils.getNameOfElementCapitalized(workflowElement));
		newClassInstance.arguments().add(stringLiteral);
		newClassInstance.arguments().add(ast.newThisExpression());
		if (workflowElement instanceof SubProcess) {
			newClassInstance.arguments().add(ast.newBooleanLiteral(false));
		}
		assignment.setRightHandSide(newClassInstance);

		ExpressionStatement assignmentExp = ast.newExpressionStatement(assignment);
		block.statements().add(assignmentExp);

		if (workflowElement instanceof SubProcess || workflowElement instanceof GoalTask) {
			MethodInvocation methodInvocation = ast.newMethodInvocation();
			methodInvocation.setExpression(ast.newName(elementName));
			methodInvocation.setName(ast.newSimpleName("setSubflow"));
			StringLiteral param = ast.newStringLiteral();
			param.setLiteralValue(GeneratorUtils.getNameOfElementCapitalized(workflowElement));
			methodInvocation.arguments().add(param);
			ExpressionStatement methodCall = ast.newExpressionStatement(methodInvocation);
			block.statements().add(methodCall);
		}

		MethodInvocation mi = ast.newMethodInvocation();
		mi.setName(ast.newSimpleName("registerActivity"));
		mi.arguments().add(ast.newSimpleName(elementName));
		if (isFirstTask) {
			mi.arguments().add(ast.newSimpleName("INITIAL"));
		}
		if (isLastTask) {
			mi.arguments().add(ast.newSimpleName("FINAL"));
		}
		ExpressionStatement methodCall = ast.newExpressionStatement(mi);
		block.statements().add(methodCall);
	}

	@SuppressWarnings("unchecked")
	private static void declareField(AST ast, TypeDeclaration td) {
		StringLiteral initializerExpression = ast.newStringLiteral();
		initializerExpression.setLiteralValue("ASD");
		VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName("MY_CONSTANT"));
		fragment.setInitializer(initializerExpression);
		FieldDeclaration newFieldDeclaration = ast.newFieldDeclaration(fragment);
		newFieldDeclaration.setType(ast.newSimpleType(ast.newSimpleName("String")));
		newFieldDeclaration.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
		newFieldDeclaration.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
		newFieldDeclaration.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.FINAL_KEYWORD));

		td.bodyDeclarations().add(newFieldDeclaration);
	}

	@SuppressWarnings("unchecked")
	private static Block createDefineTransitionsMethod(AST ast, TypeDeclaration td) {
		MethodDeclaration md = ast.newMethodDeclaration();
		md.setName(ast.newSimpleName("defineTransitions"));
		md.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
		td.bodyDeclarations().add(md);
		// cu.types().add(td);

		Block block = ast.newBlock();
		md.setBody(block);
		return block;
	}

	@SuppressWarnings("unchecked")
	private static Block createDefineActivitiesMethod(AST ast, TypeDeclaration td) {
		MethodDeclaration md = ast.newMethodDeclaration();
		md.setName(ast.newSimpleName("defineActivities"));
		md.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
		td.bodyDeclarations().add(md);
		// cu.types().add(td);

		Block block = ast.newBlock();
		md.setBody(block);
		return block;
	}

	@SuppressWarnings("unchecked")
	private static TypeDeclaration createClass(Process process, AST ast, CompilationUnit cu) {
		TypeDeclaration td = ast.newTypeDeclaration();
		td.setName(ast.newSimpleName(GeneratorUtils.getProcessName(process)));
		td.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
		td.setSuperclassType(ast.newSimpleType(ast.newSimpleName("WorkflowBehaviour")));
		cu.types().add(td);
		return td;
	}

	private static void setPackageName(String targetPath, AST ast, CompilationUnit cu) {
		PackageDeclaration p1 = ast.newPackageDeclaration();
		p1.setName(ast.newName(GeneratorUtils.getPackageName(targetPath)));
		cu.setPackage(p1);
	}

	@SuppressWarnings("unchecked")
	private static void addStaticImports(AST ast, CompilationUnit cu) {
		ImportDeclaration id_alist = ast.newImportDeclaration();
		id_alist.setName(ast.newName(new String[] { "java", "util", "ArrayList" }));
		cu.imports().add(id_alist);
		ImportDeclaration id_list = ast.newImportDeclaration();
		id_list.setName(ast.newName(new String[] { "java", "util", "List" }));
		cu.imports().add(id_list);
		ImportDeclaration id_map = ast.newImportDeclaration();
		id_map.setName(ast.newName(new String[] { "java", "util", "Map" }));
		cu.imports().add(id_map);
		ImportDeclaration id = ast.newImportDeclaration();
		id.setName(ast.newName(new String[] { "com", "tilab", "wade", "performer", "CodeExecutionBehaviour" }));
		cu.imports().add(id);
		ImportDeclaration id2 = ast.newImportDeclaration();
		id2.setName(ast.newName(new String[] { "com", "tilab", "wade", "performer", "Transition" }));
		cu.imports().add(id2);
		ImportDeclaration id3 = ast.newImportDeclaration();
		id3.setName(ast.newName(new String[] { "com", "tilab", "wade", "performer", "WorkflowBehaviour" }));
		cu.imports().add(id3);
		ImportDeclaration id4 = ast.newImportDeclaration();
		id4.setName(ast.newName(new String[] { "tr", "edu", "itu", "owls2bpmo", "matcher", "ProcessDiscovery" }));
		cu.imports().add(id4);
		ImportDeclaration id5 = ast.newImportDeclaration();
		id5.setName(ast.newName(new String[] { "tr", "edu", "itu", "owls2bpmo", "selection", "Self" }));
		cu.imports().add(id5);
	}

}
