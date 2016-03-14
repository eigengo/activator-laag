package fitness.muvr.profile.impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import fitness.muvr.profile.api.UserService;

/**
 * Defines and configures the user service module
 */
public class UserServiceModule extends AbstractModule implements ServiceGuiceSupport {

    @Override
    protected void configure() {
        bindServices(serviceBinding(UserService.class, UserServiceImpl.class));
    }
}
