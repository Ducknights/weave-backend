package com.weave.post.config;

import com.weave.post.model.enums.PostStatus;
import lombok.extern.log4j.Log4j2;
import com.weave.post.model.enums.PostStateEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.guard.Guard;

import java.util.EnumSet;

/**
 * 帖子状态机配置
 */
@Log4j2
@Configuration
@EnableStateMachineFactory
public class PostStateMachineConfig extends EnumStateMachineConfigurerAdapter<PostStatus, PostStateEvent> {

    @Override
    public void configure(StateMachineStateConfigurer<PostStatus, PostStateEvent> states) throws Exception {
        states
                .withStates()
                .initial(PostStatus.PUBLIC)
                .end(PostStatus.DELETED)
                .states(EnumSet.allOf(PostStatus.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<PostStatus, PostStateEvent> transitions) throws Exception {
        transitions
                // 隐藏已发布帖子: PUBLIC -> HIDDEN
                .withExternal()
                    .source(PostStatus.PUBLIC).target(PostStatus.HIDDEN)
                    .event(PostStateEvent.HIDE)
                    .action(context -> log.info("帖子已隐藏"))
                    .and()

                // 恢复隐藏帖子: HIDDEN -> PUBLIC
                .withExternal()
                    .source(PostStatus.HIDDEN).target(PostStatus.PUBLIC)
                    .event(PostStateEvent.RESTORE)
                    .action(context -> log.info("帖子已恢复显示"))
                    .and()

                // 从已发布删除: PUBLIC -> DELETED
                .withExternal()
                    .source(PostStatus.PUBLIC).target(PostStatus.DELETED)
                    .event(PostStateEvent.DELETE)
                    .guard(notDeletedGuard())
                    .action(context -> log.info("已发布帖子已删除"))
                    .and()

                // 从隐藏删除: HIDDEN -> DELETED
                .withExternal()
                    .source(PostStatus.HIDDEN).target(PostStatus.DELETED)
                    .event(PostStateEvent.DELETE)
                    .guard(notDeletedGuard())
                    .action(context -> log.info("隐藏帖子已删除"))
        ;
    }

    private Guard<PostStatus, PostStateEvent> notDeletedGuard() {
        return context -> {
            PostStatus currentState = context.getStateMachine().getState().getId();
            boolean notDeleted = currentState != PostStatus.DELETED;
            if (!notDeleted) {
                log.warn("帖子已删除，无法操作");
            }
            return notDeleted;
        };
    }
}
