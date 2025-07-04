/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.example.graph.openmanus.human;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;

import java.util.List;
import java.util.Map;

/**
 * 智能条件边类
 * 根据agent的instruction进行智能路由决策
 */
public class IntelligentConditionalEdge {

    private final Map<String, AgentInfo> agentMap;
    private final ChatClient decisionClient;
    private final ToolCallbackResolver resolver;

    /**
     * Agent信息类
     */
    public static class AgentInfo {
        private final String name;
        private final String instruction;
        private final Object agent; // 改为Object以支持不同类型的agent

        public AgentInfo(String name, String instruction, Object agent) {
            this.name = name;
            this.instruction = instruction;
            this.agent = agent;
        }

        public String getName() {
            return name;
        }

        public String getInstruction() {
            return instruction;
        }

        public Object getAgent() {
            return agent;
        }

        /**
         * 获取ReactAgent（如果agent是ReactAgent类型）
         */
        public ReactAgent getReactAgent() {
            if (agent instanceof ReactAgent) {
                return (ReactAgent) agent;
            }
            throw new IllegalStateException("Agent is not a ReactAgent: " + agent.getClass().getSimpleName());
        }
    }

    /**
     * 构造函数
     * @param agentMap 包含agent名称和信息的映射
     * @param decisionClient 用于决策的聊天客户端
     * @param resolver 工具回调解析器
     */
    public IntelligentConditionalEdge(Map<String, AgentInfo> agentMap, ChatClient decisionClient, ToolCallbackResolver resolver) {
        this.agentMap = agentMap;
        this.decisionClient = decisionClient;
        this.resolver = resolver;
    }

    /**
     * 创建智能条件边
     * @return 异步边动作
     */
    public AsyncEdgeAction createIntelligentEdge() {
        return AsyncEdgeAction.edge_async(this::makeDecision);
    }

    /**
     * 智能决策方法
     * @param state 当前状态
     * @return 决策结果（agent名称）
     */
    private String makeDecision(OverAllState state) {
        try {
            // 构建决策提示
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("你是一个智能路由决策器。根据当前状态和可用的agents，选择最合适的agent来处理任务。\n\n");
            promptBuilder.append("可用的agents:\n");
            
            for (Map.Entry<String, AgentInfo> entry : agentMap.entrySet()) {
                AgentInfo agentInfo = entry.getValue();
                promptBuilder.append("- ").append(agentInfo.getName()).append(": ").append(agentInfo.getInstruction()).append("\n");
            }
            
            promptBuilder.append("\n当前状态信息:\n");
            promptBuilder.append(state.data().toString());
            promptBuilder.append("\n\n请根据当前状态和agent的instruction，选择最合适的agent。只返回agent的名称，不要其他内容。");

            // 调用决策客户端
            String decision = decisionClient.prompt()
                .user(promptBuilder.toString())
                .call()
                .content();

            // 验证决策结果是否在可用agents中
            if (agentMap.containsKey(decision)) {
                return decision;
            } else {
                // 如果决策结果不在可用agents中，返回默认agent或第一个agent
                return agentMap.keySet().iterator().next();
            }
        } catch (Exception e) {
            // 发生异常时返回默认agent
            return agentMap.keySet().iterator().next();
        }
    }

    /**
     * 获取指定名称的agent信息
     * @param agentName agent名称
     * @return agent信息
     */
    public AgentInfo getAgentInfo(String agentName) {
        return agentMap.get(agentName);
    }

    /**
     * 获取所有可用的agent名称
     * @return agent名称列表
     */
    public List<String> getAvailableAgents() {
        return List.copyOf(agentMap.keySet());
    }

    /**
     * 构建器类
     */
    public static class Builder {
        private Map<String, AgentInfo> agentMap = new java.util.HashMap<>();
        private ChatClient decisionClient;
        private ToolCallbackResolver resolver;

        public Builder decisionClient(ChatClient decisionClient) {
            this.decisionClient = decisionClient;
            return this;
        }

        public Builder resolver(ToolCallbackResolver resolver) {
            this.resolver = resolver;
            return this;
        }

        public Builder addAgent(String name, String instruction, Object agent) {
            agentMap.put(name, new AgentInfo(name, instruction, agent));
            return this;
        }

        public Builder addReactAgent(String name, String instruction, ReactAgent agent) {
            agentMap.put(name, new AgentInfo(name, instruction, agent));
            return this;
        }

        public IntelligentConditionalEdge build() {
            if (decisionClient == null) {
                throw new IllegalStateException("Decision client must be provided");
            }
            if (agentMap.isEmpty()) {
                throw new IllegalStateException("At least one agent must be added");
            }
            return new IntelligentConditionalEdge(agentMap, decisionClient, resolver);
        }
    }

    /**
     * 创建构建器
     * @return 构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }
} 