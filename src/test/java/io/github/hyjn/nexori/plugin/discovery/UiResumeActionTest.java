package io.github.hyjn.nexori.plugin.discovery;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UiResumeActionTest {

    @Test
    void isAnnotatedAsFunctionalInterface() {
        assertTrue(UiResumeAction.class.isAnnotationPresent(FunctionalInterface.class),
            "UiResumeAction is a @FunctionalInterface with one abstract method: reopen(...)");
    }

    @Test
    void isAnInterface() {
        assertTrue(UiResumeAction.class.isInterface());
    }

    @Test
    void hasExactlyOneAbstractMethodDeclaredOnInterface() {
        long abstractMethods = Arrays.stream(UiResumeAction.class.getMethods())
            .filter(m -> Modifier.isAbstract(m.getModifiers()))
            .filter(m -> UiResumeAction.class.equals(m.getDeclaringClass()))
            .count();
        assertEquals(1L, abstractMethods,
            "A @FunctionalInterface must have exactly one abstract method; default methods do not count");
    }
}
