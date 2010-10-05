/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.ins.object;

import java.lang.reflect.*;
import java.util.*;

import com.sun.max.ins.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.tele.object.*;
import com.sun.max.vm.actor.holder.*;

/**
 * Creates and manages canonical instances of {@link ObjectInspector} for
 * objects in the heap of the VM.
 *
 * @author Michael Van De Vanter
 */
public final class ObjectInspectorFactory extends AbstractInspectionHolder {

    private static ObjectInspectorFactory factory;

    /**
     * Creates the singleton factory that listens for events and will find or create instances
     * of {@link ObjectInspector} as needed.
     */
    public static ObjectInspectorFactory make(final Inspection inspection) {
        if (factory == null) {
            factory = new ObjectInspectorFactory(inspection);
        }
        return factory;
    }

    /**
     * Map:   {@link TeleObject} -- > the {@link ObjectInspector}, if it exists, for the corresponding
     * object in the VM.  Relies on {@link ObjectInspector}s being canonical.
     */
    private final Map<TeleObject, ObjectInspector> teleObjectToInspector = new HashMap<TeleObject, ObjectInspector>();

    /**
     * ObjectInspector constructors for specific tuple-implemented subclasses of {@link TeleObject}s.
     * The most specific class that matches a particular {@link TeleObject} will
     * be used, in an emulation of virtual method dispatch.
     */
    private final Map<Class, Constructor> teleTupleObjectClassToObjectInspectorConstructor = new HashMap<Class, Constructor>();

    /**
     * ObjectInspector constructors for specific array-implemented subclasses of {@link TeleObject}s.
     * The most specific class that matches a particular array component type will
     * be used, in an emulation of virtual method dispatch.
     */
    private final Map<Class, Constructor> arrayComponentClassToObjectInspectorConstructor = new HashMap<Class, Constructor>();

    private final Constructor defaultArrayInspectorConstructor;
    private final Constructor defaultTupleInspectorConstructor;

    private ObjectInspectorFactory(final Inspection inspection) {
        super(inspection);
        Trace.begin(1, tracePrefix() + "initializing");

        // Use this if there is no subclass of array component type is matched, or if the component type is an interface.
        defaultArrayInspectorConstructor = getConstructor(ArrayInspector.class);
        // Array inspectors for specific subclasses of component type
        arrayComponentClassToObjectInspectorConstructor.put(Character.class, getConstructor(CharacterArrayInspector.class));

        // Use this if there is no object type subclass matched
        defaultTupleInspectorConstructor = getConstructor(TupleInspector.class);
        // Tuple inspectors for specific subclasses
        teleTupleObjectClassToObjectInspectorConstructor.put(TeleDescriptor.class, getConstructor(DescriptorInspector.class));
        teleTupleObjectClassToObjectInspectorConstructor.put(TeleEnum.class, getConstructor(EnumInspector.class));
        teleTupleObjectClassToObjectInspectorConstructor.put(TeleString.class, getConstructor(StringInspector.class));
        teleTupleObjectClassToObjectInspectorConstructor.put(TeleStringConstant.class, getConstructor(StringConstantInspector.class));
        teleTupleObjectClassToObjectInspectorConstructor.put(TeleUtf8Constant.class, getConstructor(Utf8ConstantInspector.class));

        focus().addListener(new InspectionFocusAdapter() {

            @Override
            public void heapObjectFocusChanged(TeleObject oldTeleObject, TeleObject teleObject) {
                if (teleObject != null) {
                    factory.makeObjectInspector(inspection, teleObject);
                }
            }
        });
        Trace.end(1, tracePrefix() + "initializing");
    }

    private void makeObjectInspector(Inspection inspection, TeleObject teleObject) {
        ObjectInspector objectInspector =  teleObjectToInspector.get(teleObject);
        if (objectInspector == null) {
            switch (teleObject.kind()) {
                case HYBRID: {
                    objectInspector = new HubInspector(inspection, this, teleObject);
                    break;
                }
                case TUPLE: {
                    Constructor constructor = lookupInspectorConstructor(teleTupleObjectClassToObjectInspectorConstructor, teleObject.getClass());
                    if (constructor == null) {
                        constructor = defaultTupleInspectorConstructor;
                    }
                    try {
                        objectInspector = (ObjectInspector) constructor.newInstance(inspection, this, teleObject);
                    } catch (InstantiationException e) {
                        throw ProgramError.unexpected(e);
                    } catch (IllegalAccessException e) {
                        throw ProgramError.unexpected(e);
                    } catch (InvocationTargetException e) {
                        throw ProgramError.unexpected(e.getTargetException());
                    }
                    break;
                }
                case ARRAY: {
                    ClassActor componentClassActor = teleObject.classActorForObjectType().componentClassActor();
                    if (componentClassActor.isPrimitiveClassActor()) {
                        final PrimitiveClassActor primitiveClassActor = (PrimitiveClassActor) componentClassActor;
                        componentClassActor = primitiveClassActor.toWrapperClassActor();
                    }
                    Constructor constructor = lookupInspectorConstructor(arrayComponentClassToObjectInspectorConstructor, componentClassActor.toJava());
                    if (constructor == null) {
                        constructor = defaultArrayInspectorConstructor;
                    }
                    try {
                        objectInspector = (ObjectInspector) constructor.newInstance(inspection, this, teleObject);
                    } catch (InstantiationException e) {
                        throw ProgramError.unexpected();
                    } catch (IllegalAccessException e) {
                        throw ProgramError.unexpected();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                        throw ProgramError.unexpected();
                    }
                    break;
                }
            }
            if (objectInspector != null) {
                teleObjectToInspector.put(teleObject, objectInspector);
            }
        }
        if (objectInspector != null) {
            objectInspector.highlight();
        }
    }

    private Constructor getConstructor(Class clazz) {
        return Classes.getDeclaredConstructor(clazz, Inspection.class, ObjectInspectorFactory.class, TeleObject.class);
    }

    private Constructor lookupInspectorConstructor(Map<Class, Constructor> map, Class clazz) {
        Class javaClass = clazz;
        while (javaClass != null) {
            final Constructor constructor = map.get(javaClass);
            if (constructor != null) {
                return constructor;
            }
            javaClass = javaClass.getSuperclass();
        }
        return null;
    }

    void objectInspectorClosing(ObjectInspector objectInspector) {
        teleObjectToInspector.remove(objectInspector.teleObject());
    }

    public boolean isObjectInspectorObservingObject(long oid) {
        for (TeleObject teleObject : teleObjectToInspector.keySet()) {
            if (teleObject.reference().makeOID() == oid) {
                return true;
            }
        }
        return false;
    }

    public void resetObjectToInspectorMapEntry(TeleObject oldTeleObject, TeleObject newTeleObject, ObjectInspector objectInspector) {
        teleObjectToInspector.remove(oldTeleObject);
        teleObjectToInspector.put(newTeleObject, objectInspector);
    }

    /**
     * @return all existing instances of {@link ObjectInspector}, even if hidden or iconic.
     */
    public Set<ObjectInspector> inspectors() {
        return new HashSet<ObjectInspector>(teleObjectToInspector.values());
    }
}
