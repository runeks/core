/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.weld.injection.producer;

import java.lang.reflect.Type;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.util.Set;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.spi.AnnotatedMember;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.Producer;

import org.jboss.weld.annotated.enhanced.EnhancedAnnotatedMethod;
import org.jboss.weld.bean.DisposalMethod;
import org.jboss.weld.bean.SessionBean;
import org.jboss.weld.exceptions.DefinitionException;
import org.jboss.weld.injection.MethodInjectionPoint;
import org.jboss.weld.injection.InjectionPointFactory;
import org.jboss.weld.logging.BeanLogger;
import org.jboss.weld.security.GetMethodAction;

/**
 * {@link Producer} implementation for producer methods.
 *
 * @author Jozef Hartinger
 *
 */
public abstract class ProducerMethodProducer<X, T> extends AbstractMemberProducer<X, T> {

    private static final String PRODUCER_ANNOTATION = "@Produces";
    // The underlying method
    private final MethodInjectionPoint<T, ? super X> method;

    public ProducerMethodProducer(EnhancedAnnotatedMethod<T, ? super X> enhancedAnnotatedMethod, DisposalMethod<?, ?> disposalMethod) {
        super(enhancedAnnotatedMethod, disposalMethod);
        this.method = InjectionPointFactory.instance().createMethodInjectionPoint(enhancedAnnotatedMethod, getBean(), enhancedAnnotatedMethod.getDeclaringType().getJavaClass(), null, getBeanManager());
        checkProducerMethod(enhancedAnnotatedMethod);
        checkDelegateInjectionPoints();
    }

    /**
     * Validates the producer method
     */
    protected void checkProducerMethod(EnhancedAnnotatedMethod<T, ? super X> method) {
        if (method.getEnhancedParameters(Observes.class).size() > 0) {
            throw BeanLogger.LOG.inconsistentAnnotationsOnMethod(PRODUCER_ANNOTATION, "@Observes", this.method);
        } else if (method.getEnhancedParameters(Disposes.class).size() > 0) {
            throw BeanLogger.LOG.inconsistentAnnotationsOnMethod(PRODUCER_ANNOTATION, "@Disposes", this.method);
        } else if (getDeclaringBean() instanceof SessionBean<?>) {
            boolean methodDeclaredOnTypes = false;
            // TODO use annotated item?
            for (Type type : getDeclaringBean().getTypes()) {
                // TODO: deal with parameterized types!
                if (type instanceof Class<?>) {
                    try {
                        AccessController.doPrivileged(new GetMethodAction((Class<?>) type, method.getName(), method.getParameterTypesAsArray()));
                        methodDeclaredOnTypes = true;
                        break;
                    } catch (PrivilegedActionException ignored) {
                    }
                }
            }
            if (!methodDeclaredOnTypes) {
                throw BeanLogger.LOG.methodNotBusinessMethod(this, getDeclaringBean());
            }
        }
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return method.getInjectionPoints();
    }

    @Override
    protected T produce(Object receiver, CreationalContext<T> ctx) {
        return method.invoke(receiver, null, getBeanManager(), ctx, CreationException.class);
    }

    @Override
    public AnnotatedMember<? super X> getAnnotated() {
        return method.getAnnotated();
    }

    protected DefinitionException producerCannotHaveWildcardBeanType(Object member) {
        return BeanLogger.LOG.producerMethodCannotHaveAWildcardReturnType(member);
    }

    protected DefinitionException producerWithTypeVariableBeanTypeMustBeDependent(Object member) {
        return BeanLogger.LOG.producerMethodWithTypeVariableReturnTypeMustBeDependent(member);
    }
}
