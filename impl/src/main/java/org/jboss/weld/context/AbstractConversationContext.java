/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2009 Sun Microsystems, Inc. All rights reserved.
 *
 * Use is subject to license terms.
 *
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
package org.jboss.weld.context;

import static org.jboss.weld.context.conversation.ConversationIdGenerator.CONVERSATION_ID_GENERATOR_ATTRIBUTE_NAME;
import static org.jboss.weld.util.reflection.Reflections.cast;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.enterprise.context.ConversationScoped;

import org.jboss.weld.Container;
import org.jboss.weld.context.beanstore.BoundBeanStore;
import org.jboss.weld.context.beanstore.ConversationNamingScheme;
import org.jboss.weld.context.beanstore.NamingScheme;
import org.jboss.weld.context.conversation.ConversationIdGenerator;
import org.jboss.weld.context.conversation.ConversationImpl;
import org.jboss.weld.literal.DestroyedLiteral;
import org.jboss.weld.logging.ContextLogger;
import org.jboss.weld.logging.ConversationLogger;
import org.jboss.weld.manager.BeanManagerImpl;
import org.jboss.weld.serialization.BeanIdentifierIndex;


/**
 * The base of the conversation context, which can use a variety of storage
 * forms
 *
 * @author Pete Muir
 * @author Jozef Hartinger
 * @author George Sapountzis
 * @author Marko Luksa
 */
public abstract class AbstractConversationContext<R, S> extends AbstractBoundContext<R> implements ConversationContext {

    private static final String CURRENT_CONVERSATION_ATTRIBUTE_NAME = ConversationContext.class.getName() + ".currentConversation";
    public static final String CONVERSATIONS_ATTRIBUTE_NAME = ConversationContext.class.getName() + ".conversations";

    private static final long DEFAULT_TIMEOUT = 10 * 60 * 1000L;
    private static final long CONCURRENT_ACCESS_TIMEOUT = 1000L;
    private static final String PARAMETER_NAME = "cid";

    private final AtomicReference<String> parameterName;
    private final AtomicLong defaultTimeout;
    private final AtomicLong concurrentAccessTimeout;

    private final ThreadLocal<R> associated;

    private final BeanManagerImpl manager;

    private final BeanIdentifierIndex beanIdentifierIndex;

    public AbstractConversationContext(String contextId, BeanIdentifierIndex beanIdentifierIndex) {
        super(contextId, true);
        this.parameterName = new AtomicReference<String>(PARAMETER_NAME);
        this.defaultTimeout = new AtomicLong(DEFAULT_TIMEOUT);
        this.concurrentAccessTimeout = new AtomicLong(CONCURRENT_ACCESS_TIMEOUT);
        this.associated = new ThreadLocal<R>();
        this.manager = Container.instance(contextId).deploymentManager();
        this.beanIdentifierIndex = beanIdentifierIndex;
    }

    @Override
    public String getParameterName() {
        return parameterName.get();
    }

    @Override
    public void setParameterName(String cid) {
        this.parameterName.set(cid);
    }

    @Override
    public void setConcurrentAccessTimeout(long timeout) {
        this.concurrentAccessTimeout.set(timeout);
    }

    @Override
    public long getConcurrentAccessTimeout() {
        return concurrentAccessTimeout.get();
    }

    @Override
    public void setDefaultTimeout(long timeout) {
        this.defaultTimeout.set(timeout);
    }

    @Override
    public long getDefaultTimeout() {
        return defaultTimeout.get();
    }

    @Override
    public boolean associate(R request) {
        if (this.associated.get() == null) {
            this.associated.set(request);
            /*
            * We need to delay attaching the bean store until activate() is called
            * so that we can attach the correct conversation id
            */

            /*
            * We may need access to the conversation id generator and
            * conversations. If the session already exists, we can load it from
            * there, otherwise we can create a new conversation id generator and
            * conversations collection. If the the session exists when the request
            * is dissociated, then we store them in the session then.
            *
            * We always store the generator and conversation map in the request
            * for temporary usage.
            */
            if (getSessionAttribute(request, CONVERSATION_ID_GENERATOR_ATTRIBUTE_NAME, false) == null) {
                ConversationIdGenerator generator = new ConversationIdGenerator();
                setRequestAttribute(request, CONVERSATION_ID_GENERATOR_ATTRIBUTE_NAME, generator);
                setSessionAttribute(request, CONVERSATION_ID_GENERATOR_ATTRIBUTE_NAME, generator, false);
            } else {
                setRequestAttribute(request, CONVERSATION_ID_GENERATOR_ATTRIBUTE_NAME, getSessionAttribute(request, CONVERSATION_ID_GENERATOR_ATTRIBUTE_NAME, true));
            }

            if (getSessionAttribute(request, CONVERSATIONS_ATTRIBUTE_NAME, false) == null) {
                Map<String, ManagedConversation> conversations = Collections.synchronizedMap(new HashMap<String, ManagedConversation>());
                setRequestAttribute(request, CONVERSATIONS_ATTRIBUTE_NAME, conversations);
                setSessionAttribute(request, CONVERSATIONS_ATTRIBUTE_NAME, conversations, false);
            } else {
                setRequestAttribute(request, CONVERSATIONS_ATTRIBUTE_NAME, getSessionAttribute(request, CONVERSATIONS_ATTRIBUTE_NAME, true));
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean dissociate(R request) {
        if (isAssociated()) {
            try {
                copyConversationIdGeneratorAndConversationsToSession();
                return true;
            } finally {
                this.associated.set(null);
                cleanup();
            }
        } else {
            return false;
        }
    }

    public void copyConversationIdGeneratorAndConversationsToSession() {
        if (!isAssociated()) {
            return;
        }
        final R request = associated.get();
        /*
        * If the session is available, store the conversation id generator and
        * conversations if necessary.
        */
        if (getSessionAttribute(request, CONVERSATION_ID_GENERATOR_ATTRIBUTE_NAME, false) == null) {
            setSessionAttribute(request, CONVERSATION_ID_GENERATOR_ATTRIBUTE_NAME, getRequestAttribute(request, CONVERSATION_ID_GENERATOR_ATTRIBUTE_NAME), false);
        }
        if (getSessionAttribute(request, CONVERSATIONS_ATTRIBUTE_NAME, false) == null) {
            setSessionAttribute(request, CONVERSATIONS_ATTRIBUTE_NAME, getRequestAttribute(request, CONVERSATIONS_ATTRIBUTE_NAME), false);
        }
    }

    public void sessionCreated() {
        copyConversationIdGeneratorAndConversationsToSession();
    }


    protected void associateRequestWithNewConversation() {
        ManagedConversation conversation = new ConversationImpl(manager);
        lock(conversation);
        setRequestAttribute(getRequest(), CURRENT_CONVERSATION_ATTRIBUTE_NAME, conversation);

        // Set a temporary bean store, this will be attached at the end of the request if needed
        NamingScheme namingScheme = new ConversationNamingScheme(getNamingSchemePrefix(), "transient", beanIdentifierIndex);
        setBeanStore(createRequestBeanStore(namingScheme, getRequest()));
        setRequestAttribute(getRequest(), ConversationNamingScheme.PARAMETER_NAME, namingScheme);
    }

    protected void associateRequest(ManagedConversation conversation) {
        setRequestAttribute(getRequest(), CURRENT_CONVERSATION_ATTRIBUTE_NAME, conversation);

        NamingScheme namingScheme = new ConversationNamingScheme(getNamingSchemePrefix(), conversation.getId(), beanIdentifierIndex);
        setBeanStore(createRequestBeanStore(namingScheme, getRequest()));
        getBeanStore().attach();
    }

    @Override
    public void activate() {
        this.activate(null);
    }

    @Override
    public void activate(String cid) {
        if (!isActive()) {
            if (!isAssociated()) {
                throw ConversationLogger.LOG.mustCallAssociateBeforeActivate();
            }
            // Activate the context
            super.setActive(true);

            initialize(cid);
        } else {
            throw ConversationLogger.LOG.contextAlreadyActive();
        }
    }

    protected void initialize(String cid) {
        // Attach the conversation
        // WELD-1315 Don't try to restore the long-running conversation if cid param is empty
        if (cid != null && !cid.isEmpty()) {
            ManagedConversation conversation = getConversation(cid);
            if (conversation != null && !isExpired(conversation)) {
                boolean lock = lock(conversation);
                if (lock) {
                    // WELD-1690 Don't associate a conversation which was ended (race condition)
                    if(conversation.isTransient()) {
                        associateRequestWithNewConversation();
                        throw ConversationLogger.LOG.noConversationFoundToRestore(cid);
                    }
                    associateRequest(conversation);
                } else {
                    // CDI 6.7.4 we must activate a new transient conversation before we throw the exception
                    associateRequestWithNewConversation();
                    throw ConversationLogger.LOG.conversationLockTimedout(cid);
                }
            } else {
                // CDI 6.7.4 we must activate a new transient conversation before we throw the exception
                associateRequestWithNewConversation();
                throw ConversationLogger.LOG.noConversationFoundToRestore(cid);
            }
        } else {
            associateRequestWithNewConversation();
        }
    }

    private boolean lock(ManagedConversation conversation) {
        return conversation.lock(getConcurrentAccessTimeout());
    }

    @Override
    public void deactivate() {
        // Disassociate from the current conversation
        if (isActive()) {
            if (!isAssociated()) {
                throw ConversationLogger.LOG.mustCallAssociateBeforeDeactivate();
            }

            try {
                if (getCurrentConversation().isTransient() && getRequestAttribute(getRequest(), ConversationNamingScheme.PARAMETER_NAME) != null) {
                    // WELD-1746 Don't destroy ended conversations - these must be destroyed in a synchronized block - see also cleanUpConversationMap()
                    destroy();
                } else {
                    // Update the conversation timestamp
                    getCurrentConversation().touch();
                    if (!getBeanStore().isAttached()) {
                        /*
                         * This was a transient conversation at the beginning of the request, so we need to update the CID it uses, and attach it. We also add
                         * it to the conversations the session knows about.
                         */
                        if (!(getRequestAttribute(getRequest(), ConversationNamingScheme.PARAMETER_NAME) instanceof ConversationNamingScheme)) {
                            throw ConversationLogger.LOG.conversationNamingSchemeNotFound();
                        }
                        ((ConversationNamingScheme) getRequestAttribute(getRequest(), ConversationNamingScheme.PARAMETER_NAME)).setCid(getCurrentConversation()
                                .getId());

                        try {
                            getBeanStore().attach();
                            getConversationMap().put(getCurrentConversation().getId(), getCurrentConversation());
                        } catch (Exception e) {
                            // possible session not found error..
                            ContextLogger.LOG.destroyingContextAfterBeanStoreAttachError(this, getBeanStore());
                            destroy();
                        }
                    }
                }
            } finally {
                // WELD-1690 always try to unlock the current conversation
                getCurrentConversation().unlock();
            }
            setBeanStore(null);
            // Clean up any expired/ended conversations
            cleanUpConversationMap();
            // deactivate the context
            super.setActive(false);
        } else {
            throw ConversationLogger.LOG.contextNotActive();
        }
    }

    private void cleanUpConversationMap() {
        Map<String, ManagedConversation> conversations = getConversationMap();
        synchronized (conversations) {
            Iterator<Entry<String, ManagedConversation>> entryIterator = conversations.entrySet().iterator();
            while (entryIterator.hasNext()) {
                Entry<String, ManagedConversation> entry = entryIterator.next();
                if (entry.getValue().isTransient()) {
                    destroyConversation(getSessionFromRequest(getRequest(), false), entry.getKey());
                    entryIterator.remove();
                }
            }
        }
    }

    public void conversationPromotedToLongRunning(ConversationImpl conversation) {
        getConversationMap().put(conversation.getId(), conversation);
    }

    @Override
    public void invalidate() {
        ManagedConversation currentConversation = getCurrentConversation();
        Map<String, ManagedConversation> conversations = getConversationMap();
        synchronized (conversations) {
            Iterator<Entry<String, ManagedConversation>> iterator = conversations.entrySet().iterator();
            while (iterator.hasNext()) {
                ManagedConversation conversation = iterator.next().getValue();
                if (!currentConversation.equals(conversation) && !conversation.isTransient() && isExpired(conversation)) {
                    // Try to lock the conversation and log warning if not successful - unlocking should not be necessary
                    if(!conversation.lock(0)) {
                        ConversationLogger.LOG.endLockedConversation(conversation.getId());
                    }
                    conversation.end();
                }
            }
        }
    }

    public boolean destroy(S session) {
        // the context may be active
        // if it is, we need to re-attach the bean store once the other conversations are destroyed
        BoundBeanStore beanStore = getBeanStore();
        final boolean active = isActive();
        if (beanStore != null) {
            beanStore.detach();
        }

        try {
            if (getSessionAttributeFromSession(session, CONVERSATIONS_ATTRIBUTE_NAME) instanceof Map<?, ?>) {
                // if there are conversations to destroy
                Map<String, ManagedConversation> conversations = cast(getSessionAttributeFromSession(session, CONVERSATIONS_ATTRIBUTE_NAME));
                // if the context is not active, let's activate it
                setActive(true);

                for (ManagedConversation conversation : conversations.values()) {
                    String id = conversation.getId();
                    if (!conversation.isTransient()) {
                        // the currently associated conversation will be destroyed at the end of the current request
                        conversation.end();
                    }
                    if (!isCurrentConversation(id)) {
                        // a conversation that is not currently associated is destroyed immediately
                        destroyConversation(session, id);
                    }
                }
            }
            return true;
        } finally {
            setBeanStore(beanStore);
            setActive(active);
            if (beanStore != null) {
                getBeanStore().attach();
            } else if (!isActive()){
                cleanup();
            }
        }
    }

    private boolean isCurrentConversation(String id) {
        if (!isAssociated()) {
            return false;
        }
        return id !=null && id.equals(getCurrentConversation().getId());
    }

    protected void destroyConversation(S session, String id) {
        if (session != null) {
            // session can be null as we may have nothing in the session
            setBeanStore(createSessionBeanStore(new ConversationNamingScheme(getNamingSchemePrefix(), id, beanIdentifierIndex), session));
            getBeanStore().attach();
            destroy();
            getBeanStore().detach();
            setBeanStore(null);
            manager.getGlobalLenientObserverNotifier().fireEvent(id, DestroyedLiteral.CONVERSATION);
        }
    }

    @Override
    public String generateConversationId() {
        if (!isAssociated()) {
            throw ConversationLogger.LOG.mustCallAssociateBeforeGeneratingId();
        }
        if (!(getRequestAttribute(getRequest(), CONVERSATION_ID_GENERATOR_ATTRIBUTE_NAME) instanceof ConversationIdGenerator)) {
            throw ConversationLogger.LOG.conversationIdGeneratorNotFound();
        }
        checkContextInitialized();
        ConversationIdGenerator generator = (ConversationIdGenerator) getRequestAttribute(getRequest(), CONVERSATION_ID_GENERATOR_ATTRIBUTE_NAME);
        return generator.call();
    }

    private static boolean isExpired(ManagedConversation conversation) {
        return System.currentTimeMillis() > (conversation.getLastUsed() + conversation.getTimeout());
    }

    @Override
    public ManagedConversation getConversation(String id) {
        return getConversationMap().get(id);
    }

    @Override
    public Collection<ManagedConversation> getConversations() {
        // Don't return the map view to avoid concurrency issues
        Map<String, ManagedConversation> conversations = getConversationMap();
        synchronized (conversations) {
            return new HashSet<ManagedConversation>(conversations.values());
        }
    }

    private void checkIsAssociated() {
        if (!isAssociated()) {
            throw ConversationLogger.LOG.mustCallAssociateBeforeLoadingKnownConversations();
        }
    }

    private Map<String, ManagedConversation> getConversationMap() {
        checkIsAssociated();
        checkContextInitialized();
        if (!(getRequestAttribute(getRequest(), CONVERSATIONS_ATTRIBUTE_NAME) instanceof Map<?, ?>)) {
            throw ConversationLogger.LOG.unableToLoadCurrentConversations();
        }
        return cast(getRequestAttribute(getRequest(), CONVERSATIONS_ATTRIBUTE_NAME));
    }

    @Override
    public ManagedConversation getCurrentConversation() {
        checkIsAssociated();
        checkContextInitialized();
        if (!(getRequestAttribute(getRequest(), CURRENT_CONVERSATION_ATTRIBUTE_NAME) instanceof ManagedConversation)) {
            throw ConversationLogger.LOG.unableToLoadCurrentConversations();
        }
        return (ManagedConversation) getRequestAttribute(getRequest(), CURRENT_CONVERSATION_ATTRIBUTE_NAME);
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return ConversationScoped.class;
    }

    /**
     * Set an attribute in the session.
     *
     * @param request the request to set the session attribute in
     * @param name    the name of the attribute
     * @param value   the value of the attribute
     * @param create  if false, the attribute will only be set if the session
     *                already exists, otherwise it will always be set
     * @throws IllegalStateException if create is true, and the session can't be
     *                               created
     */
    protected abstract void setSessionAttribute(R request, String name, Object value, boolean create);

    /**
     * Get an attribute value from the session.
     *
     * @param request the request to get the session attribute from
     * @param name    the name of the attribute
     * @param create  if false, the attribute will only be retrieved if the
     *                session already exists, other wise it will always be retrieved
     * @return attribute
     * @throws IllegalStateException if create is true, and the session can't be
     *                               created
     */
    protected abstract Object getSessionAttribute(R request, String name, boolean create);

    /**
     * Get an attribute value from the session.
     *
     * @param session the session to get the session attribute from
     * @param name    the name of the attribute
     * @return attribute
     * @throws IllegalStateException if create is true, and the session can't be
     *                               created
     */
    protected abstract Object getSessionAttributeFromSession(S session, String name);

    /**
     * Remove an attribute from the request.
     *
     * @param request the request to remove the attribute from
     * @param name    the name of the attribute
     */
    protected abstract void removeRequestAttribute(R request, String name);

    /**
     * Set an attribute in the request.
     *
     * @param request the request to set the attribute from
     * @param name    the name of the attribute
     * @param value   the value of the attribute
     */
    protected abstract void setRequestAttribute(R request, String name, Object value);

    /**
     * Retrieve an attribute value from the request
     *
     * @param request the request to get the attribute from
     * @param name    the name of the attribute to get
     * @return the value of the attribute
     */
    protected abstract Object getRequestAttribute(R request, String name);

    protected abstract BoundBeanStore createRequestBeanStore(NamingScheme namingScheme, R request);

    protected abstract BoundBeanStore createSessionBeanStore(NamingScheme namingScheme, S session);

    protected abstract S getSessionFromRequest(R request, boolean create);

    protected abstract String getNamingSchemePrefix();

    /**
     * Check if the context is currently associated
     *
     * @return true if the context is associated
     */
    protected boolean isAssociated() {
        return associated.get() != null;
    }

    /**
     * Get the associated store
     *
     * @return the request
     */
    protected R getRequest() {
        return associated.get();
    }
}
