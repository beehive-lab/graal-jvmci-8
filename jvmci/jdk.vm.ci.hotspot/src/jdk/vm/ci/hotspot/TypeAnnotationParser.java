/*
 * Copyright (c) 2015, Aardvark Systems and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * Please contact Aardvark Systems
 * or visit www.aardvark.systems if you need additional information or have any
 * questions.
 */

package jdk.vm.ci.hotspot;


import java.lang.annotation.*;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import static jdk.vm.ci.common.JVMCIError.unimplemented;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.LocalAnnotation;
import sun.reflect.annotation.*;
import sun.reflect.generics.factory.*;
import sun.reflect.generics.parser.*;
import sun.reflect.generics.scope.*;
import sun.reflect.generics.tree.*;
import sun.reflect.generics.visitor.*;

public class TypeAnnotationParser {
    // Position codes
    private static final byte LOCAL_VARIABLE = (byte) 0x40;

    private static int readUnsignedShort(final ByteBuffer bb) {
        return ((bb.get() & 0xff) << 8) | (bb.get() & 0xff);
    }

    public static LocalAnnotation[] parseTypeAnnotations(byte[] bytes, final ConstantPool constantPool, Class<?> container) {
        LocalAnnotation[] result = null;
        List<LocalAnnotation> annotations = new ArrayList<>();
      
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        final int numOffsets = readUnsignedShort(bb);

        for (int annotationIndex = 0; annotationIndex < numOffsets; annotationIndex++) {
            int type = bb.get() & 0xff;
 
            if (type == LOCAL_VARIABLE) {
                int start = 0, length = 0, slot = 0;
                int items = readUnsignedShort(bb);

                for (int j = items; j > 0; --j) {
                    start = readUnsignedShort(bb);
                    length = readUnsignedShort(bb);
                    slot = readUnsignedShort(bb);
                }

                int pathLength = bb.get() & 0xff;
               
                 bb.position(bb.position() + 2 * pathLength);
                Annotation an = readAnnotationValues(bb, constantPool, container);
                LocalAnnotation la = new LocalAnnotation(an, start, length, slot);
                 
                annotations.add(la);
            } else {
                unimplemented(String.format("type annotation: type=0x%x",type));
            }
        }
        result = new LocalAnnotation[annotations.size()];
        annotations.toArray(result);
       
        return result;
    }

    private static Annotation readAnnotationValues(ByteBuffer bb, final ConstantPool constantPool, final Class<?> container) {
        int typeIndex = readUnsignedShort(bb);

        Class<? extends Annotation> annotationClass = null;
        String sig = constantPool.lookupUtf8(typeIndex);
        annotationClass = (Class<? extends Annotation>) parseSig(sig, container);

        AnnotationType type = null;
        try {
            type = AnnotationType.getInstance(annotationClass);
        } catch (IllegalArgumentException e) {
            // skip annotation parsing
            return null;
        }

        Map<String, Class<?>> memberTypes = type.memberTypes();
        Map<String, Object> memberValues = new LinkedHashMap<String, Object>(type.memberDefaults());

        int numMembers = readUnsignedShort(bb);

        for (int i = 0; i < numMembers; i++) {
            int memberNameIndex = readUnsignedShort(bb);
            String memberName = constantPool.lookupUtf8(memberNameIndex);
            Class<?> memberType = memberTypes.get(memberName);

            if (memberType == null) {
                // Member is no longer present in annotation type; ignore it
                // skipMemberValue(buf);
            } else {
                unimplemented();
            }
        }
        return AnnotationParser.annotationForMap(annotationClass, memberValues);
    }

    private static Class<?> parseSig(String sig, Class<?> container) {
        if (sig.equals("V"))
            return void.class;
        SignatureParser parser = SignatureParser.make();
        TypeSignature typeSig = parser.parseTypeSig(sig);
        GenericsFactory factory = CoreReflectionFactory.make(container, ClassScope.make(container));
        Reifier reify = Reifier.make(factory);
        typeSig.accept(reify);
        Type result = reify.getResult();
        return toClass(result);
    }

    static Class<?> toClass(Type o) {
        if (o instanceof GenericArrayType)
            return Array.newInstance(toClass(((GenericArrayType) o).getGenericComponentType()), 0).getClass();
        return (Class) o;
    }

}
