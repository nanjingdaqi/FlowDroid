package soot.jimple.infoflow.android.callbacks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Body;
import soot.Local;
import soot.MethodOrMethodContext;
import soot.PackManager;
import soot.RefType;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.Ref;
import soot.jimple.Stmt;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.callbacks.CallbackDefinition.CallbackType;
import soot.jimple.infoflow.android.callbacks.filters.ICallbackFilter;
import soot.jimple.infoflow.android.entryPointCreators.AndroidEntryPointConstants;
import soot.jimple.infoflow.android.entryPointCreators.AndroidEntryPointUtils;
import soot.jimple.infoflow.memory.IMemoryBoundedSolver;
import soot.jimple.infoflow.memory.ISolverTerminationReason;
import soot.jimple.infoflow.util.SystemClassHandler;
import soot.jimple.internal.JIdentityStmt;
import soot.jimple.internal.JInvokeStmt;
import soot.util.Chain;
import soot.util.HashMultiMap;
import soot.util.MultiMap;
import soot.util.queue.QueueReader;

import static soot.jimple.infoflow.android.SetupApplication.L;

/**
 * Default implementation of the callback analyzer class. This implementation
 * aims for precision. It tries to rule out callbacks registered in unreachable
 * code. The mapping between components and callbacks is as precise as possible.
 * 
 * @author Steven Arzt
 *
 */
public class DefaultCallbackAnalyzer extends AbstractCallbackAnalyzer implements IMemoryBoundedSolver {

	private MultiMap<SootClass, SootMethod> callbackWorklist = null;
	private AndroidEntryPointUtils entryPointUtils = new AndroidEntryPointUtils();
	private Set<IMemoryBoundedSolverStatusNotification> notificationListeners = new HashSet<>();
	private ISolverTerminationReason isKilled = null;

	public DefaultCallbackAnalyzer(InfoflowAndroidConfiguration config, Set<SootClass> entryPointClasses)
			throws IOException {
		super(config, entryPointClasses);
	}

	public DefaultCallbackAnalyzer(InfoflowAndroidConfiguration config, Set<SootClass> entryPointClasses,
			String callbackFile) throws IOException {
		super(config, entryPointClasses, callbackFile);
	}

	public DefaultCallbackAnalyzer(InfoflowAndroidConfiguration config, Set<SootClass> entryPointClasses,
			Set<String> androidCallbacks) throws IOException {
		super(config, entryPointClasses, androidCallbacks);
	}

	public void hookActiveBody(Body b) {
		SootMethod m = b.getMethod();
		JInvokeStmt bindUnit = null;
		for (Unit u : b.getUnits()) {
			if (u instanceof JInvokeStmt) {
				JInvokeStmt stmt = (JInvokeStmt) u;
				SootMethod sm2 = stmt.getInvokeExpr().getMethod();
				if (sm2.getDeclaringClass().getName().equals("butterknife.ButterKnife")
						&& sm2.getName().equals("bind")) {
					logger.debug("daqi - find bind invoke: " + stmt);
					logger.debug("arg type: " + stmt.getInvokeExpr().getArg(0).getType());
					bindUnit = stmt;
					break;
				}
			}
		}

		if (bindUnit == null) return;

		Body newBd = Jimple.v().newBody(m);
		logger.info("daqi - old body: " + b);
		for (Local local : b.getLocals()) {
			logger.info("daqi - l: " + local);
			newBd.getLocals().add(local);
		}
		LocalGenerator lg = new LocalGenerator(newBd);
		for (Unit u : b.getUnits()) {
			if (u != bindUnit) {
				logger.info("daqi - unit 0: " + u);
				newBd.getUnits().add(u);
			} else {
				logger.info("daqi - unit 1: " + u);
				String cmpName = bindUnit.getInvokeExpr().getArg(0).getType().toString();
				String clsName =  cmpName+ "_" + "ViewBinding";
				SootClass sc = Scene.v().getSootClass(clsName);
				SootMethod sm3 = sc.getMethod("void <init>(" +  cmpName + ",android.view.View)");
				Local l0 = lg.generateLocal(RefType.v(sc));
				Stmt st = Jimple.v().newAssignStmt(l0, Jimple.v().newNewExpr(RefType.v(sc)));
				newBd.getUnits().addLast(st);
				Local l1 = lg.generateLocal(RefType.v(Scene.v().getSootClass(cmpName)));
				st = Jimple.v().newIdentityStmt(l1, Jimple.v().newThisRef(RefType.v(Scene.v().getSootClass(cmpName))));
				newBd.getUnits().add(st);
				Local l2 = lg.generateLocal(RefType.v(Scene.v().getSootClass("android.view.Window")));
				st = Jimple.v().newAssignStmt(l2,
						Jimple.v().newVirtualInvokeExpr(l1,
								Scene.v().makeMethodRef(Scene.v().getSootClass(cmpName), "getWindow",
										new ArrayList<Type>(), RefType.v(Scene.v().getSootClass("android.view.Window")), false)));
				newBd.getUnits().add(st);
				Local l3 = lg.generateLocal(RefType.v(Scene.v().getSootClass("android.view.View")));
				st = Jimple.v().newAssignStmt(l3,
						Jimple.v().newVirtualInvokeExpr(l2,
								Scene.v().makeMethodRef(Scene.v().getSootClass("android.view.Window"),
										"getDecorView", new ArrayList<Type>(), RefType.v(Scene.v().getSootClass("android.view.View")), false)));
				newBd.getUnits().add(st);
				List<Value> args = new ArrayList<>();
				args.add(l1);
				args.add(l3);
				newBd.getUnits().add(Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(l0, sm3.makeRef(), args)));
			}
		}
		logger.info("daqi: replace the body for: " + m);
		logger.info("daqi - new body: " + newBd);
        m.setActiveBody(newBd);
	}

	/**
	 * Collects the callback methods for all Android default handlers implemented in
	 * the source code. Note that this operation runs inside Soot, so this method
	 * only registers a new phase that will be executed when Soot is next run
	 */
	@Override
	public void collectCallbackMethods() {
		super.collectCallbackMethods();

		Transform t1 = new Transform("wjtp.daqi", new SceneTransformer() {
			@Override
			protected void internalTransform(String phaseName, Map<String, String> options) {
				logger.info("daqi - " + "running wjtp.daqi transform, cls sz: " + Scene.v().getClasses().size());
				for (SootClass cls : Scene.v().getClasses()) {
					if (SystemClassHandler.isClassInSystemPackage(cls.getType())) continue;
					logger.info("daqi - " + "handling none system cls: " + cls.getName());
                    String clsName = cls.getName();
                    String viewBindingClsName = clsName + "_ViewBinding";
                    SootClass viewBindingCls = Scene.v().getSootClassUnsafe(viewBindingClsName, false);
                    if (viewBindingCls != null) {
                    	logger.info("daqi - " + "find a butterknife bind class");
						for (SootMethod mtd : cls.getMethods()) {
							logger.info("daqi - " + "handling mtd: " + mtd + ", has active mtd: " + mtd.hasActiveBody());
                            hookActiveBody(mtd.retrieveActiveBody());
						}
					}
				}
			}
		});
		// Fail, 原因是此时的cg已经建立起来，再修改body，对reachable_mtd不会有影响
//		PackManager.v().getPack("wjtp").add(t1);


		Transform transform = new Transform("wjtp.ajc", new SceneTransformer() {
			@Override
			protected void internalTransform(String phaseName, @SuppressWarnings("rawtypes") Map options) {
				// Notify the listeners that the solver has been started
				for (IMemoryBoundedSolverStatusNotification listener : notificationListeners)
					listener.notifySolverStarted(DefaultCallbackAnalyzer.this);

				// Do we have to start from scratch or do we have a worklist to
				// process?
				if (callbackWorklist == null) {
					logger.info("Collecting callbacks in DEFAULT mode...");
					callbackWorklist = new HashMultiMap<>();

					// Find the mappings between classes and layouts
					findClassLayoutMappings();

					// Process the callback classes directly reachable from the
					// entry points
					/** reachable is true
					Chain<SootClass> clses = Scene.v().getClasses();
					for (SootClass cls : clses) {
						if (!cls.getName().contains("org.peace")) continue;
						L("Handling cls: " + cls);
                        for (SootMethod sm : cls.getMethods()) {
                        	L("Handling sm: " + sm + ", body: " + sm.hasActiveBody());
						}
					}
					 **/

					for (SootClass sc : entryPointClasses) {
						// Check whether we're still running
						if (isKilled != null)
							break;

						List<MethodOrMethodContext> methods = new ArrayList<MethodOrMethodContext>(
								getLifecycleMethods(sc));

						logger.info("daqi - collect cbs: " + sc + ", mt sz: " + methods.size());
						// Check for callbacks registered in the code
						analyzeRechableMethods(sc, methods);

						// Check for method overrides
						analyzeMethodOverrideCallbacks(sc);
					}
					logger.info("Callback analysis done.");
				} else {
					// Incremental mode, only process the worklist
					logger.info(String.format("Running incremental callback analysis for %d components...",
							callbackWorklist.size()));
					for (Iterator<SootClass> classIt = callbackWorklist.keySet().iterator(); classIt.hasNext();) {
						// Check whether we're still running
						if (isKilled != null)
							break;

						SootClass componentClass = classIt.next();
						Set<SootMethod> callbacks = callbackWorklist.get(componentClass);

						// Check whether we're already beyond the maximum number
						// of callbacks
						// for the current component
						if (config.getCallbackConfig().getMaxCallbacksPerComponent() > 0
								&& callbacks.size() > config.getCallbackConfig().getMaxCallbacksPerComponent()) {
							callbackMethods.remove(componentClass);
							entryPointClasses.remove(componentClass);
							classIt.remove();
							continue;
						}

						List<MethodOrMethodContext> entryClasses = new ArrayList<>(callbacks.size());
						for (SootMethod sm : callbacks)
							entryClasses.add(sm);

						analyzeRechableMethods(componentClass, entryClasses);
						classIt.remove();
					}
					logger.info("Incremental callback analysis done.");
				}

				// Notify the listeners that the solver has been terminated
				for (IMemoryBoundedSolverStatusNotification listener : notificationListeners)
					listener.notifySolverTerminated(DefaultCallbackAnalyzer.this);
			}

		});
		PackManager.v().getPack("wjtp").add(transform);
	}

	/**
	 * Gets all lifecycle methods in the given entry point class
	 * 
	 * @param sc
	 *            The class in which to look for lifecycle methods
	 * @return The set of lifecycle methods in the given class
	 */
	private Collection<? extends MethodOrMethodContext> getLifecycleMethods(SootClass sc) {
		switch (entryPointUtils.getComponentType(sc)) {
		case Activity:
			return getLifecycleMethods(sc, AndroidEntryPointConstants.getActivityLifecycleMethods());
		case Service:
			return getLifecycleMethods(sc, AndroidEntryPointConstants.getServiceLifecycleMethods());
		case Application:
			return getLifecycleMethods(sc, AndroidEntryPointConstants.getApplicationLifecycleMethods());
		case BroadcastReceiver:
			return getLifecycleMethods(sc, AndroidEntryPointConstants.getBroadcastLifecycleMethods());
		case Fragment:
			return getLifecycleMethods(sc, AndroidEntryPointConstants.getFragmentLifecycleMethods());
		case ContentProvider:
			return getLifecycleMethods(sc, AndroidEntryPointConstants.getContentproviderLifecycleMethods());
		case GCMBaseIntentService:
			return getLifecycleMethods(sc, AndroidEntryPointConstants.getGCMIntentServiceMethods());
		case GCMListenerService:
			return getLifecycleMethods(sc, AndroidEntryPointConstants.getGCMListenerServiceMethods());
		case ServiceConnection:
			return getLifecycleMethods(sc, AndroidEntryPointConstants.getServiceConnectionMethods());
		case Plain:
			return Collections.emptySet();
		}
		return Collections.emptySet();
	}

	/**
	 * This method takes a lifecycle class and the list of lifecycle method
	 * subsignatures. For each subsignature, it checks whether the given class or
	 * one of its superclass overwrites the respective methods. All findings are
	 * collected in a set and returned.
	 * 
	 * @param sc
	 *            The class in which to look for lifecycle method implementations
	 * @param methods
	 *            The list of lifecycle method subsignatures for the type of
	 *            component that the given class corresponds to
	 * @return The set of implemented lifecycle methods in the given class
	 */
	private Collection<? extends MethodOrMethodContext> getLifecycleMethods(SootClass sc, List<String> methods) {
		Set<MethodOrMethodContext> lifecycleMethods = new HashSet<>();
		SootClass currentClass = sc;
		logger.info("daqi - getLifecyleMtds: " + sc + ", " + System.identityHashCode(sc));
		while (currentClass != null) {
			for (String sig : methods) {
				SootMethod sm = currentClass.getMethodUnsafe(sig);
				if (sm != null)
					if (!SystemClassHandler.isClassInSystemPackage(sm.getDeclaringClass().getName()))
						lifecycleMethods.add(sm);
			}
			currentClass = currentClass.hasSuperclass() ? currentClass.getSuperclass() : null;
		}
		return lifecycleMethods;
	}

	private void analyzeRechableMethods(SootClass lifecycleElement, List<MethodOrMethodContext> methods) {
		// Make sure to exclude all other edges in the callgraph except for the
		// edges start in the lifecycle methods we explicitly pass in
        logger.info("daqi - analyzeRechableMethods: " + lifecycleElement + ", methods sz: " + methods.size());
		ComponentReachableMethods rm = new ComponentReachableMethods(config, lifecycleElement, methods);
		rm.update();

//		List<MethodOrMethodContext> finalMts = new LinkedList<>();
//		QueueReader<MethodOrMethodContext> mts = rm.listener();
//		while (mts.hasNext()) {
//            SootMethod sm = mts.next().method();
//				logger.info("daqi - sm 1: " + sm);
//				logger.info("daqi - body 1: " + sm.retrieveActiveBody());
//			finalMts.add(sm);
//		}

//		rm = new ComponentReachableMethods(config, lifecycleElement, methods);
//		rm.update();

		// Scan for listeners in the class hierarchy
		QueueReader<MethodOrMethodContext> reachableMethods = rm.listener();
//        Iterator<MethodOrMethodContext> reachableMethods = finalMts.iterator();
		while (reachableMethods.hasNext()) {
			// Check whether we're still running
			if (isKilled != null)
				break;

			for (ICallbackFilter filter : callbackFilters)
				filter.setReachableMethods(rm);

			SootMethod method = reachableMethods.next().method();
			logger.info("daqi - reachable mt 1: " + method + ", " + System.identityHashCode(method));
			analyzeMethodForCallbackRegistrations(lifecycleElement, method);
			analyzeMethodForDynamicBroadcastReceiver(method);
			analyzeMethodForServiceConnection(method);
			analyzeMethodForFragmentTransaction(lifecycleElement, method);
		}
	}

	@Override
	protected boolean checkAndAddMethod(SootMethod method, SootMethod parentMethod, SootClass lifecycleClass,
			CallbackType callbackType) {
		if (!this.excludedEntryPoints.contains(lifecycleClass)) {
			if (super.checkAndAddMethod(method, parentMethod, lifecycleClass, callbackType)) {
				// Has this entry point been excluded?
				this.callbackWorklist.put(lifecycleClass, method);
				return true;
			}
		}
		return false;
	}

	/**
	 * Finds the mappings between classes and their respective layout files
	 */
	private void findClassLayoutMappings() {
		Iterator<MethodOrMethodContext> rmIterator = Scene.v().getReachableMethods().listener();
		while (rmIterator.hasNext()) {
			SootMethod sm = rmIterator.next().method();
			if (!sm.isConcrete())
				continue;
			if (SystemClassHandler.isClassInSystemPackage(sm.getDeclaringClass().getName()))
				continue;

			for (Unit u : sm.retrieveActiveBody().getUnits())
				if (u instanceof Stmt) {
					Stmt stmt = (Stmt) u;
					if (stmt.containsInvokeExpr()) {
						InvokeExpr inv = stmt.getInvokeExpr();
						if (invokesSetContentView(inv) || invokesInflate(inv)) { // check
																					// also
																					// for
																					// inflate
																					// to
																					// look
																					// for
																					// the
																					// fragments
							for (Value val : inv.getArgs()) {
								Integer intValue = valueProvider.getValue(sm, stmt, val, Integer.class);
								if (intValue != null)
									this.layoutClasses.put(sm.getDeclaringClass(), intValue);
							}
						}
					}
				}
		}
	}

	@Override
	public void forceTerminate(ISolverTerminationReason reason) {
		this.isKilled = reason;
	}

	@Override
	public boolean isTerminated() {
		return isKilled != null;
	}

	@Override
	public boolean isKilled() {
		return isKilled != null;
	}

	@Override
	public void reset() {
		this.isKilled = null;
	}

	@Override
	public void addStatusListener(IMemoryBoundedSolverStatusNotification listener) {
		this.notificationListeners.add(listener);
	}

	@Override
	public void excludeEntryPoint(SootClass entryPoint) {
		super.excludeEntryPoint(entryPoint);
		this.callbackWorklist.remove(entryPoint);
		this.callbackMethods.remove(entryPoint);
	}

	@Override
	public ISolverTerminationReason getTerminationReason() {
		return isKilled;
	}

}
