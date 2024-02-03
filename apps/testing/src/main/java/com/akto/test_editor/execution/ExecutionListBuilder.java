package com.akto.test_editor.execution;

import java.util.ArrayList;
import java.util.List;

import com.akto.dao.test_editor.TestEditorEnums;
import com.akto.dao.test_editor.TestEditorEnums.ExecutorOperandTypes;
import com.akto.dto.test_editor.ExecutionOrderResp;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

public class ExecutionListBuilder {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(Executor.class);

    public ExecutionOrderResp parseExecuteOperations(ExecutorNode node, List<ExecutorNode> executeOrder) {

        String error = null;
        boolean followRedirect = true;
        if (node.getChildNodes().size() < 2) {
            error = "executor child nodes is less than 2, returning empty execution result";
            loggerMaker.errorAndAddToDb(error, LogDb.TESTING);
            return new ExecutionOrderResp(error, true);
        }

        ExecutorNode reqNodes = node.getChildNodes().get(1);
        if (reqNodes.getChildNodes() == null || reqNodes.getChildNodes().size() == 0) {
            error = "executor child nodes is less than 2, returning empty execution result";
            loggerMaker.errorAndAddToDb(error, LogDb.TESTING);
            return new ExecutionOrderResp(error, true);
        }

        if (reqNodes.getChildNodes().get(0).getOperationType().equalsIgnoreCase(TestEditorEnums.ExecutorParentOperands.TYPE.toString())) {
            try {
                followRedirect = Boolean.valueOf(reqNodes.getChildNodes().get(0).getValues().toString());
            } catch (Exception e) {
            }
        }

        buildExecuteOrder(reqNodes.getChildNodes().get(0), executeOrder);
        
        return new ExecutionOrderResp(error, followRedirect);

    }


    public void buildExecuteOrder(ExecutorNode node, List<ExecutorNode> executionOrder) {

        List<ExecutorNode> childNodes = node.getChildNodes();

        if (node.getOperationType().equalsIgnoreCase(TestEditorEnums.ExecutorParentOperands.TYPE.toString()) || 
            node.getOperationType().equalsIgnoreCase(TestEditorEnums.ExecutorOperandTypes.Validate.toString()) || 
            node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.TerminalNonExecutable.toString()) ||
            node.getOperationType().equalsIgnoreCase(TestEditorEnums.TerminalExecutorDataOperands.FOLLOW_REDIRECT.toString())) {
                return;
        }

        if (node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.Terminal.toString()) || 
                node.getNodeType().equalsIgnoreCase(ExecutorOperandTypes.NonTerminal.toString())) {
            executionOrder.add(node);
        }
        ExecutorNode childNode;
        for (int i = 0; i < childNodes.size(); i++) {
            childNode = childNodes.get(i);
            buildExecuteOrder(childNode, executionOrder);
        }
    }

}
