package org.camunda.bpm.engine.test.api.runtime;

import static org.camunda.bpm.engine.test.api.runtime.migration.ModifiableBpmnModelInstance.modify;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * @author Svetlana Dorokhova.
 */
public class ProcessInstanceModificationSubProcessTest {

  protected ProcessEngineRule rule = new ProvidedProcessEngineRule();
  protected ProcessEngineTestRule testHelper = new ProcessEngineTestRule(rule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(rule).around(testHelper);

  private RuntimeService runtimeService;
  private RepositoryService repositoryService;
  private TaskService taskService;

  @Before
  public void init() {
    repositoryService = rule.getRepositoryService();
    runtimeService = rule.getRuntimeService();
    taskService = rule.getTaskService();
  }

  @Test
  public void shouldCompleteParentProcess() {
    final BpmnModelInstance parentProcessInstance =
      Bpmn.createExecutableProcess("parentProcess")
        .startEvent()
          .callActivity("callActivity").calledElement("subprocess")
        .endEvent()
        .done();

    final BpmnModelInstance subprocessInstance =
      Bpmn.createExecutableProcess("subprocess")
        .startEvent()
          .userTask("userTask")
        .endEvent("subEnd")
        .done();

    testHelper.deploy(parentProcessInstance, subprocessInstance);

    // given I start the process, which wait as user task in subprocess
    runtimeService.startProcessInstanceByKey("parentProcess");

    assertNotNull(taskService.createTaskQuery().taskName("userTask").singleResult());

    final ProcessInstance subprocess = runtimeService.createProcessInstanceQuery().processDefinitionKey("subprocess").singleResult();
    assertNotNull(subprocess);

    // when I do process instance modification
    runtimeService.createProcessInstanceModification(
      subprocess.getProcessInstanceId())
      .cancelAllForActivity("userTask")
      .startAfterActivity("userTask")
      .execute();

    // then the process should be finished
    assertThat(runtimeService.createProcessInstanceQuery().count(), is(0L));
  }

  @Test
  public void shouldContinueParentProcess() {
    final BpmnModelInstance parentProcessInstance =
      Bpmn.createExecutableProcess("parentProcess")
        .startEvent()
        .callActivity("callActivity").calledElement("subprocess")
        .userTask()
        .endEvent()
        .done();

    final BpmnModelInstance subprocessInstance =
      Bpmn.createExecutableProcess("subprocess")
        .startEvent()
          .userTask("userTask")
        .endEvent("subEnd")
        .done();

    testHelper.deploy(parentProcessInstance, subprocessInstance);

    // given I start the process, which wait as user task in subprocess
    ProcessInstance parentPI = runtimeService.startProcessInstanceByKey("parentProcess");

    assertNotNull(taskService.createTaskQuery().taskName("userTask").singleResult());

    final ProcessInstance subprocess = runtimeService.createProcessInstanceQuery().processDefinitionKey("subprocess").singleResult();
    assertNotNull(subprocess);

    // when I do process instance modification
    runtimeService.createProcessInstanceModification(
      subprocess.getProcessInstanceId())
      .cancelAllForActivity("userTask")
      .startAfterActivity("userTask")
      .execute();

    // then the parent process instance is still active
    assertThat(runtimeService.createProcessInstanceQuery().count(), is(1L));

    Task task = taskService.createTaskQuery().singleResult();
    assertThat(task.getProcessInstanceId(), is(parentPI.getId()));
  }

  @Test
  public void shouldCompleteParentProcessWithParallelGateway() {

    final BpmnModelInstance modelInstance =
      Bpmn.createExecutableProcess("parentProcess").startEvent()
        .parallelGateway()
          .serviceTask("doNothingServiceTask").camundaExpression("${true}")
        .moveToLastGateway()
          .callActivity("callActivity").calledElement("subprocess")
        .parallelGateway("mergingParallelGateway")
      .endEvent()
      .done();

    final BpmnModelInstance parentProcessInstance =
      modify(modelInstance)
        .flowNodeBuilder("doNothingServiceTask").connectTo("mergingParallelGateway").done();

    final BpmnModelInstance subprocessInstance =
        Bpmn.createExecutableProcess("subprocess")
          .startEvent()
            .userTask("userTask")
          .endEvent("subEnd")
          .done();

    testHelper.deploy(parentProcessInstance, subprocessInstance);

    // given I start the process, which waits at user task in subprocess
    runtimeService.startProcessInstanceByKey("parentProcess");

    final ProcessInstance subprocess = runtimeService.createProcessInstanceQuery().processDefinitionKey("subprocess").singleResult();
    assertNotNull(subprocess);

    assertNotNull(taskService.createTaskQuery().taskName("userTask").singleResult());

    // when I do process instance modification
    runtimeService.createProcessInstanceModification(
      subprocess.getProcessInstanceId())
      .cancelAllForActivity("userTask")
      .startAfterActivity("userTask")
      .execute();

    // then the process should be finished
    assertThat(runtimeService.createProcessInstanceQuery().count(), is(0L));

  }

  @Test
  public void shouldContinueParentProcessWithParallelGateway() {

    final BpmnModelInstance modelInstance =
      Bpmn.createExecutableProcess("parentProcess").startEvent()
        .parallelGateway()
          .serviceTask("doNothingServiceTask").camundaExpression("${true}")
        .moveToLastGateway()
          .callActivity("callActivity").calledElement("subprocess")
        .parallelGateway("mergingParallelGateway")
        .userTask()
      .endEvent()
      .done();

    final BpmnModelInstance parentProcessInstance =
      modify(modelInstance)
        .flowNodeBuilder("doNothingServiceTask").connectTo("mergingParallelGateway").done();

    final BpmnModelInstance subprocessInstance =
        Bpmn.createExecutableProcess("subprocess")
          .startEvent()
            .userTask("userTask")
          .endEvent("subEnd")
          .done();

    testHelper.deploy(parentProcessInstance, subprocessInstance);

    // given I start the process, which waits at user task in subprocess
    ProcessInstance parentPI = runtimeService.startProcessInstanceByKey("parentProcess");

    final ProcessInstance subprocess = runtimeService.createProcessInstanceQuery().processDefinitionKey("subprocess").singleResult();
    assertNotNull(subprocess);

    assertNotNull(taskService.createTaskQuery().taskName("userTask").singleResult());

    // when I do process instance modification
    runtimeService.createProcessInstanceModification(
      subprocess.getProcessInstanceId())
      .cancelAllForActivity("userTask")
      .startAfterActivity("userTask")
      .execute();

    // then the parent process instance is still active
    assertThat(runtimeService.createProcessInstanceQuery().count(), is(1L));

    Task task = taskService.createTaskQuery().singleResult();
    assertThat(task.getProcessInstanceId(), is(parentPI.getId()));

  }

  @Test
  public void shouldCompleteParentProcessWithMultiInstance() {

    final BpmnModelInstance parentProcessInstance =
      Bpmn.createExecutableProcess("parentProcess")
        .startEvent()
          .callActivity("callActivity").calledElement("subprocess")
            .multiInstance().cardinality("3").multiInstanceDone()
        .endEvent()
        .done();

    final BpmnModelInstance subprocessInstance =
      Bpmn.createExecutableProcess("subprocess")
        .startEvent()
          .userTask("userTask")
        .endEvent("subEnd")
        .done();

    testHelper.deploy(parentProcessInstance, subprocessInstance);
    final String subprocessPrDefId = repositoryService.createProcessDefinitionQuery().processDefinitionKey("subprocess").singleResult().getId();

    // given I start the process, which waits at user task inside multiinstance subprocess
    runtimeService.startProcessInstanceByKey("parentProcess");

    final List<ProcessInstance> subprocesses = runtimeService.createProcessInstanceQuery().processDefinitionKey("subprocess").list();
    assertEquals(3, subprocesses.size());

    // when I do process instance modification
    runtimeService.createModification(subprocessPrDefId)
      .cancelAllForActivity("userTask")
      .startAfterActivity("userTask")
      .processInstanceIds(collectIds(subprocesses))
      .execute();

    // then the process should be finished
    assertThat(runtimeService.createProcessInstanceQuery().count(), is(0L));

  }

  @Test
  public void shouldContinueParentProcessWithMultiInstance() {

    final BpmnModelInstance parentProcessInstance =
      Bpmn.createExecutableProcess("parentProcess")
        .startEvent()
          .callActivity("callActivity").calledElement("subprocess")
            .multiInstance().cardinality("3").multiInstanceDone()
        .userTask()
        .endEvent()
        .done();

    final BpmnModelInstance subprocessInstance =
      Bpmn.createExecutableProcess("subprocess")
        .startEvent()
          .userTask("userTask")
        .endEvent("subEnd")
        .done();

    testHelper.deploy(parentProcessInstance, subprocessInstance);
    final String subprocessPrDefId = repositoryService.createProcessDefinitionQuery().processDefinitionKey("subprocess").singleResult().getId();

    // given I start the process, which waits at user task inside multiinstance subprocess
    ProcessInstance parentPI = runtimeService.startProcessInstanceByKey("parentProcess");

    final List<ProcessInstance> subprocesses = runtimeService.createProcessInstanceQuery().processDefinitionKey("subprocess").list();
    assertEquals(3, subprocesses.size());

    // when I do process instance modification
    runtimeService.createModification(subprocessPrDefId)
      .cancelAllForActivity("userTask")
      .startAfterActivity("userTask")
      .processInstanceIds(collectIds(subprocesses))
      .execute();

    // then the parent process instance is still active
    assertThat(runtimeService.createProcessInstanceQuery().count(), is(1L));

    Task task = taskService.createTaskQuery().singleResult();
    assertThat(task.getProcessInstanceId(), is(parentPI.getId()));

  }

  @Test
  public void shouldCompleteParentProcessWithMultiInstanceInsideEmbeddedSubProcess() {

    final BpmnModelInstance parentProcessInstance =
        Bpmn.createExecutableProcess("parentProcess")
          .startEvent()
          .subProcess()
            .embeddedSubProcess()
              .startEvent()
              .callActivity("callActivity")
                .calledElement("subprocess")
                .multiInstance()
                .cardinality("3")
                .multiInstanceDone()
              .endEvent()
          .subProcessDone()
          .endEvent()
          .done();

    final BpmnModelInstance subprocessInstance =
      Bpmn.createExecutableProcess("subprocess")
        .startEvent()
          .userTask("userTask")
        .endEvent("subEnd")
        .done();

    testHelper.deploy(parentProcessInstance, subprocessInstance);
    final String subprocessPrDefId = repositoryService.createProcessDefinitionQuery().processDefinitionKey("subprocess").singleResult().getId();

    // given I start the process, which waits at user task inside multiinstance subprocess
    runtimeService.startProcessInstanceByKey("parentProcess");

    final List<ProcessInstance> subprocesses = runtimeService.createProcessInstanceQuery().processDefinitionKey("subprocess").list();
    assertEquals(3, subprocesses.size());

    // when I do process instance modification
    runtimeService.createModification(subprocessPrDefId)
      .cancelAllForActivity("userTask")
      .startAfterActivity("userTask")
      .processInstanceIds(collectIds(subprocesses))
      .execute();

    // then the process should be finished
    assertThat(runtimeService.createProcessInstanceQuery().count(), is(0L));
  }

  @Test
  public void shouldContinueParentProcessWithMultiInstanceInsideEmbeddedSubProcess() {

    final BpmnModelInstance parentProcessInstance =
        Bpmn.createExecutableProcess("parentProcess")
          .startEvent()
          .subProcess()
            .embeddedSubProcess()
              .startEvent()
              .callActivity("callActivity")
                .calledElement("subprocess")
                .multiInstance()
                .cardinality("3")
                .multiInstanceDone()
              .endEvent()
          .subProcessDone()
          .userTask()
          .endEvent()
          .done();

    final BpmnModelInstance subprocessInstance =
      Bpmn.createExecutableProcess("subprocess")
        .startEvent()
          .userTask("userTask")
        .endEvent("subEnd")
        .done();

    testHelper.deploy(parentProcessInstance, subprocessInstance);
    final String subprocessPrDefId = repositoryService.createProcessDefinitionQuery().processDefinitionKey("subprocess").singleResult().getId();

    // given I start the process, which waits at user task inside multiinstance subprocess
    ProcessInstance parentPI = runtimeService.startProcessInstanceByKey("parentProcess");

    final List<ProcessInstance> subprocesses = runtimeService.createProcessInstanceQuery().processDefinitionKey("subprocess").list();
    assertEquals(3, subprocesses.size());

    // when I do process instance modification
    runtimeService.createModification(subprocessPrDefId)
      .cancelAllForActivity("userTask")
      .startAfterActivity("userTask")
      .processInstanceIds(collectIds(subprocesses))
      .execute();

    // then the parent process instance is still active
    assertThat(runtimeService.createProcessInstanceQuery().count(), is(1L));

    Task task = taskService.createTaskQuery().singleResult();
    assertThat(task.getProcessInstanceId(), is(parentPI.getId()));
  }

  @Test
  public void shouldCompleteParentProcessWithMultiInstanceEmbeddedSubProcess() {

    final BpmnModelInstance parentProcessInstance =
        Bpmn.createExecutableProcess("parentProcess")
          .startEvent()
          .subProcess()
            .embeddedSubProcess()
              .startEvent()
              .callActivity("callActivity")
                .calledElement("subprocess")
              .endEvent()
          .subProcessDone()
          .multiInstance()
          .cardinality("3")
          .multiInstanceDone()
          .endEvent()
          .done();

    final BpmnModelInstance subprocessInstance =
      Bpmn.createExecutableProcess("subprocess")
        .startEvent()
          .userTask("userTask")
        .endEvent("subEnd")
        .done();

    testHelper.deploy(parentProcessInstance, subprocessInstance);
    final String subprocessPrDefId = repositoryService.createProcessDefinitionQuery().processDefinitionKey("subprocess").singleResult().getId();

    // given I start the process, which waits at user task inside multiinstance subprocess
    runtimeService.startProcessInstanceByKey("parentProcess");

    final List<ProcessInstance> subprocesses = runtimeService.createProcessInstanceQuery().processDefinitionKey("subprocess").list();
    assertEquals(3, subprocesses.size());

    // when I do process instance modification
    runtimeService.createModification(subprocessPrDefId)
      .cancelAllForActivity("userTask")
      .startAfterActivity("userTask")
      .processInstanceIds(collectIds(subprocesses))
      .execute();

    // then the process should be finished
    assertThat(runtimeService.createProcessInstanceQuery().count(), is(0L));
  }

  @Test
  public void shouldContinueParentProcessWithMultiInstanceEmbeddedSubProcess() {

    final BpmnModelInstance parentProcessInstance =
        Bpmn.createExecutableProcess("parentProcess")
          .startEvent()
          .subProcess()
            .embeddedSubProcess()
              .startEvent()
              .callActivity("callActivity")
                .calledElement("subprocess")
              .endEvent()
          .subProcessDone()
            .multiInstance()
            .cardinality("3")
            .multiInstanceDone()
          .userTask()
          .endEvent()
          .done();

    final BpmnModelInstance subprocessInstance =
      Bpmn.createExecutableProcess("subprocess")
        .startEvent()
          .userTask("userTask")
        .endEvent("subEnd")
        .done();

    testHelper.deploy(parentProcessInstance, subprocessInstance);
    final String subprocessPrDefId = repositoryService.createProcessDefinitionQuery().processDefinitionKey("subprocess").singleResult().getId();

    // given I start the process, which waits at user task inside multiinstance subprocess
    ProcessInstance parentPI = runtimeService.startProcessInstanceByKey("parentProcess");

    final List<ProcessInstance> subprocesses = runtimeService.createProcessInstanceQuery().processDefinitionKey("subprocess").list();
    assertEquals(3, subprocesses.size());

    // when I do process instance modification
    runtimeService.createModification(subprocessPrDefId)
      .cancelAllForActivity("userTask")
      .startAfterActivity("userTask")
      .processInstanceIds(collectIds(subprocesses))
      .execute();

    // then the parent process instance is still active
    assertThat(runtimeService.createProcessInstanceQuery().count(), is(1L));

    Task task = taskService.createTaskQuery().singleResult();
    assertThat(task.getProcessInstanceId(), is(parentPI.getId()));
  }

  private List<String> collectIds(List<ProcessInstance> processInstances) {
    List<String> supbrocessIds = new ArrayList<String>();
    for (ProcessInstance processInstance: processInstances) {
      supbrocessIds.add(processInstance.getId());
    }
    return supbrocessIds;
  }

}
