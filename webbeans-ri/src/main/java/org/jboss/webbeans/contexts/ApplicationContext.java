package org.jboss.webbeans.contexts;

import javax.webbeans.ApplicationScoped;

public class ApplicationContext extends NormalContext {

   public ApplicationContext()
   {
      super(ApplicationScoped.class);
   }

}
