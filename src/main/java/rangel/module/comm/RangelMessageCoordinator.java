package rangel.module.comm;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.communication.standard.bundle.StandardMessage;
import adf.core.agent.communication.standard.bundle.StandardMessagePriority;
import adf.core.agent.communication.standard.bundle.centralized.*;
import adf.core.agent.communication.standard.bundle.information.*;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.component.communication.CommunicationMessage;
import adf.core.component.communication.MessageCoordinator;
import org.jetbrains.annotations.NotNull;
import rescuecore2.standard.entities.StandardEntityURN;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 消息协调员
 *
 * @author 软工20-2金磊
 */
public class RangelMessageCoordinator extends MessageCoordinator {

    @Override
    public void coordinate(AgentInfo agentInfo, WorldInfo worldInfo,
                           ScenarioInfo scenarioInfo, MessageManager messageManager,
                           ArrayList<CommunicationMessage> sendMessageList,
                           List<List<CommunicationMessage>> channelSendMessageList) {

        //每个智能体都有不同的列表
        ArrayList<CommunicationMessage> policeMessages = new ArrayList<>();
        ArrayList<CommunicationMessage> ambulanceMessages = new ArrayList<>();
        ArrayList<CommunicationMessage> fireBrigadeMessages = new ArrayList<>();

        //语音消息
        ArrayList<CommunicationMessage> voiceMessages = new ArrayList<>();

        StandardEntityURN agentType = getAgentType(agentInfo, worldInfo);

        for (CommunicationMessage msg : sendMessageList) {
            //如果是标准消息并且不是无线电时,将其添加到语音消息
            if (msg instanceof StandardMessage && !msg.isRadio()) {
                voiceMessages.add(msg);
            } else {
                if (msg instanceof MessageBuilding) {
                    fireBrigadeMessages.add(msg);
                } else if (msg instanceof MessageCivilian) {
                    ambulanceMessages.add(msg);
                } else if (msg instanceof MessageRoad) {
                    fireBrigadeMessages.add(msg);
                    ambulanceMessages.add(msg);
                    policeMessages.add(msg);
                } else if (msg instanceof CommandAmbulance) {
                    ambulanceMessages.add(msg);
                } else if (msg instanceof CommandFire) {
                    fireBrigadeMessages.add(msg);
                } else if (msg instanceof CommandPolice) {
                    policeMessages.add(msg);
                } else if (msg instanceof CommandScout) {
                    if (agentType == StandardEntityURN.FIRE_STATION) {
                        fireBrigadeMessages.add(msg);
                    } else if (agentType == StandardEntityURN.POLICE_OFFICE) {
                        policeMessages.add(msg);
                    } else if (agentType == StandardEntityURN.AMBULANCE_CENTRE) {
                        ambulanceMessages.add(msg);
                    }
                } else if (msg instanceof MessageReport) {
                    if (agentType == StandardEntityURN.FIRE_BRIGADE) {
                        fireBrigadeMessages.add(msg);
                    } else if (agentType == StandardEntityURN.POLICE_FORCE) {
                        policeMessages.add(msg);
                    } else if (agentType == StandardEntityURN.AMBULANCE_TEAM) {
                        ambulanceMessages.add(msg);
                    }
                } else if (msg instanceof MessageFireBrigade) {
                    fireBrigadeMessages.add(msg);
                    ambulanceMessages.add(msg);
                    policeMessages.add(msg);
                } else if (msg instanceof MessagePoliceForce) {
                    ambulanceMessages.add(msg);
                    policeMessages.add(msg);
                } else if (msg instanceof MessageAmbulanceTeam) {
                    ambulanceMessages.add(msg);
                    policeMessages.add(msg);
                }
            }
        }

        if (scenarioInfo.getCommsChannelsCount() > 1) {
            //如果有多个通信通道，则发送无线电消息
            int[] channelSize = new int[scenarioInfo.getCommsChannelsCount() - 1];

            setSendMessages(scenarioInfo, StandardEntityURN.POLICE_FORCE, agentInfo,
                    worldInfo, policeMessages, channelSendMessageList, channelSize);
            setSendMessages(scenarioInfo, StandardEntityURN.AMBULANCE_TEAM, agentInfo,
                    worldInfo, ambulanceMessages, channelSendMessageList, channelSize);
            setSendMessages(scenarioInfo, StandardEntityURN.FIRE_BRIGADE, agentInfo,
                    worldInfo, fireBrigadeMessages, channelSendMessageList, channelSize);
        }

        ArrayList<StandardMessage> voiceMessageLowList = new ArrayList<>();
        ArrayList<StandardMessage> voiceMessageNormalList = new ArrayList<>();
        ArrayList<StandardMessage> voiceMessageHighList = new ArrayList<>();

        for (CommunicationMessage msg : voiceMessages) {
            if (msg instanceof StandardMessage m) {
                switch (m.getSendingPriority()) {
                    case LOW -> voiceMessageLowList.add(m);
                    case NORMAL -> voiceMessageNormalList.add(m);
                    case HIGH -> voiceMessageHighList.add(m);
                }
            }
        }

        // set the voice channel messages
        channelSendMessageList.get(0).addAll(voiceMessageHighList);
        channelSendMessageList.get(0).addAll(voiceMessageNormalList);
        channelSendMessageList.get(0).addAll(voiceMessageLowList);
    }


    /**
     * 通过智能体类型获得频道
     *
     * @param agentType    智能体类型
     * @param agentInfo    智能体信息
     * @param worldInfo    世界信息
     * @param scenarioInfo 场景信息
     * @return int[] 频道列表
     * @author 软工20-2金磊
     * @since 2022/6/13
     */
    protected int[] getChannelsByAgentType(StandardEntityURN agentType, AgentInfo agentInfo, WorldInfo worldInfo, @NotNull ScenarioInfo scenarioInfo) {
        //第0个频道是语音
        int numChannels = scenarioInfo.getCommsChannelsCount() - 1;
        //最大频道数
        int maxChannelCount;
        //智能体是否是排
        boolean isPlatoon = isPlatoonAgent(agentInfo, worldInfo);
        if (isPlatoon) {
            maxChannelCount = scenarioInfo.getCommsChannelsMaxPlatoon();
        } else {
            maxChannelCount = scenarioInfo.getCommsChannelsMaxOffice();
        }
        int[] channels = new int[maxChannelCount];

        for (int i = 0; i < maxChannelCount; i++) {
            channels[i] = RangelChannelSubscriber.getChannelNumber(agentType, i, numChannels);
        }
        return channels;
    }


    /**
     * 判断是否是排智能体
     *
     * @param agentInfo 智能体信息
     * @param worldInfo 世界信息
     * @return boolean (true是||false不是)
     * @author 软工20-2金磊
     * @since 2022/6/13
     */
    protected boolean isPlatoonAgent(AgentInfo agentInfo, WorldInfo worldInfo) {
        StandardEntityURN agentType = getAgentType(agentInfo, worldInfo);
        //如果是消防队,警察或者救护队返回true
        return agentType == StandardEntityURN.FIRE_BRIGADE
                || agentType == StandardEntityURN.POLICE_FORCE
                || agentType == StandardEntityURN.AMBULANCE_TEAM;
    }


    /**
     * 获得智能体的类型
     *
     * @param agentInfo 智能体信息
     * @param worldInfo 世界信息
     * @return rescuecore2.standard.entities.StandardEntityURN 标准实体URN
     * @author 软工20-2金磊
     * @since 2022/6/13
     */
    protected StandardEntityURN getAgentType(@NotNull AgentInfo agentInfo, @NotNull WorldInfo worldInfo) {
        return Objects.requireNonNull(worldInfo.getEntity(agentInfo.getID())).getStandardURN();
    }


    /**
     * 设置发送的消息
     *
     * @param scenarioInfo           场景信息
     * @param agentType              智能体类型
     * @param agentInfo              智能体信息
     * @param worldInfo              世界信息
     * @param messages               消息
     * @param channelSendMessageList 可以发送消息的频道列表
     * @param channelSize            频道大小
     * @author 软工20-2金磊
     * @since 2022/6/13
     */
    protected void setSendMessages(ScenarioInfo scenarioInfo,
                                   StandardEntityURN agentType, AgentInfo agentInfo, WorldInfo worldInfo,
                                   List<CommunicationMessage> messages,
                                   List<List<CommunicationMessage>> channelSendMessageList,
                                   int[] channelSize) {
        int channelIndex = 0;
        int[] channels = getChannelsByAgentType(agentType, agentInfo, worldInfo, scenarioInfo);
        int channel = channels[channelIndex];
        int channelCapacity = scenarioInfo.getCommsChannelBandwidth(channel);
        // start from HIGH, NORMAL, to LOW
        for (int i = StandardMessagePriority.values().length - 1; i >= 0; i--) {
            for (CommunicationMessage msg : messages) {
                StandardMessage stdMsg = (StandardMessage) msg;
                if (stdMsg.getSendingPriority() == StandardMessagePriority.values()[i]) {
                    channelSize[channel - 1] += stdMsg.getByteArraySize();
                    if (channelSize[channel - 1] > channelCapacity) {
                        channelSize[channel - 1] -= stdMsg.getByteArraySize();
                        channelIndex++;
                        if (channelIndex < channels.length) {
                            channel = channels[channelIndex];
                            channelCapacity = scenarioInfo.getCommsChannelBandwidth(channel);
                            channelSize[channel - 1] += stdMsg.getByteArraySize();
                        } else {
                            // 如果该消息类型没有新的频道,则直接break
                            break;
                        }
                    }
                    channelSendMessageList.get(channel).add(stdMsg);
                }
            }
        }
    }
}