package jdk.vm.ci.meta;

import java.lang.annotation.*;

public class LocalAnnotation {
    private final int start;
    private final int length;
    private final int index;
    private final Annotation annotation;

    public LocalAnnotation(final Annotation annotation, int start, int length, int index) {
        this.annotation = annotation;
        this.start = start;
        this.length = length;
        this.index = index;
    }

    public int getStart() {
        return start;
    }

    public int getLength() {
        return length;
    }

    public int getIndex() {
        return index;
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    @Override
    public String toString() {
        return String.format("[local @ index %d]: %s", index, annotation.toString());
    }
}
