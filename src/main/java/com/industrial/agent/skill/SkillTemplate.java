package com.industrial.agent.skill;

import com.industrial.agent.prompt.PromptCompiler;
import com.industrial.agent.runtime.RuntimeContext;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Abstract base for skills that benefit from PromptCompiler-based system messages
 * and filtered tool subsets. Not a replacement for existing expert agents —
 * they already compile their own prompts via @SystemMessage.
 *
 * Use this when a new skill needs dynamic prompt assembly (L1-L6) or
 * tool filtering beyond what a static @SystemMessage provides.
 */
@Slf4j
public abstract class SkillTemplate implements Skill {

    protected final PromptCompiler promptCompiler;
    protected final ChatModel chatModel;
    protected final List<Object> tools;

    protected SkillTemplate(PromptCompiler promptCompiler, ChatModel chatModel, List<Object> tools) {
        this.promptCompiler = promptCompiler;
        this.chatModel = chatModel;
        this.tools = tools;
    }

    /**
     * Build the AiServices assistant with promptCompiler for system messages.
     * Subclasses define the assistant interface type and any @UserMessage templates.
     */
    protected <T> T buildAssistant(Class<T> assistantClass, RuntimeContext ctx) {
        var builder = AiServices.builder(assistantClass)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .systemMessageProvider(id -> promptCompiler.compileSystem(ctx));
        if (tools != null && !tools.isEmpty()) {
            builder.tools(tools.toArray());
        }
        return builder.build();
    }
}
