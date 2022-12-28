/* Qilin - a Java Pointer Analysis Framework
 * Copyright (C) 2021-2030 Qilin developers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3.0 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <https://www.gnu.org/licenses/lgpl-3.0.en.html>.
 */

package qilin.core.builder;

import qilin.CoreConfig;
import qilin.core.ArtificialMethod;
import qilin.util.PTAUtils;
import sootup.core.IdentifierFactory;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.ref.JStaticFieldRef;
import sootup.core.model.SootClass;
import sootup.core.model.SootMethod;
import sootup.core.types.ClassType;
import sootup.java.core.JavaIdentifierFactory;
import sootup.java.core.JavaSootMethod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FakeMainFactory extends ArtificialMethod {
    public static FakeMainFactory instance;

    public static int implicitCallEdges;
    private final SootClass fakeClass;

    public FakeMainFactory() {
        this.localStart = 0;
        this.fakeClass = new SootClass("FakeMain");
        this.fakeClass.setResolvingLevel(SootClass.BODIES);
        this.method = new SootMethod("fakeMain", null, VoidType.v());
        this.method.setModifiers(Modifier.STATIC);
        SootField currentThread = new SootField("currentThread", RefType.v("java.lang.Thread"), Modifier.STATIC);
        SootField globalThrow = new SootField("globalThrow", RefType.v("java.lang.Exception"), Modifier.STATIC);
        fakeClass.addMethod(this.method);
        fakeClass.addField(currentThread);
        fakeClass.addField(globalThrow);
    }

    private List<SootMethod> getEntryPoints() {
        List<SootMethod> ret = new ArrayList<>();
        if (CoreConfig.v().getPtaConfig().clinitMode == CoreConfig.ClinitMode.FULL) {
            ret.addAll(EntryPoints.v().clinits());
        } else {
            // on the fly mode, resolve the clinit methods on the fly.
            ret.addAll(Collections.emptySet());
        }

        if (CoreConfig.v().getPtaConfig().singleentry) {
            List<SootMethod> entries = EntryPoints.v().application();
            if (entries.isEmpty()) {
                throw new RuntimeException("Must specify MAINCLASS when appmode enabled!!!");
            } else {
                ret.addAll(entries);
            }
        } else {
            System.out.println("include implicit entry!");
            ret.addAll(EntryPoints.v().application());
            ret.addAll(EntryPoints.v().implicit());
        }
        System.out.println("#EntrySize:" + ret.size());
        return ret;
    }

    public SootMethod getFakeMain() {
        if (body == null) {
            synchronized (this) {
                if (body == null) {
                    this.method.setSource((m, phaseName) -> new JimpleBody(this.method));
                    this.body = PTAUtils.getMethodBody(method);
                    makeFakeMain();
                }
            }
        }
        return this.method;
    }

    public JStaticFieldRef getFieldCurrentThread() {
        return getStaticFieldRef("FakeMain", "currentThread", "java.lang.Thread");
    }

    public JStaticFieldRef getFieldGlobalThrow() {
        return getStaticFieldRef("FakeMain", "globalThrow", "java.lang.Exception");
    }

    private boolean isStaticInitializer (SootMethod method) {
        if (method instanceof JavaSootMethod jm) {
            return jm.isStaticInitializer();
        }
        return false;
    }

    private void makeFakeMain() {
        implicitCallEdges = 0;
        IdentifierFactory idFactory = JavaIdentifierFactory.getInstance();
        for (SootMethod entry : getEntryPoints()) {
            if (entry.isStatic()) {
                if (entry.isMain()) {
                    ClassType classType = (ClassType) idFactory.getType("java.lang.String");
                    Value mockStr = getNew(classType);
                    Local strArray = getNewArray(classType);
                    addAssign(getArrayRef(strArray), mockStr);
                    addInvoke(entry.getSignature().toString(), strArray);
                    implicitCallEdges++;
                } else if (CoreConfig.v().getPtaConfig().clinitMode != CoreConfig.ClinitMode.ONFLY || !isStaticInitializer(entry)) {
                    // in the on fly mode, we won't add a call directly for <clinit> methods.
                    addInvoke(entry.getSignature().toString());
                    implicitCallEdges++;
                }
            }
        }
        if (CoreConfig.v().getPtaConfig().singleentry) {
            return;
        }
        Local sv = getNextLocal(idFactory.getType("java.lang.String"));
        Local mainThread = getNew((ClassType) idFactory.getType("java.lang.Thread"));
        Local mainThreadGroup = getNew((ClassType) idFactory.getType("java.lang.ThreadGroup"));
        Local systemThreadGroup = getNew((ClassType) idFactory.getType("java.lang.ThreadGroup"));

        Value gCurrentThread = getFieldCurrentThread();
        addAssign(gCurrentThread, mainThread); // Store
        Local vRunnable = getNextLocal(idFactory.getType("java.lang.Runnable"));

        Local lThreadGroup = getNextLocal(idFactory.getType("java.lang.ThreadGroup"));
        addInvoke(mainThread, "<java.lang.Thread: void <init>(java.lang.ThreadGroup,java.lang.String)>", mainThreadGroup, sv);
        Value tmpThread = getNew((ClassType) idFactory.getType("java.lang.Thread"));
        addInvoke(tmpThread, "<java.lang.Thread: void <init>(java.lang.ThreadGroup,java.lang.Runnable)>", lThreadGroup, vRunnable);
        addInvoke(tmpThread, "<java.lang.Thread: void exit()>");

        addInvoke(systemThreadGroup, "<java.lang.ThreadGroup: void <init>()>");
        addInvoke(mainThreadGroup, "<java.lang.ThreadGroup: void <init>(java.lang.ThreadGroup,java.lang.String)>", systemThreadGroup, sv);

        Local lThread = getNextLocal(idFactory.getType("java.lang.Thread"));
        Local lThrowable = getNextLocal(idFactory.getType("java.lang.Throwable"));
        Value tmpThreadGroup = getNew((ClassType) idFactory.getType("java.lang.ThreadGroup"));
        addInvoke(tmpThreadGroup, "<java.lang.ThreadGroup: void uncaughtException(java.lang.Thread,java.lang.Throwable)>", lThread, lThrowable); // TODO.


        // ClassLoader
        Value defaultClassLoader = getNew((ClassType) idFactory.getType("sun.misc.Launcher$AppClassLoader"));
        addInvoke(defaultClassLoader, "<java.lang.ClassLoader: void <init>()>");
        Local vClass = getNextLocal(idFactory.getType("java.lang.Class"));
        Local vDomain = getNextLocal(idFactory.getType("java.security.ProtectionDomain"));
        addInvoke(defaultClassLoader, "<java.lang.ClassLoader: java.lang.Class loadClassInternal(java.lang.String)>", sv);
        addInvoke(defaultClassLoader, "<java.lang.ClassLoader: void checkPackageAccess(java.lang.Class,java.security.ProtectionDomain)>", vClass, vDomain);
        addInvoke(defaultClassLoader, "<java.lang.ClassLoader: void addClass(java.lang.Class)>", vClass);

        // PrivilegedActionException
        Value privilegedActionException = getNew((ClassType) idFactory.getType("java.security.PrivilegedActionException"));
        Local gLthrow = getNextLocal(idFactory.getType("java.lang.Exception"));
        addInvoke(privilegedActionException, "<java.security.PrivilegedActionException: void <init>(java.lang.Exception)>", gLthrow);
    }
}