package com.weave.auth.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserAuthoritiesRefreshEvent extends ApplicationEvent {

    private final Long userId;

    public UserAuthoritiesRefreshEvent(Object source, Long userId) {
        super(source);
        this.userId = userId;
    }

}
