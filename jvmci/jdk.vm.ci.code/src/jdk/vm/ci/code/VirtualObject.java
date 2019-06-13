/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.vm.ci.code;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * An instance of this class represents an object whose allocation was removed by escape analysis.
 * The information stored in the {@link VirtualObject} is used during deoptimization to recreate the
 * object.
 */
public final class VirtualObject implements JavaValue {

    private final ResolvedJavaType type;
    private JavaValue baseObject;
    private JavaValue[] values;
    private JavaKind[] slotKinds;
    private final int id;
    private final boolean isAutoBox;

    /**
     * Creates a new {@link VirtualObject} for the given type, with the given contents. If
     * {@code type} is an instance class then {@link #getValues} provides the values for the fields
     * returned by {@link ResolvedJavaType#getInstanceFields(boolean) getInstanceFields(true)}. If
     * {@code type} is an array then the length of {@link #getValues} determines the reallocated
     * array length.
     *
     * @param type the type of the object whose allocation was removed during compilation. This can
     *            be either an instance of an array type.
     * @param id a unique id that identifies the object within the debug information for one
     *            position in the compiled code.
     * @return a new {@link VirtualObject} instance.
     */
    public static VirtualObject get(ResolvedJavaType type, int id) {
        return new VirtualObject(type, null, id, false);
    }

    /**
     * Creates a new {@link VirtualObject} for the given type, with the given contents. If
     * {@code type} is an instance class then {@link #getValues} provides the values for the fields
     * returned by {@link ResolvedJavaType#getInstanceFields(boolean) getInstanceFields(true)}. If
     * {@code type} is an array then the length of {@link #getValues} determines the reallocated
     * array length.
     *
     * @param type the type of the object whose allocation was removed during compilation. This can
     *            be either an instance of an array type.
     * @param id a unique id that identifies the object within the debug information for one
     *            position in the compiled code.
     * @param isAutoBox a flag that tells the runtime that the object may be a boxed primitive that
     *            needs to be obtained from the box cache instead of creating a new instance.
     * @return a new {@link VirtualObject} instance.
     */
    public static VirtualObject get(ResolvedJavaType type, int id, boolean isAutoBox) {
        return new VirtualObject(type, null, id, isAutoBox);
    }

    /**
     * Creates a new {@link VirtualObject} based on a given existing object, with the given
     * contents. If {@code type} is an instance class then {@link #getValues} provides the values
     * for the fields returned by {@link ResolvedJavaType#getInstanceFields(boolean)
     * getInstanceFields(true)}. If {@code type} is an array then the length of {@link #getValues}
     * determines the array length.
     *
     * @param type the type of the object whose allocation was removed during compilation. This can
     *            be either an instance of an array type.
     * @param baseObject the pre-existing object to be used instead of allocating a new object.
     * @param id a unique id that identifies the object within the debug information for one
     *            position in the compiled code.
     * @param isAutoBox a flag that tells the runtime that the object may be a boxed primitive that
     *            needs to be obtained from the box cache instead of creating a new instance.
     * @return a new {@link VirtualObject} instance.
     */
    public static VirtualObject get(ResolvedJavaType type, JavaValue baseObject, int id, boolean isAutoBox) {
        return new VirtualObject(type, baseObject, id, isAutoBox);
    }

    @NativeImageReinitialize private static boolean boxCachesInitialized;

    private VirtualObject(ResolvedJavaType type, JavaValue baseObject, int id, boolean isAutoBox) {
        this.type = type;
        this.baseObject = baseObject;
        this.id = id;
        this.isAutoBox = isAutoBox;
        if (isAutoBox && !boxCachesInitialized) {
            // Make sure all the primitive box caches are populated.
            // Required for rematerialization during deoptimization.
            Boolean.valueOf(false);
            Short.valueOf((short) 0);
            Character.valueOf((char) 0);
            Integer.valueOf(0);
            Long.valueOf(0);
            boxCachesInitialized = true;
        }
    }

    private static StringBuilder appendValue(StringBuilder buf, JavaValue value, Set<VirtualObject> visited) {
        if (value instanceof VirtualObject) {
            VirtualObject vo = (VirtualObject) value;
            buf.append("vobject:").append(vo.type.toJavaName(false)).append(':').append(vo.id);
            if (!visited.contains(vo)) {
                visited.add(vo);
                if (vo.isAutoBox) {
                    buf.append("[auto_box]");
                }
                buf.append('{');
                if (vo.values == null) {
                    buf.append("<uninitialized>");
                } else {
                    if (vo.type.isArray()) {
                        for (int i = 0; i < vo.values.length; i++) {
                            if (i != 0) {
                                buf.append(',');
                            }
                            buf.append(i).append('=');
                            appendValue(buf, vo.values[i], visited);
                        }
                    } else {
                        ResolvedJavaField[] fields = vo.type.getInstanceFields(true);
                        int fieldIndex = 0;
                        for (int i = 0; i < vo.values.length; i++, fieldIndex++) {
                            if (i != 0) {
                                buf.append(',');
                            }
                            if (fieldIndex >= fields.length) {
                                buf.append("<missing field>");
                            } else {
                                ResolvedJavaField field = fields[fieldIndex];
                                buf.append(field.getName());
                                if (vo.slotKinds[i].getSlotCount() == 2 && field.getType().getJavaKind().getSlotCount() == 1) {
                                    if (fieldIndex + 1 >= fields.length) {
                                        buf.append("/<missing field>");
                                    } else {
                                        ResolvedJavaField field2 = fields[++fieldIndex];
                                        buf.append('/').append(field2.getName());
                                    }
                                }
                            }
                            buf.append('=');
                            appendValue(buf, vo.values[i], visited);
                        }
                        // Extra fields
                        for (; fieldIndex < fields.length; fieldIndex++) {
                            buf.append(fields[fieldIndex].getName()).append("=<missing value>");
                        }
                    }
                }
                buf.append('}');
            }
        } else {
            buf.append(value);
        }
        return buf;
    }

    public interface LayoutVerifier {
        int getOffset(ResolvedJavaField field);

        default JavaKind getStorageKind(ResolvedJavaField field) {
            return field.getType().getJavaKind();
        }
    }

    public void verifyLayout(LayoutVerifier verifier) {
        if (!type.isArray()) {
            ResolvedJavaField[] fields = type.getInstanceFields(true);
            int fieldIndex = 0;
            for (int i = 0; i < values.length; i++, fieldIndex++) {
                JavaKind slotKind = slotKinds[i];
                if (fieldIndex >= fields.length) {
                    throw new JVMCIError("Not enough fields for the values provided for %s", toString());
                } else {
                    ResolvedJavaField field = fields[fieldIndex];
                    JavaKind fieldKind = verifier.getStorageKind(field);
                    if (slotKind.getSlotCount() == 2 && fieldKind == JavaKind.Int) {
                        int offset = verifier.getOffset(field);
                        if (offset % 8 != 0) {
                            throw new JVMCIError("Double word value stored across two ints must be aligned %s", toString());
                        }

                        if (fieldIndex + 1 >= fields.length) {
                            throw new JVMCIError("Missing second field for double word value stored in two ints %s", toString());
                        }
                        ResolvedJavaField field2 = fields[fieldIndex + 1];
                        if (field2.getType().getJavaKind() != JavaKind.Int) {
                            throw new JVMCIError("Second field for double word value stored in two ints must be int but got %s in %s", field2.getType().getJavaKind(), toString());
                        }
                        int offset2 = verifier.getOffset(field2);
                        if (offset + 4 != offset2) {
                            throw new JVMCIError("Double word value stored across two ints must be sequential %s", toString());
                        }
                        fieldIndex++;
                    } else if (fieldKind.getStackKind() != slotKind.getStackKind()) {
                        throw new JVMCIError("Expected value of kind %s but got %s for field %s in %s", fieldKind, slotKind, field, toString());
                    }
                }
            }
            // Extra fields
            if (fieldIndex < fields.length) {
                throw new JVMCIError("Not enough values provided for fields in %s", this);
            }
        }
    }

    @Override
    public String toString() {
        Set<VirtualObject> visited = Collections.newSetFromMap(new IdentityHashMap<VirtualObject, Boolean>());
        return appendValue(new StringBuilder(), this, visited).toString();
    }

    /**
     * Returns the type of the object whose allocation was removed during compilation. This can be
     * either an instance of an array type.
     */
    public ResolvedJavaType getType() {
        return type;
    }

    /**
     * Returns the pre-existing object, or null if a new object needs to be allocated.
     */
    public JavaValue getBaseObject() {
        return baseObject;
    }

    public void setBaseObject(JavaValue baseObject) {
        this.baseObject = baseObject;
    }

    /**
     * Returns the array containing all the values to be stored into the object when it is
     * recreated. This field is intentional exposed as a mutable array that a compiler may modify
     * (e.g. during register allocation).
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "`values` is intentional mutable")//
    public JavaValue[] getValues() {
        return values;
    }

    /**
     * Returns the kind of the value at {@code index}.
     */
    public JavaKind getSlotKind(int index) {
        return slotKinds[index];
    }

    /**
     * Returns the unique id that identifies the object within the debug information for one
     * position in the compiled code.
     */
    public int getId() {
        return id;
    }

    /**
     * Returns true if the object is a box. For boxes, deoptimization will check if the boxed value
     * is in the range guaranteed to be cached by the API. For example, {@link Integer#valueOf(int)}
     * says:
     *
     * "This method will always cache values in the range -128 to 127, inclusive, and may cache
     * other values outside of this range".
     *
     * This means deoptimization must rematerialize a boxed {@code int} within this range by
     * obtaining the box from the cache.
     */
    public boolean isAutoBox() {
        return isAutoBox;
    }

    /**
     * Overwrites the current set of values with a new one.
     *
     * @param values an array containing all the values to be stored into the object when it is
     *            recreated.
     * @param slotKinds an array containing the Java kinds of the values. This must have the same
     *            length as {@code values}. This array is now owned by this object and must not be
     *            mutated by the caller.
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "caller transfers ownership of `slotKinds`")
    public void setValues(JavaValue[] values, JavaKind[] slotKinds) {
        assert values.length == slotKinds.length;
        this.values = values;
        this.slotKinds = slotKinds;
    }

    @Override
    public int hashCode() {
        return 42 + type.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof VirtualObject) {
            VirtualObject l = (VirtualObject) o;
            if (!l.type.equals(type) || l.values.length != values.length) {
                return false;
            }
            for (int i = 0; i < values.length; i++) {
                /*
                 * Virtual objects can form cycles. Calling equals() could therefore lead to
                 * infinite recursion.
                 */
                if (!same(values[i], l.values[i])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean same(Object o1, Object o2) {
        return o1 == o2;
    }
}
