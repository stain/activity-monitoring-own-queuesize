package com.example.sonja.sonjatest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.taverna.t2.invocation.InvocationContext;
import net.sf.taverna.t2.lang.observer.Observable;
import net.sf.taverna.t2.lang.observer.Observer;
import net.sf.taverna.t2.monitor.MonitorManager;
import net.sf.taverna.t2.monitor.MonitorableProperty;
import net.sf.taverna.t2.monitor.MonitorManager.MonitorMessage;
import net.sf.taverna.t2.monitor.MonitorManager.RegisterNodeMessage;
import net.sf.taverna.t2.monitor.NoSuchPropertyException;
import net.sf.taverna.t2.reference.ReferenceService;
import net.sf.taverna.t2.reference.T2Reference;
import net.sf.taverna.t2.workflowmodel.Processor;
import net.sf.taverna.t2.workflowmodel.processor.activity.AbstractAsynchronousActivity;
import net.sf.taverna.t2.workflowmodel.processor.activity.ActivityConfigurationException;
import net.sf.taverna.t2.workflowmodel.processor.activity.AsynchronousActivity;
import net.sf.taverna.t2.workflowmodel.processor.activity.AsynchronousActivityCallback;

public class ExampleActivity extends
		AbstractAsynchronousActivity<ExampleActivityConfigurationBean>
		implements AsynchronousActivity<ExampleActivityConfigurationBean> {

	public static class MonitorObserver implements
			Observer<MonitorManager.MonitorMessage> {
		private static final List<String> QUEUE_SIZE = Arrays.asList("dispatch",
				"parallelize", "queuesize");
		private Map<String, MonitorableProperty<Integer>> queueSizePerOwningProcess = new HashMap<String, MonitorableProperty<Integer>>();

		@Override
		public void notify(Observable<MonitorMessage> arg0,
				MonitorMessage msg) throws Exception {
			if (msg instanceof RegisterNodeMessage) {
				RegisterNodeMessage registerNode = (RegisterNodeMessage) msg;
				if (!(registerNode.getWorkflowObject() instanceof Processor)) {
					return;
				}
				Processor processor = (Processor) registerNode
						.getWorkflowObject();
				// Note: This will be the processor object in the RUNNING
				// copy of the workflow
				for (MonitorableProperty<?> prop : registerNode
						.getProperties()) {
					List<String> name = Arrays.asList(prop.getName());
					if (!(name.equals(QUEUE_SIZE))) {
						continue;
					}
					String owning = strJoin(":", registerNode.getOwningProcess());
					System.out.println("Found it for " + owning);

					getQueueSizePerOwningProcess().put(owning,
							(MonitorableProperty<Integer>) prop);
				}
			}
		}

		public static String strJoin(String separator, String... parts) {
			StringBuffer sb = new StringBuffer();
			for (String part : parts) {
				sb.append(part);
				sb.append(separator);
			}
			if (sb.length() > 0) {
				sb.deleteCharAt(sb.length() - 1);
			}
			return sb.toString();
		}

		public Map<String, MonitorableProperty<Integer>> getQueueSizePerOwningProcess() {
			return queueSizePerOwningProcess;
		}

		public void setQueueSizePerOwningProcess(
				Map<String, MonitorableProperty<Integer>> queueSizePerOwningProcess) {
			this.queueSizePerOwningProcess = queueSizePerOwningProcess;
		}
	}

	/*
	 * Best practice: Keep port names as constants to avoid misspelling. This
	 * would not apply if port names are looked up dynamically from the service
	 * operation, like done for WSDL services.
	 */
	private static final String IN_FIRST_INPUT = "firstInput";
	private static final String IN_EXTRA_DATA = "extraData";
	private static final String OUT_MORE_OUTPUTS = "moreOutputs";
	private static final String OUT_SIMPLE_OUTPUT = "simpleOutput";
	private static final String OUT_REPORT = "report";
	
	private ExampleActivityConfigurationBean configBean;
	private MonitorObserver myObserver;

	@Override
	public void configure(ExampleActivityConfigurationBean configBean)
			throws ActivityConfigurationException {

		// Any pre-config sanity checks
		if (configBean.getExampleString().equals("invalidExample")) {
			throw new ActivityConfigurationException(
					"Example string can't be 'invalidExample'");
		}
		// Store for getConfiguration(), but you could also make
		// getConfiguration() return a new bean from other sources
		this.configBean = configBean;

		// OPTIONAL: 
		// Do any server-side lookups and configuration, like resolving WSDLs

		// myClient = new MyClient(configBean.getExampleUri());
		// this.service = myClient.getService(configBean.getExampleString());

		
		// REQUIRED: (Re)create input/output ports depending on configuration
		configurePorts();
		registerMonitorObserver();
		
	}

	protected void registerMonitorObserver() {
		if (myObserver == null) {
			myObserver = new MonitorObserver();		
			MonitorManager.getInstance().addObserver(myObserver);
		}
	}

	@Override
	protected void finalize() throws Throwable {
		// Unregister so it does not hang around forever!
		if (myObserver != null) {
			MonitorManager.getInstance().removeObserver(myObserver);
		}
		myObserver = null;
	}
	
	protected void configurePorts() {
		// In case we are being reconfigured - remove existing ports first
		// to avoid duplicates
		removeInputs();
		removeOutputs();

		// FIXME: Replace with your input and output port definitions
		
		// Hard coded input port, expecting a single String
		addInput(IN_FIRST_INPUT, 0, true, null, String.class);

		// Optional ports depending on configuration
		if (configBean.getExampleString().equals("specialCase")) {
			// depth 1, ie. list of binary byte[] arrays
			addInput(IN_EXTRA_DATA, 1, true, null, byte[].class);
			addOutput(OUT_REPORT, 0);
		}
		
		// Single value output port (depth 0)
		addOutput(OUT_SIMPLE_OUTPUT, 0);
		// Output port with list of values (depth 1)
		addOutput(OUT_MORE_OUTPUTS, 1);

	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void executeAsynch(final Map<String, T2Reference> inputs,
			final AsynchronousActivityCallback callback) {
		// Don't execute service directly now, request to be run ask to be run
		// from thread pool and return asynchronously
		callback.requestRun(new Runnable() {
			
			public void run() {
				InvocationContext context = callback
						.getContext();
				ReferenceService referenceService = context
						.getReferenceService();
				

				// ...
				
				// Strip off the last :invocation53
				String grandParent = callback.getParentProcessIdentifier().replaceFirst(":[^:]*$", "");
				System.out.println("Looking up grand parent " + grandParent );
				MonitorableProperty<Integer> queueSize = myObserver.getQueueSizePerOwningProcess().get(grandParent);
				try {
					System.out.println("My queue size is " + queueSize.getValue());
				} catch (NoSuchPropertyException e) {
					e.printStackTrace();
				}
				
				// Register outputs
				Map<String, T2Reference> outputs = new HashMap<String, T2Reference>();
				String simpleValue = "simple";
				T2Reference simpleRef = referenceService.register(simpleValue, 0, true, context);
				outputs.put(OUT_SIMPLE_OUTPUT, simpleRef);

				// For list outputs, only need to register the top level list
				List<String> moreValues = new ArrayList<String>();
				moreValues.add("Value 1");
				moreValues.add("Value 2");
				T2Reference moreRef = referenceService.register(moreValues, 1, true, context);
				outputs.put(OUT_MORE_OUTPUTS, moreRef);

				
				
				// return map of output data, with empty index array as this is
				// the only and final result (this index parameter is used if
				// pipelining output)
				callback.receiveResult(outputs, new int[0]);
			}
		});
	}

	@Override
	public ExampleActivityConfigurationBean getConfiguration() {
		return this.configBean;
	}

}
