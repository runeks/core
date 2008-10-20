package org.jboss.webbeans.test;

import java.lang.annotation.Annotation;
import java.util.HashMap;

import javax.webbeans.Current;

import org.jboss.webbeans.ManagerImpl;
import org.jboss.webbeans.injectable.SimpleConstructor;
import org.jboss.webbeans.introspector.AnnotatedType;
import org.jboss.webbeans.introspector.SimpleAnnotatedType;
import org.jboss.webbeans.model.SimpleComponentModel;
import org.jboss.webbeans.test.annotations.Synchronous;
import org.jboss.webbeans.test.components.Chicken;
import org.jboss.webbeans.test.components.Donkey;
import org.jboss.webbeans.test.components.Duck;
import org.jboss.webbeans.test.components.Goat;
import org.jboss.webbeans.test.components.Goose;
import org.jboss.webbeans.test.components.Order;
import org.jboss.webbeans.test.components.Sheep;
import org.jboss.webbeans.test.components.Turkey;
import org.jboss.webbeans.test.mock.MockContainerImpl;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SpecVersion("20080925")
public class ConstructorModelTest
{

   private ManagerImpl container;
   
   private AnnotatedType<Object> emptyAnnotatedItem;
   
   @BeforeMethod
   public void before()
   {
      emptyAnnotatedItem = new SimpleAnnotatedType<Object>(null, new HashMap<Class<? extends Annotation>, Annotation>());
      container = new MockContainerImpl(null);
      
   }
   
   @Test
   public void testImplicitConstructor()
   {
      SimpleConstructor<Order> constructor = new SimpleComponentModel<Order>(new SimpleAnnotatedType<Order>(Order.class), emptyAnnotatedItem, container).getConstructor();
      assert constructor.getAnnotatedItem().getDelegate().getDeclaringClass().equals(Order.class);
      assert constructor.getAnnotatedItem().getDelegate().getParameterTypes().length == 0;
      assert constructor.getParameters().size() == 0;
   }
   
   @Test
   public void testSingleConstructor()
   {
      SimpleConstructor<Donkey> constructor = new SimpleComponentModel<Donkey>(new SimpleAnnotatedType<Donkey>(Donkey.class), emptyAnnotatedItem, container).getConstructor();
      assert constructor.getAnnotatedItem().getDelegate().getDeclaringClass().equals(Donkey.class);
      assert constructor.getAnnotatedItem().getDelegate().getParameterTypes().length == 1;
      assert constructor.getAnnotatedItem().getDelegate().getParameterTypes()[0].equals(String.class);
      assert constructor.getParameters().size() == 1;
      assert constructor.getParameters().get(0).getType().equals(String.class);
      assert constructor.getParameters().get(0).getBindingTypes().length == 1;
      assert constructor.getParameters().get(0).getBindingTypes()[0].annotationType().equals(Current.class);
   }
   
   @Test
   public void testInitializerAnnotatedConstructor()
   {
      SimpleConstructor<Sheep> constructor = new SimpleComponentModel<Sheep>(new SimpleAnnotatedType<Sheep>(Sheep.class), emptyAnnotatedItem, container).getConstructor();
      assert constructor.getAnnotatedItem().getDelegate().getDeclaringClass().equals(Sheep.class);
      assert constructor.getAnnotatedItem().getDelegate().getParameterTypes().length == 2;
      assert constructor.getAnnotatedItem().getDelegate().getParameterTypes()[0].equals(String.class);
      assert constructor.getAnnotatedItem().getDelegate().getParameterTypes()[1].equals(Double.class);
      assert constructor.getParameters().size() == 2;
      assert constructor.getParameters().get(0).getType().equals(String.class);
      assert constructor.getParameters().get(1).getType().equals(Double.class);
      assert constructor.getParameters().get(0).getBindingTypes().length == 1;
      assert constructor.getParameters().get(0).getBindingTypes()[0].annotationType().equals(Current.class);
      assert constructor.getParameters().get(1).getBindingTypes().length == 1;
      assert constructor.getParameters().get(1).getBindingTypes()[0].annotationType().equals(Current.class);
   }
   
   @Test
   public void testBindingTypeAnnotatedConstructor()
   {
      SimpleConstructor<Duck> constructor = new SimpleComponentModel<Duck>(new SimpleAnnotatedType<Duck>(Duck.class), emptyAnnotatedItem, container).getConstructor();
      assert constructor.getAnnotatedItem().getDelegate().getDeclaringClass().equals(Duck.class);
      assert constructor.getAnnotatedItem().getDelegate().getParameterTypes().length == 2;
      assert constructor.getAnnotatedItem().getDelegate().getParameterTypes()[0].equals(String.class);
      assert constructor.getAnnotatedItem().getDelegate().getParameterTypes()[1].equals(Integer.class);
      assert constructor.getParameters().size() == 2;
      assert constructor.getParameters().get(0).getType().equals(String.class);
      assert constructor.getParameters().get(1).getType().equals(Integer.class);
      assert constructor.getParameters().get(0).getBindingTypes().length == 1;
      assert constructor.getParameters().get(0).getBindingTypes()[0].annotationType().equals(Current.class);
      assert constructor.getParameters().get(1).getBindingTypes().length == 1;
      assert constructor.getParameters().get(1).getBindingTypes()[0].annotationType().equals(Synchronous.class);
   }
   
   @Test
   public void testTooManyInitializerAnnotatedConstructor()
   {
      boolean exception = false;
      try
      {
         new SimpleComponentModel<Chicken>(new SimpleAnnotatedType<Chicken>(Chicken.class), emptyAnnotatedItem, container);
      }
      catch (Exception e) 
      {
         exception = true;
      }
      assert exception;
      
   }
   
   @Test
   public void testTooManyConstructors()
   {
      boolean exception = false;
      try
      {
         new SimpleComponentModel<Turkey>(new SimpleAnnotatedType<Turkey>(Turkey.class), emptyAnnotatedItem, container);
      }
      catch (Exception e) 
      {
         exception = true;
      }
      assert exception;
      
   }
   
   @Test
   public void testTooManyBindingTypeAnnotatedConstructor()
   {
      boolean exception = false;
      try
      {
         new SimpleComponentModel<Goat>(new SimpleAnnotatedType<Goat>(Goat.class), emptyAnnotatedItem, container);
      }
      catch (Exception e) 
      {
         exception = true;
      }
      assert exception;
      
   }
   
   @Test
   public void testBindingTypeAndInitializerAnnotatedConstructor()
   {
      boolean exception = false;
      try
      {
         new SimpleComponentModel<Goose>(new SimpleAnnotatedType<Goose>(Goose.class), emptyAnnotatedItem, container);
      }
      catch (Exception e) 
      {
         exception = true;
      }
      assert exception;
      
   }
   
   @Test @SpecAssertion(section="2.7.2")
   public void testStereotypeOnConstructor()
   {
	   assert false;
   }
   
}
