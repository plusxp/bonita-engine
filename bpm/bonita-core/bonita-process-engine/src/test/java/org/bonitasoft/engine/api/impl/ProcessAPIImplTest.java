package org.bonitasoft.engine.api.impl;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bonitasoft.engine.actor.mapping.ActorMappingService;
import org.bonitasoft.engine.actor.mapping.SActorNotFoundException;
import org.bonitasoft.engine.actor.mapping.model.SActor;
import org.bonitasoft.engine.bpm.contract.ContractDefinition;
import org.bonitasoft.engine.bpm.contract.ContractViolationException;
import org.bonitasoft.engine.bpm.contract.Input;
import org.bonitasoft.engine.bpm.contract.impl.ContractDefinitionImpl;
import org.bonitasoft.engine.bpm.contract.impl.InputDefinitionImpl;
import org.bonitasoft.engine.bpm.data.DataInstance;
import org.bonitasoft.engine.bpm.data.impl.IntegerDataInstanceImpl;
import org.bonitasoft.engine.bpm.flownode.FlowNodeExecutionException;
import org.bonitasoft.engine.bpm.flownode.UserTaskNotFoundException;
import org.bonitasoft.engine.bpm.process.ProcessInstanceNotFoundException;
import org.bonitasoft.engine.classloader.ClassLoaderService;
import org.bonitasoft.engine.commons.exceptions.SBonitaException;
import org.bonitasoft.engine.commons.transaction.TransactionExecutor;
import org.bonitasoft.engine.core.data.instance.TransientDataService;
import org.bonitasoft.engine.core.process.definition.ProcessDefinitionService;
import org.bonitasoft.engine.core.process.definition.exception.SProcessDefinitionNotFoundException;
import org.bonitasoft.engine.core.process.definition.exception.SProcessDefinitionReadException;
import org.bonitasoft.engine.core.process.definition.model.SFlowElementContainerDefinition;
import org.bonitasoft.engine.core.process.definition.model.SProcessDefinition;
import org.bonitasoft.engine.core.process.definition.model.SUserTaskDefinition;
import org.bonitasoft.engine.core.process.definition.model.impl.SContractDefinitionImpl;
import org.bonitasoft.engine.core.process.definition.model.impl.SInputDefinitionImpl;
import org.bonitasoft.engine.core.process.instance.api.ActivityInstanceService;
import org.bonitasoft.engine.core.process.instance.api.exceptions.SActivityInstanceNotFoundException;
import org.bonitasoft.engine.core.process.instance.api.exceptions.SActivityReadException;
import org.bonitasoft.engine.core.process.instance.api.exceptions.SFlowNodeNotFoundException;
import org.bonitasoft.engine.core.process.instance.api.exceptions.SProcessInstanceNotFoundException;
import org.bonitasoft.engine.core.process.instance.model.SFlowElementsContainerType;
import org.bonitasoft.engine.core.process.instance.model.SFlowNodeInstance;
import org.bonitasoft.engine.core.process.instance.model.SStateCategory;
import org.bonitasoft.engine.core.process.instance.model.SUserTaskInstance;
import org.bonitasoft.engine.data.instance.api.DataInstanceContainer;
import org.bonitasoft.engine.data.instance.api.DataInstanceService;
import org.bonitasoft.engine.data.instance.exception.SDataInstanceException;
import org.bonitasoft.engine.data.instance.exception.SDataInstanceReadException;
import org.bonitasoft.engine.data.instance.model.SDataInstance;
import org.bonitasoft.engine.dependency.model.ScopeType;
import org.bonitasoft.engine.exception.RetrieveException;
import org.bonitasoft.engine.exception.UpdateException;
import org.bonitasoft.engine.execution.TransactionalProcessInstanceInterruptor;
import org.bonitasoft.engine.lock.BonitaLock;
import org.bonitasoft.engine.lock.LockService;
import org.bonitasoft.engine.recorder.model.EntityUpdateDescriptor;
import org.bonitasoft.engine.scheduler.SchedulerService;
import org.bonitasoft.engine.scheduler.model.SJobParameter;
import org.bonitasoft.engine.service.TenantServiceAccessor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class ProcessAPIImplTest {

    final long tenantId = 1;

    private static final long ACTOR_ID = 100;

    private static final long PROCESS_DEFINITION_ID = 110;

    private static final String ACTOR_NAME = "employee";

    @Mock
    private TenantServiceAccessor tenantAccessor;

    @Mock
    private TransientDataService transientDataService;

    @Mock
    private ActivityInstanceService activityInstanceService;

    @Mock
    private ClassLoaderService classLoaderService;

    private ProcessAPIImpl processAPI;

    @Mock
    private TransactionExecutor transactionExecutor;

    @Mock
    private ContractValidator contractValidator;

    @Mock
    private LockService lockService;

    @Before
    public void setup() {
        processAPI = spy(new ProcessAPIImpl());
        doReturn(tenantAccessor).when(processAPI).getTenantAccessor();
        when(tenantAccessor.getTenantId()).thenReturn(tenantId);
        when(tenantAccessor.getTransientDataService()).thenReturn(transientDataService);
        when(tenantAccessor.getActivityInstanceService()).thenReturn(activityInstanceService);
        when(tenantAccessor.getClassLoaderService()).thenReturn(classLoaderService);
    }

    @Test
    public void cancelAnUnknownProcessInstanceThrowsANotFoundException() throws Exception {
        final long processInstanceId = 45;
        final long userId = 9;
        final LockService lockService = mock(LockService.class);
        final TransactionalProcessInstanceInterruptor interruptor = mock(TransactionalProcessInstanceInterruptor.class);

        when(tenantAccessor.getLockService()).thenReturn(lockService);
        doReturn(userId).when(processAPI).getUserId();
        doReturn(interruptor).when(processAPI).buildProcessInstanceInterruptor(tenantAccessor);
        doThrow(new SProcessInstanceNotFoundException(processInstanceId)).when(interruptor).interruptProcessInstance(processInstanceId,
                SStateCategory.CANCELLING, userId);

        try {
            processAPI.cancelProcessInstance(processInstanceId);
            fail("The process instance does not exists");
        } catch (final ProcessInstanceNotFoundException pinfe) {
            verify(lockService).lock(processInstanceId, SFlowElementsContainerType.PROCESS.name(), tenantId);
            verify(lockService).unlock(any(BonitaLock.class), eq(tenantId));
        }
    }

    @Test
    public void should_updateProcessDataInstance_call_updateProcessDataInstances() throws Exception {
        final long processInstanceId = 42l;
        doNothing().when(processAPI).updateProcessDataInstances(eq(processInstanceId), any(Map.class));

        processAPI.updateProcessDataInstance("foo", processInstanceId, "go");

        verify(processAPI).updateProcessDataInstances(eq(processInstanceId), eq(Collections.<String, Serializable> singletonMap("foo", "go")));
    }

    @Test
    public void should_updateProcessDataInstances_call_DataInstanceService() throws Exception {
        final long processInstanceId = 42l;

        final TenantServiceAccessor tenantAccessor = mock(TenantServiceAccessor.class);
        final DataInstanceService dataInstanceService = mock(DataInstanceService.class);

        doReturn(null).when(processAPI).getProcessInstanceClassloader(any(TenantServiceAccessor.class), anyLong());

        doReturn(tenantAccessor).when(processAPI).getTenantAccessor();
        doReturn(dataInstanceService).when(tenantAccessor).getDataInstanceService();

        final SDataInstance sDataFoo = mock(SDataInstance.class);
        doReturn("foo").when(sDataFoo).getName();
        final SDataInstance sDataBar = mock(SDataInstance.class);
        doReturn("bar").when(sDataBar).getName();
        doReturn(asList(sDataFoo, sDataBar)).when(dataInstanceService).getDataInstances(eq(asList("foo", "bar")), anyLong(), anyString());

        // Then update the data instances
        final Map<String, Serializable> dataNameValues = new HashMap<String, Serializable>();
        dataNameValues.put("foo", "go");
        dataNameValues.put("bar", "go");
        processAPI.updateProcessDataInstances(processInstanceId, dataNameValues);

        // Check that we called DataInstanceService for each pair data/value
        verify(dataInstanceService, times(2)).updateDataInstance(any(SDataInstance.class), any(EntityUpdateDescriptor.class));
        verify(dataInstanceService).updateDataInstance(eq(sDataFoo), any(EntityUpdateDescriptor.class));
        verify(dataInstanceService).updateDataInstance(eq(sDataBar), any(EntityUpdateDescriptor.class));
    }

    @Test
    public void should_updateProcessDataInstances_call_DataInstance_on_non_existing_data_throw_UpdateException() throws Exception {
        final long processInstanceId = 42l;

        final TenantServiceAccessor tenantAccessor = mock(TenantServiceAccessor.class);
        final DataInstanceService dataInstanceService = mock(DataInstanceService.class);

        doReturn(null).when(processAPI).getProcessInstanceClassloader(any(TenantServiceAccessor.class), anyLong());

        doReturn(tenantAccessor).when(processAPI).getTenantAccessor();
        doReturn(dataInstanceService).when(tenantAccessor).getDataInstanceService();

        doThrow(new SDataInstanceReadException("Mocked")).when(dataInstanceService).getDataInstances(eq(asList("foo", "bar")), anyLong(), anyString());

        // Then update the data instances
        final Map<String, Serializable> dataNameValues = new HashMap<String, Serializable>();
        dataNameValues.put("foo", "go");
        dataNameValues.put("bar", "go");
        try {
            processAPI.updateProcessDataInstances(processInstanceId, dataNameValues);
            fail("An exception should have been thrown.");
        } catch (final UpdateException e) {
            // Ok
        }

        // Check that we called DataInstanceService for each pair data/value
        verify(dataInstanceService, never()).updateDataInstance(any(SDataInstance.class), any(EntityUpdateDescriptor.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void replayingAFailedJobNoParamShouldExecuteAgainSchedulerServiceWithNoParameters() throws Exception {
        final long jobDescriptorId = 25L;
        final SchedulerService schedulerService = mock(SchedulerService.class);
        when(tenantAccessor.getSchedulerService()).thenReturn(schedulerService);
        doNothing().when(schedulerService).executeAgain(anyLong(), anyList());

        processAPI.replayFailedJob(jobDescriptorId, null);
        processAPI.replayFailedJob(jobDescriptorId, Collections.EMPTY_MAP);

        verify(schedulerService, times(2)).executeAgain(jobDescriptorId);
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void replayingAFailedJobShouldExecuteAgainSchedulerServiceWithSomeParameters() throws Exception {
        final Map<String, Serializable> parameters = Collections.singletonMap("anyparam", (Serializable) Boolean.FALSE);
        final long jobDescriptorId = 544L;
        final SchedulerService schedulerService = mock(SchedulerService.class);
        when(tenantAccessor.getSchedulerService()).thenReturn(schedulerService);
        doNothing().when(schedulerService).executeAgain(anyLong(), anyList());

        doReturn(new ArrayList()).when(processAPI).getJobParameters(parameters);

        processAPI.replayFailedJob(jobDescriptorId, parameters);

        verify(schedulerService).executeAgain(eq(jobDescriptorId), anyList());
    }

    @Test
    public void replayingAFailedJobWithNoParamShouldCallWithNullParams() throws Exception {
        final long jobDescriptorId = 544L;

        // This spy is specific to this test method:
        final ProcessAPIImpl myProcessAPI = spy(new ProcessAPIImpl());
        doNothing().when(myProcessAPI).replayFailedJob(jobDescriptorId, null);

        myProcessAPI.replayFailedJob(jobDescriptorId);

        verify(myProcessAPI).replayFailedJob(jobDescriptorId, null);
    }

    @Test
    public void getJobParametersShouldConvertMapIntoList() {
        // given:
        final Map<String, Serializable> parameters = new HashMap<String, Serializable>(2);
        final String key1 = "mon param 1";
        final String key2 = "my second param";
        final SJobParameter expectedValue1 = mockSJobParameter(key1);
        parameters.put(expectedValue1.getKey(), expectedValue1.getValue());

        final SJobParameter expectedValue2 = mockSJobParameter(key2);
        parameters.put(expectedValue2.getKey(), expectedValue2.getValue());

        doReturn(expectedValue1).when(processAPI).buildSJobParameter(eq(key1), any(Serializable.class));
        doReturn(expectedValue2).when(processAPI).buildSJobParameter(eq(key2), any(Serializable.class));

        // when:
        final List<SJobParameter> jobParameters = processAPI.getJobParameters(parameters);

        // then:
        assertThat(jobParameters).containsOnly(expectedValue1, expectedValue2);
    }

    private SJobParameter mockSJobParameter(final String key) {
        final SJobParameter jobParam = mock(SJobParameter.class);
        when(jobParam.getKey()).thenReturn(key);
        when(jobParam.getValue()).thenReturn(Integer.MAX_VALUE);
        return jobParam;
    }

    @Test
    public void testGetActivityTransientDataInstances() throws Exception {
        final String dataValue = "TestOfCourse";
        final long activityInstanceId = 13244;
        final String dataName = "TransientName";
        doNothing().when(processAPI).updateTransientData(dataName, activityInstanceId, dataValue, transientDataService);
        final SFlowNodeInstance flowNodeInstance = mock(SFlowNodeInstance.class);
        when(activityInstanceService.getFlowNodeInstance(activityInstanceId)).thenReturn(flowNodeInstance);

        final int nbResults = 100;
        final int startIndex = 0;
        final SDataInstance sDataInstance = mock(SDataInstance.class);
        when(sDataInstance.getClassName()).thenReturn(Integer.class.getName());
        final List<SDataInstance> sDataInstances = Lists.newArrayList(sDataInstance);
        when(transientDataService.getDataInstances(activityInstanceId, DataInstanceContainer.ACTIVITY_INSTANCE.name(), startIndex, nbResults))
        .thenReturn(sDataInstances);
        final IntegerDataInstanceImpl dataInstance = mock(IntegerDataInstanceImpl.class);
        doReturn(Lists.newArrayList(dataInstance)).when(processAPI).convertModelToDataInstances(sDataInstances);

        final List<DataInstance> dis = processAPI.getActivityTransientDataInstances(activityInstanceId, startIndex, nbResults);

        assertThat(dis).contains(dataInstance);

        verify(processAPI, times(1)).convertModelToDataInstances(sDataInstances);
        verify(transientDataService, times(1)).getDataInstances(activityInstanceId, DataInstanceContainer.ACTIVITY_INSTANCE.name(), startIndex, nbResults);
        verify(tenantAccessor, times(1)).getTransientDataService();
        verify(tenantAccessor, times(1)).getClassLoaderService();
        verify(tenantAccessor, times(1)).getActivityInstanceService();
        verify(activityInstanceService, times(1)).getFlowNodeInstance(activityInstanceId);
        verify(flowNodeInstance, times(1)).getLogicalGroup(anyInt());
        verify(classLoaderService, times(1)).getLocalClassLoader(eq(ScopeType.PROCESS.name()), anyInt());
    }

    @Test
    public void testGetActivityTransientDataInstance() throws Exception {
        final String dataValue = "TestOfCourse";
        final int activityInstanceId = 13244;
        final String dataName = "TransientName";
        doNothing().when(processAPI).updateTransientData(dataName, activityInstanceId, dataValue, transientDataService);
        final SFlowNodeInstance flowNodeInstance = mock(SFlowNodeInstance.class);
        when(activityInstanceService.getFlowNodeInstance(activityInstanceId)).thenReturn(flowNodeInstance);

        final SDataInstance sDataInstance = mock(SDataInstance.class);
        when(sDataInstance.getClassName()).thenReturn(Integer.class.getName());
        when(transientDataService.getDataInstance(dataName, activityInstanceId, DataInstanceContainer.ACTIVITY_INSTANCE.name())).thenReturn(sDataInstance);
        final IntegerDataInstanceImpl dataInstance = mock(IntegerDataInstanceImpl.class);
        doReturn(dataInstance).when(processAPI).convertModeltoDataInstance(sDataInstance);

        final DataInstance di = processAPI.getActivityTransientDataInstance(dataName, activityInstanceId);

        assertThat(di).isEqualTo(dataInstance);

        verify(processAPI, times(1)).convertModeltoDataInstance(sDataInstance);
        verify(transientDataService, times(1)).getDataInstance(dataName, activityInstanceId, DataInstanceContainer.ACTIVITY_INSTANCE.name());
        verify(tenantAccessor, times(1)).getTransientDataService();
        verify(tenantAccessor, times(1)).getClassLoaderService();
        verify(tenantAccessor, times(1)).getActivityInstanceService();
        verify(activityInstanceService, times(1)).getFlowNodeInstance(activityInstanceId);
        verify(flowNodeInstance, times(1)).getLogicalGroup(anyInt());
        verify(classLoaderService, times(1)).getLocalClassLoader(eq(ScopeType.PROCESS.name()), anyInt());
    }

    @Test
    public void testUpdateActivityTransientDataInstance_should_call_update() throws Exception {
        final String dataValue = "TestOfCourse";
        final int activityInstanceId = 13244;
        final String dataName = "TransientName";
        doNothing().when(processAPI).updateTransientData(dataName, activityInstanceId, dataValue, transientDataService);
        final SFlowNodeInstance flowNodeInstance = mock(SFlowNodeInstance.class);
        when(activityInstanceService.getFlowNodeInstance(activityInstanceId)).thenReturn(flowNodeInstance);

        processAPI.updateActivityTransientDataInstance(dataName, activityInstanceId, dataValue);

        verify(processAPI).updateTransientData(dataName, activityInstanceId, dataValue, transientDataService);
        verify(tenantAccessor, times(1)).getTransientDataService();
        verify(tenantAccessor, times(1)).getClassLoaderService();
        verify(tenantAccessor, times(1)).getActivityInstanceService();
        verify(activityInstanceService, times(1)).getFlowNodeInstance(activityInstanceId);
        verify(flowNodeInstance, times(1)).getLogicalGroup(anyInt());
        verify(classLoaderService, times(1)).getLocalClassLoader(eq(ScopeType.PROCESS.name()), anyInt());
    }

    @Test(expected = UpdateException.class)
    public void testUpdateActivityTransientDataInstance_should_throw_Exception() throws Exception {
        final String dataValue = "TestOfCourse";
        final int activityInstanceId = 13244;
        final String dataName = "TransientName";
        doThrow(new SDataInstanceException("")).when(processAPI).updateTransientData(dataName, activityInstanceId, dataValue, transientDataService);
        final SFlowNodeInstance flowNodeInstance = mock(SFlowNodeInstance.class);
        when(activityInstanceService.getFlowNodeInstance(activityInstanceId)).thenReturn(flowNodeInstance);

        processAPI.updateActivityTransientDataInstance(dataName, activityInstanceId, dataValue);
    }

    @Test
    public void testUpdateTransientData() throws Exception {
        final String dataValue = "TestOfCourse";
        final int activityInstanceId = 13244;
        final String dataName = "TransientName";
        final SDataInstance sDataInstance = mock(SDataInstance.class);
        when(transientDataService.getDataInstance(dataName, activityInstanceId,
                DataInstanceContainer.ACTIVITY_INSTANCE.toString())).thenReturn(sDataInstance);
        processAPI.updateTransientData(dataName, activityInstanceId, dataValue, transientDataService);
        verify(transientDataService).updateDataInstance(eq(sDataInstance), any(EntityUpdateDescriptor.class));
        verify(transientDataService, times(1)).getDataInstance(dataName, activityInstanceId,
                DataInstanceContainer.ACTIVITY_INSTANCE.toString());
    }

    public void getUserIdsForActor_returns_result_of_actor_mapping_service() throws Exception {
        // given
        final SActor actor = mock(SActor.class);
        when(actor.getId()).thenReturn(ACTOR_ID);

        final ActorMappingService actorMappingService = mock(ActorMappingService.class);
        when(tenantAccessor.getActorMappingService()).thenReturn(actorMappingService);
        when(actorMappingService.getPossibleUserIdsOfActorId(ACTOR_ID, 0, 10)).thenReturn(Arrays.asList(1L, 10L));
        when(actorMappingService.getActor(ACTOR_NAME, PROCESS_DEFINITION_ID)).thenReturn(actor);

        // when
        final List<Long> userIdsForActor = processAPI.getUserIdsForActor(PROCESS_DEFINITION_ID, ACTOR_NAME, 0, 10);

        // then
        assertThat(userIdsForActor).containsExactly(1L, 10L);
    }

    @Test
    public void getUserIdsForActor_throws_RetrieveException_when_actorMappingService_throws_SBonitaException() throws Exception {
        // given
        final SActor actor = mock(SActor.class);
        when(actor.getId()).thenReturn(ACTOR_ID);

        final ActorMappingService actorMappingService = mock(ActorMappingService.class);
        when(tenantAccessor.getActorMappingService()).thenReturn(actorMappingService);
        when(actorMappingService.getActor(ACTOR_NAME, PROCESS_DEFINITION_ID)).thenThrow(new SActorNotFoundException(""));

        // when
        try {
            processAPI.getUserIdsForActor(PROCESS_DEFINITION_ID, ACTOR_NAME, 0, 10);
            fail("Exception expected");
        } catch (final RetrieveException e) {
            // then ok
        }

    }

    @Test
    public void getUserTaskContract_should_return_contract_associated_to_a_given_task() throws Exception {
        final long userTaskInstanceId = 786454L;
        final long processDefinitionId = 464684354L;
        final long userTaskDefinitionId = 786454L;
        final SUserTaskInstance instance = mock(SUserTaskInstance.class);
        final ActivityInstanceService activityInstanceService = mock(ActivityInstanceService.class);
        when(tenantAccessor.getActivityInstanceService()).thenReturn(activityInstanceService);
        when(activityInstanceService.getUserTaskInstance(userTaskInstanceId)).thenReturn(instance);
        when(instance.getProcessDefinitionId()).thenReturn(processDefinitionId);
        when(instance.getFlowNodeDefinitionId()).thenReturn(userTaskDefinitionId);
        final ProcessDefinitionService processDefinitionService = mock(ProcessDefinitionService.class);
        final SProcessDefinition processDefinition = mock(SProcessDefinition.class);
        final SFlowElementContainerDefinition container = mock(SFlowElementContainerDefinition.class);
        final SUserTaskDefinition definition = mock(SUserTaskDefinition.class);
        when(tenantAccessor.getProcessDefinitionService()).thenReturn(processDefinitionService);
        when(processDefinitionService.getProcessDefinition(processDefinitionId)).thenReturn(processDefinition);
        when(processDefinition.getProcessContainer()).thenReturn(container);
        when(container.getFlowNode(userTaskDefinitionId)).thenReturn(definition);
        final SContractDefinitionImpl contractDefinitionImpl = new SContractDefinitionImpl();
        final InputDefinitionImpl input = new InputDefinitionImpl("name", "aType", "aDescription");
        contractDefinitionImpl.addInput(new SInputDefinitionImpl(input));
        when(definition.getContract()).thenReturn(contractDefinitionImpl);
        final ContractDefinitionImpl contractDefinition = new ContractDefinitionImpl();
        contractDefinition.addInput(input);

        final ContractDefinition contract = processAPI.getUserTaskContract(userTaskInstanceId);

        assertThat(contract).isEqualTo(contractDefinition);
    }

    @Test(expected = UserTaskNotFoundException.class)
    public void getUserTaskContractThrowsAExceptionWhenTheIdDoesNotReferToAnExistingTask() throws Exception {
        final long userTaskInstanceId = 786454L;
        final ActivityInstanceService activityInstanceService = mock(ActivityInstanceService.class);
        when(tenantAccessor.getActivityInstanceService()).thenReturn(activityInstanceService);
        when(activityInstanceService.getUserTaskInstance(userTaskInstanceId)).thenThrow(new SActivityInstanceNotFoundException(786454L));

        processAPI.getUserTaskContract(userTaskInstanceId);
    }

    @Test(expected = RetrieveException.class)
    public void getUserTaskContractThrowsAExceptionWhenAnExceptionOccursWhenGettingTheActivity() throws Exception {
        final long userTaskInstanceId = 786454L;
        final ActivityInstanceService activityInstanceService = mock(ActivityInstanceService.class);
        when(tenantAccessor.getActivityInstanceService()).thenReturn(activityInstanceService);
        when(activityInstanceService.getUserTaskInstance(userTaskInstanceId)).thenThrow(new SActivityReadException("ouch !"));

        processAPI.getUserTaskContract(userTaskInstanceId);
    }

    @Test(expected = UserTaskNotFoundException.class)
    public void getUserTaskContractThrowsAExceptionWhenTheIdDoesNotReferToAnExistingProcess() throws Exception {
        final long userTaskInstanceId = 786454L;
        final long processDefinitionId = 464684354L;
        final SUserTaskInstance instance = mock(SUserTaskInstance.class);
        final ActivityInstanceService activityInstanceService = mock(ActivityInstanceService.class);
        when(tenantAccessor.getActivityInstanceService()).thenReturn(activityInstanceService);
        when(activityInstanceService.getUserTaskInstance(userTaskInstanceId)).thenReturn(instance);
        when(instance.getProcessDefinitionId()).thenReturn(processDefinitionId);

        final ProcessDefinitionService processDefinitionService = mock(ProcessDefinitionService.class);
        when(tenantAccessor.getProcessDefinitionService()).thenReturn(processDefinitionService);
        when(processDefinitionService.getProcessDefinition(processDefinitionId)).thenThrow(new SProcessDefinitionNotFoundException("proc"));

        processAPI.getUserTaskContract(userTaskInstanceId);
    }

    @Test(expected = RetrieveException.class)
    public void getUserTaskContractThrowsAExceptionWhenAnExceptionOccursWhenGettingTheProcess() throws Exception {
        final long userTaskInstanceId = 786454L;
        final long processDefinitionId = 464684354L;
        final SUserTaskInstance instance = mock(SUserTaskInstance.class);
        final ActivityInstanceService activityInstanceService = mock(ActivityInstanceService.class);
        when(tenantAccessor.getActivityInstanceService()).thenReturn(activityInstanceService);
        when(activityInstanceService.getUserTaskInstance(userTaskInstanceId)).thenReturn(instance);
        when(instance.getProcessDefinitionId()).thenReturn(processDefinitionId);

        final ProcessDefinitionService processDefinitionService = mock(ProcessDefinitionService.class);
        when(tenantAccessor.getProcessDefinitionService()).thenReturn(processDefinitionService);
        when(processDefinitionService.getProcessDefinition(processDefinitionId)).thenThrow(new SProcessDefinitionReadException("proc"));

        processAPI.getUserTaskContract(userTaskInstanceId);
    }

    //    @CustomTransactions
    //    @Override
    //    public void executeUserTask(final long flownodeInstanceId, final List<Input> inputs) throws FlowNodeExecutionException, ContractViolationException, UserTaskNotFoundException {
    //        executeUserTask(0, flownodeInstanceId, inputs);
    //    }
    //
    //

    @Test(expected = FlowNodeExecutionException.class)
    public void executeUserTask_should_throw_FlowNodeExecutionException() throws Exception {
        final long userId = 1l;
        final long flownodeInstanceId = 2l;
        final List<Input> inputs = new ArrayList<Input>();
        final SBonitaException sBonitaException = new SBonitaException() {

            @Override
            public synchronized Throwable getCause() {
                return new Exception("message");
            }
        };
        //given
        doThrow(sBonitaException).when(processAPI).executeFlowNode(userId, flownodeInstanceId, true, inputs);

        //when
        processAPI.executeUserTask(userId, flownodeInstanceId, inputs);

        //then exception
    }

    @Test(expected = ContractViolationException.class)
    public void executeUserTask_should_throw_ContractViolationException() throws Exception {
        final long userId = 1l;
        final long flownodeInstanceId = 2l;
        final List<Input> inputs = new ArrayList<Input>();
        final SBonitaException sBonitaException = new SBonitaException() {

            @Override
            public synchronized Throwable getCause() {
                return new Exception("message");
            }
        };
        //given
        doThrow(ContractViolationException.class).when(processAPI).executeFlowNode(userId, flownodeInstanceId, true, inputs);

        //when
        processAPI.executeUserTask(userId, flownodeInstanceId, inputs);

        //then exception
    }

    @Test(expected = UserTaskNotFoundException.class)
    public void executeUserTask_should_throw_UserTaskNotFoundException() throws Exception {
        final long userId = 1l;
        final long flownodeInstanceId = 2l;
        final List<Input> inputs = new ArrayList<Input>();
        final SFlowNodeNotFoundException sFlowNodeNotFoundException = new SFlowNodeNotFoundException(flownodeInstanceId);

        //given
        doThrow(sFlowNodeNotFoundException).when(processAPI).executeFlowNode(userId, flownodeInstanceId, true, inputs);

        //when
        processAPI.executeUserTask(userId, flownodeInstanceId, inputs);

        //then exception

    }

}
