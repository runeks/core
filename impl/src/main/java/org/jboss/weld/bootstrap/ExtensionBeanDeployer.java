/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat, Inc., and individual contributors
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
package org.jboss.weld.bootstrap;

import static org.jboss.weld.util.reflection.Reflections.cast;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.spi.Extension;

import org.jboss.weld.annotated.enhanced.EnhancedAnnotatedMethod;
import org.jboss.weld.annotated.enhanced.EnhancedAnnotatedType;
import org.jboss.weld.bean.RIBean;
import org.jboss.weld.bean.builtin.ExtensionBean;
import org.jboss.weld.bootstrap.events.ContainerLifecycleEvents;
import org.jboss.weld.bootstrap.spi.Deployment;
import org.jboss.weld.bootstrap.spi.Metadata;
import org.jboss.weld.event.ObserverFactory;
import org.jboss.weld.event.ObserverMethodImpl;
import org.jboss.weld.logging.BootstrapLogger;
import org.jboss.weld.manager.BeanManagerImpl;
import org.jboss.weld.resources.ClassTransformer;
import org.jboss.weld.resources.spi.ResourceLoadingException;
import org.jboss.weld.util.BeanMethods;
import org.jboss.weld.util.DeploymentStructures;
import org.jboss.weld.util.reflection.Formats;

/**
 * @author pmuir
 */
public class ExtensionBeanDeployer {

    private final BeanManagerImpl beanManager;
    private final Set<Metadata<Extension>> extensions;
    private final Deployment deployment;
    private final BeanDeploymentArchiveMapping bdaMapping;
    private final Collection<ContextHolder<? extends Context>> contexts;
    private final ContainerLifecycleEvents containerLifecycleEventObservers;
    private final MissingDependenciesRegistry missingDependenciesRegistry;

    public ExtensionBeanDeployer(BeanManagerImpl manager, Deployment deployment, BeanDeploymentArchiveMapping bdaMapping,
            Collection<ContextHolder<? extends Context>> contexts) {
        this.beanManager = manager;
        this.extensions = new HashSet<Metadata<Extension>>();
        this.deployment = deployment;
        this.bdaMapping = bdaMapping;
        this.contexts = contexts;
        this.containerLifecycleEventObservers = beanManager.getServices().get(ContainerLifecycleEvents.class);
        this.missingDependenciesRegistry = beanManager.getServices().get(MissingDependenciesRegistry.class);
    }

    public ExtensionBeanDeployer deployBeans() {
        ClassTransformer classTransformer = beanManager.getServices().get(ClassTransformer.class);
        for (Metadata<Extension> extension : extensions) {
            // Locate the BeanDeployment for this extension
            BeanDeployment beanDeployment = DeploymentStructures.getOrCreateBeanDeployment(deployment, beanManager, bdaMapping, contexts, extension.getValue()
                    .getClass());

            EnhancedAnnotatedType<Extension> enchancedAnnotatedType = getEnhancedAnnotatedType(classTransformer, extension, beanDeployment);

            if (enchancedAnnotatedType != null) {
                ExtensionBean bean = new ExtensionBean(beanDeployment.getBeanManager(), enchancedAnnotatedType, extension);
                Set<ObserverInitializationContext<?, ?>> observerMethodInitializers = new HashSet<ObserverInitializationContext<?, ?>>();
                createObserverMethods(bean, beanDeployment.getBeanManager(), enchancedAnnotatedType, observerMethodInitializers);
                beanDeployment.getBeanManager().addBean(bean);
                beanDeployment.getBeanDeployer().addExtension(bean);
                for (ObserverInitializationContext<?, ?> observerMethodInitializer : observerMethodInitializers) {
                    observerMethodInitializer.initialize();
                    beanDeployment.getBeanManager().addObserver(observerMethodInitializer.getObserver());
                    containerLifecycleEventObservers.processObserverMethod(observerMethodInitializer.getObserver());
                }
            }
        }
        return this;
    }

    private EnhancedAnnotatedType<Extension> getEnhancedAnnotatedType(ClassTransformer classTransformer, Metadata<Extension> extension,
            BeanDeployment beanDeployment) {
        Class<? extends Extension> clazz = extension.getValue().getClass();
        try {
            return cast(classTransformer.getEnhancedAnnotatedType(clazz, beanDeployment.getBeanDeploymentArchive().getId()));
        } catch (ResourceLoadingException e) {
            String missingDependency = Formats.getNameOfMissingClassLoaderDependency(e);
            BootstrapLogger.LOG.ignoringExtensionClassDueToLoadingError(clazz.getName(), missingDependency);
            BootstrapLogger.LOG.catchingDebug(e);
            missingDependenciesRegistry.registerClassWithMissingDependency(clazz.getName(), missingDependency);
            return null;
        }
    }

    public void addExtensions(Iterable<Metadata<Extension>> extensions) {
        for (Metadata<Extension> extension : extensions) {
            addExtension(extension);
        }
    }

    public void addExtension(Metadata<Extension> extension) {
        this.extensions.add(extension);
    }

    protected <X> void createObserverMethods(RIBean<X> declaringBean, BeanManagerImpl beanManager, EnhancedAnnotatedType<? super X> annotatedClass,
            Set<ObserverInitializationContext<?, ?>> observerMethodInitializers) {
        for (EnhancedAnnotatedMethod<?, ? super X> method : BeanMethods.getObserverMethods(annotatedClass)) {
            createObserverMethod(declaringBean, beanManager, method, observerMethodInitializers);
        }
    }

    protected <T, X> void createObserverMethod(RIBean<X> declaringBean, BeanManagerImpl beanManager, EnhancedAnnotatedMethod<T, ? super X> method,
            Set<ObserverInitializationContext<?, ?>> observerMethodInitializers) {
        ObserverMethodImpl<T, X> observer = ObserverFactory.create(method, declaringBean, beanManager);
        ObserverInitializationContext<T, X> observerMethodInitializer = ObserverInitializationContext.of(observer, method);
        observerMethodInitializers.add(observerMethodInitializer);
    }

}
